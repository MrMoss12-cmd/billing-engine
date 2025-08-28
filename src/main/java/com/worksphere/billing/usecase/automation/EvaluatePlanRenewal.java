package com.worksphere.billing.usecase.automation;

import com.worksphere.billing.domain.model.BillingCycle;
import com.worksphere.billing.domain.model.PaymentResult;
import com.worksphere.billing.domain.exception.PaymentException;
import com.worksphere.billing.transport.events.EventPublisher;
import com.worksphere.billing.transport.events.PlanRenewedEvent;
import com.worksphere.billing.transport.events.RenewalFailedEvent;
import com.worksphere.billing.domain.repository.BillingCycleRepository;
import com.worksphere.billing.domain.repository.PaymentResultRepository;
import com.worksphere.billing.domain.repository.SubscriptionRepository;
import com.worksphere.billing.service.TenantPolicyService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Caso de uso para evaluar la renovación de planes al finalizar un ciclo de facturación.
 *
 * Reglas principales:
 * - Solo renovar si el plan está activo y con contrato vigente según política del tenant.
 * - Verificar existencia de un pago válido (SUCCESS) para el ciclo evaluado, salvo que la política permita renovación diferida.
 * - Soporta modos: AUTOMATIC, MANUAL, MIXED (según política).
 * - Evitar renovaciones duplicadas (idempotencia).
 * - Emitir eventos 'plan_renewed' o 'renewal_failed' con trazabilidad.
 */
@Component
public class EvaluatePlanRenewal {

    private static final Logger logger = LoggerFactory.getLogger(EvaluatePlanRenewal.class);

    public enum RenewalMode { AUTOMATIC, MANUAL, MIXED }
    public enum Decision { RENEWED, PENDING, FAILED }

    private final BillingCycleRepository billingCycleRepository;
    private final PaymentResultRepository paymentResultRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantPolicyService tenantPolicyService;
    private final EventPublisher eventPublisher;

    public EvaluatePlanRenewal(BillingCycleRepository billingCycleRepository,
                               PaymentResultRepository paymentResultRepository,
                               SubscriptionRepository subscriptionRepository,
                               TenantPolicyService tenantPolicyService,
                               EventPublisher eventPublisher) {
        this.billingCycleRepository = billingCycleRepository;
        this.paymentResultRepository = paymentResultRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.tenantPolicyService = tenantPolicyService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Evalúa renovación para un tenant y ciclo.
     *
     * @param tenantId       tenant objetivo
     * @param billingCycleId ciclo que acaba de finalizar o está por finalizar
     * @param mode           modo de renovación (AUTOMATIC, MANUAL, MIXED)
     * @return decisión tomada
     */
    @Transactional
    public Decision execute(String tenantId, String billingCycleId, RenewalMode mode) {

        // 1) Cargar estado actual: ciclo y suscripción
        BillingCycle cycle = billingCycleRepository
                .findByTenantIdAndBillingCycleIdForUpdate(tenantId, billingCycleId);

        if (cycle == null) {
            logger.error("EvaluatePlanRenewal: ciclo no encontrado tenant={} cycle={}", tenantId, billingCycleId);
            emitFailed(tenantId, billingCycleId, "BILLING_CYCLE_NOT_FOUND");
            return Decision.FAILED;
        }

        var subscription = subscriptionRepository.findActiveByTenantIdForUpdate(tenantId)
                .orElse(null);

        if (subscription == null) {
            logger.warn("EvaluatePlanRenewal: suscripción inactiva o no encontrada tenant={} cycle={}", tenantId, billingCycleId);
            emitFailed(tenantId, billingCycleId, "SUBSCRIPTION_INACTIVE");
            return Decision.FAILED;
        }

        // 2) Idempotencia: ¿ya se renovó este ciclo?
        if (billingCycleId.equals(subscription.getLastRenewedCycleId())) {
            logger.info("EvaluatePlanRenewal: renovación ya aplicada tenant={} cycle={}", tenantId, billingCycleId);
            return Decision.RENEWED; // idempotente
        }

        // 3) Reglas de negocio del tenant (contrato/antigüedad/uso)
        var policy = tenantPolicyService.getPolicyForTenant(tenantId);
        boolean contractValid = policy.isContractValid(subscription.getContractEndDate(), LocalDate.now());
        boolean planEligible = policy.isPlanEligibleForRenewal(subscription.getPlanCode(), subscription.getTier());
        boolean usageOk = policy.isUsageWithinLimits(tenantId, cycle.getStartDate(), cycle.getEndDate());

        if (!contractValid || !planEligible) {
            logger.warn("EvaluatePlanRenewal: condiciones de contrato/plan no válidas tenant={} cycle={} contractValid={} planEligible={}",
                    tenantId, billingCycleId, contractValid, planEligible);
            markPendingOrFail(tenantId, billingCycleId, subscription, policy, "CONTRACT_OR_PLAN_NOT_ELIGIBLE");
            return Decision.PENDING;
        }

        // 4) Interacción con pagos: ¿existe un pago válido para el ciclo?
        boolean requiresSuccessfulPayment = policy.requiresSuccessfulPaymentBeforeRenewal(mode);
        boolean hasSuccessfulPayment = false;

        if (cycle.getInvoiceId() != null) {
            PaymentResult pr = paymentResultRepository.findByInvoiceId(cycle.getInvoiceId());
            hasSuccessfulPayment = pr != null && "SUCCESS".equalsIgnoreCase(pr.getStatus());
        }

        if (requiresSuccessfulPayment && !hasSuccessfulPayment) {
            logger.info("EvaluatePlanRenewal: sin pago válido para renovar tenant={} cycle={}", tenantId, billingCycleId);
            markPendingOrFail(tenantId, billingCycleId, subscription, policy, "MISSING_SUCCESSFUL_PAYMENT");
            return Decision.PENDING;
        }

        // 5) Escenarios múltiples por modo
        switch (mode) {
            case AUTOMATIC:
                return doRenew(tenantId, billingCycleId, subscription, cycle, "AUTO");
            case MANUAL:
                if (!policy.allowManualRenewal(subscription.getPlanCode())) {
                    logger.warn("EvaluatePlanRenewal: renovación manual no permitida tenant={} cycle={}", tenantId, billingCycleId);
                    emitFailed(tenantId, billingCycleId, "MANUAL_RENEWAL_NOT_ALLOWED");
                    return Decision.FAILED;
                }
                // En manual, si llegó aquí es porque una acción externa lo confirmó.
                return doRenew(tenantId, billingCycleId, subscription, cycle, "MANUAL");
            case MIXED:
                // En mixto, si hay pago válido o aprobación previa, renovar; si no, dejar pendiente.
                if (hasSuccessfulPayment || policy.hasPreApproval(tenantId)) {
                    return doRenew(tenantId, billingCycleId, subscription, cycle, "MIXED");
                } else {
                    markPendingOrFail(tenantId, billingCycleId, subscription, policy, "MIXED_WAITING_APPROVAL_OR_PAYMENT");
                    return Decision.PENDING;
                }
            default:
                emitFailed(tenantId, billingCycleId, "UNKNOWN_MODE");
                return Decision.FAILED;
        }
    }

    // --- helpers ---

    @Transactional
    protected Decision doRenew(String tenantId,
                               String billingCycleId,
                               Object subscription,
                               BillingCycle cycle,
                               String modeTag) {

        // seguridad anti-duplicados (segunda verificación en la misma transacción)
        var sub = subscriptionRepository.findActiveByTenantIdForUpdate(tenantId).orElseThrow();
        if (billingCycleId.equals(sub.getLastRenewedCycleId())) {
            logger.info("EvaluatePlanRenewal#doRenew: ya renovado tenant={} cycle={}", tenantId, billingCycleId);
            return Decision.RENEWED;
        }

        // actualizar suscripción: extender vigencia y marcar último ciclo renovado
        var nextPeriod = sub.calculateNextPeriodFrom(cycle.getEndDate()); // método del dominio
        sub.setCurrentPeriodStart(nextPeriod.getStart());
        sub.setCurrentPeriodEnd(nextPeriod.getEnd());
        sub.setLastRenewedCycleId(billingCycleId);
        subscriptionRepository.save(sub);

        // (opcional) preparar siguiente ciclo – lo realiza ScheduleBillingCycle, aquí solo decisión
        eventPublisher.publish(new PlanRenewedEvent(
                tenantId,
                billingCycleId,
                sub.getPlanCode(),
                nextPeriod.getStart(),
                nextPeriod.getEnd(),
                modeTag
        ));

        logger.info("Plan renovado tenant={} cycle={} plan={} period={}..{} mode={}",
                tenantId, billingCycleId, sub.getPlanCode(), nextPeriod.getStart(), nextPeriod.getEnd(), modeTag);

        return Decision.RENEWED;
    }

    @Transactional
    protected void markPendingOrFail(String tenantId,
                                     String billingCycleId,
                                     Object subscription,
                                     Object policy,
                                     String reason) {
        // Por defecto, dejamos PENDING para que un scheduler vuelva a evaluar o se resuelva manualmente.
        // Si la política exige bloqueo (p. ej., plan inactivo), se emite FAILED.
        boolean mustFail = tenantPolicyService.mustFailOnReason(tenantId, reason);

        if (mustFail) {
            eventPublisher.publish(new RenewalFailedEvent(tenantId, billingCycleId, reason));
            logger.warn("Renovación FALLIDA tenant={} cycle={} reason={}", tenantId, billingCycleId, reason);
        } else {
            // Se podría persistir un registro de estado 'PENDING' en la suscripción/ciclo para auditoría
            billingCycleRepository.markRenewalPending(tenantId, billingCycleId, reason);
            logger.info("Renovación PENDIENTE tenant={} cycle={} reason={}", tenantId, billingCycleId, reason);
        }
    }

    private void emitFailed(String tenantId, String billingCycleId, String reason) {
        eventPublisher.publish(new RenewalFailedEvent(tenantId, billingCycleId, reason));
    }
}
