package com.worksphere.billing.usecase.automation;

import com.worksphere.billing.domain.model.BillingCycle;
import com.worksphere.billing.domain.model.PaymentResult;
import com.worksphere.billing.domain.model.Subscription;
import com.worksphere.billing.domain.repository.BillingCycleRepository;
import com.worksphere.billing.domain.repository.PaymentResultRepository;
import com.worksphere.billing.domain.repository.SubscriptionRepository;
import com.worksphere.billing.transport.events.EventPublisher;
import com.worksphere.billing.transport.events.CancellationWarningEvent;
import com.worksphere.billing.transport.events.ServiceSuspendedEvent;
import com.worksphere.billing.transport.events.SubscriptionCancelledEvent;
import com.worksphere.billing.service.TenantPolicyService;
import com.worksphere.billing.service.TenantOrchestratorClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Caso de uso: cancelar o suspender servicios por falta de pago.
 *
 * Flujo de alto nivel (configurable por política del tenant):
 *  - Vencimiento de factura -> periodo de gracia (ej. 15 días).
 *  - En el día {grace - warnDays} enviar advertencia (warning).
 *  - Si llega el fin de gracia y NO hay pago válido => suspender o cancelar.
 *  - Reversibilidad: si el cliente paga luego, un proceso aparte puede reactivar.
 *
 * Idempotencia: no emite acciones duplicadas; revisa estados actuales.
 */
@Component
public class TriggerCancellationDueToNonPayment {

    private static final Logger logger = LoggerFactory.getLogger(TriggerCancellationDueToNonPayment.class);

    private final BillingCycleRepository billingCycleRepository;
    private final PaymentResultRepository paymentResultRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantPolicyService tenantPolicyService;
    private final TenantOrchestratorClient tenantOrchestratorClient;
    private final EventPublisher eventPublisher;

    public TriggerCancellationDueToNonPayment(BillingCycleRepository billingCycleRepository,
                                              PaymentResultRepository paymentResultRepository,
                                              SubscriptionRepository subscriptionRepository,
                                              TenantPolicyService tenantPolicyService,
                                              TenantOrchestratorClient tenantOrchestratorClient,
                                              EventPublisher eventPublisher) {
        this.billingCycleRepository = billingCycleRepository;
        this.paymentResultRepository = paymentResultRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.tenantPolicyService = tenantPolicyService;
        this.tenantOrchestratorClient = tenantOrchestratorClient;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Ejecuta evaluación y (si corresponde) advertencia/suspensión/cancelación por impago.
     * Diseñado para correrse vía scheduler (ej. diariamente).
     *
     * @param tenantId       tenant a evaluar
     * @param billingCycleId ciclo cuyo invoice está vencido o por vencer
     */
    @Transactional
    public void execute(String tenantId, String billingCycleId) {
        // 1) Cargar ciclo y suscripción con bloqueo para evitar carreras
        BillingCycle cycle = billingCycleRepository.findByTenantIdAndBillingCycleIdForUpdate(tenantId, billingCycleId);
        if (cycle == null) {
            logger.warn("No se encontró billingCycle tenant={} cycle={}", tenantId, billingCycleId);
            return; // nada que hacer
        }

        Subscription sub = subscriptionRepository.findActiveOrSuspendedByTenantIdForUpdate(tenantId)
                .orElse(null);
        if (sub == null) {
            logger.info("Suscripción inexistente/inactiva para tenant={}, no se aplica cancelación", tenantId);
            return;
        }

        // 2) Reglas contractuales (periodo de gracia, avisos, tipo de acción)
        var policy = tenantPolicyService.getPolicyForTenant(tenantId);
        int graceDays = policy.getCancellationGraceDays();           // p.ej. 15
        int warnDaysBefore = policy.getCancellationWarningDays();    // p.ej. 3
        boolean cancelInsteadOfSuspend = policy.cancelInsteadOfSuspend(); // algunos tenants cancelan directo
        LocalDate dueDate = cycle.getDueDate() != null ? cycle.getDueDate().toLocalDate() : cycle.getEndDate();
        LocalDate today = LocalDate.now();

        // 3) Integración con facturación: ¿existe pago válido?
        boolean hasSuccessfulPayment = false;
        if (cycle.getInvoiceId() != null) {
            PaymentResult pr = paymentResultRepository.findByInvoiceId(cycle.getInvoiceId());
            hasSuccessfulPayment = pr != null && "SUCCESS".equalsIgnoreCase(pr.getStatus());
        }

        if (hasSuccessfulPayment) {
            // Reversibilidad implícita: si estaba suspendido y ahora pagó, podemos reactivar
            if (sub.isSuspended() && policy.autoReactivateOnLatePayment()) {
                reactivateServices(tenantId, sub, cycle);
            }
            logger.info("Tenant {} con pago válido para cycle {}, no se cancela/suspende", tenantId, billingCycleId);
            return;
        }

        // 4) Si no hay pago: calcular ventanas de advertencia y de gracia
        LocalDate cancelThreshold = dueDate.plusDays(graceDays);
        LocalDate warnThreshold = cancelThreshold.minusDays(warnDaysBefore);

        // 4.1) Notificación antes de cancelar/suspender (una vez)
        // Marcamos en la suscripción que ya se advirtió para este ciclo (idempotencia)
        if ((today.isEqual(warnThreshold) || today.isAfter(warnThreshold))
                && today.isBefore(cancelThreshold)
                && !billingCycleRepository.wasCancellationWarningEmitted(tenantId, billingCycleId)) {

            eventPublisher.publish(new CancellationWarningEvent(tenantId, billingCycleId, dueDate, cancelThreshold));
            billingCycleRepository.markCancellationWarningEmitted(tenantId, billingCycleId, LocalDateTime.now());
            logger.info("Advertencia de cancelación emitida tenant={} cycle={} cancelThreshold={}", tenantId, billingCycleId, cancelThreshold);
        }

        // 4.2) Llegó el umbral de gracia: suspender o cancelar (idempotente)
        if (today.isBefore(cancelThreshold)) {
            // Aún en ventana de gracia, solo notificar/esperar
            return;
        }

        // Ya superó la fecha de gracia: actuar según política.
        // Evitar acciones repetidas si ya fue suspendido/cancelado para este ciclo.
        if (billingCycleRepository.isCancellationFinalized(tenantId, billingCycleId)) {
            logger.info("Acción de cancelación ya realizada tenant={} cycle={}", tenantId, billingCycleId);
            return;
        }

        if (cancelInsteadOfSuspend) {
            // Cancelación definitiva
            cancelSubscription(tenantId, sub, cycle, "NON_PAYMENT_GRACE_EXPIRED");
        } else {
            // Suspensión (reversible)
            suspendServices(tenantId, sub, cycle, "NON_PAYMENT_GRACE_EXPIRED");
        }

        billingCycleRepository.markCancellationFinalized(tenantId, billingCycleId, LocalDateTime.now());
    }

    // --- Acciones auxiliares ---

    private void suspendServices(String tenantId, Subscription sub, BillingCycle cycle, String reason) {
        if (sub.isSuspended()) {
            logger.info("Suscripción ya suspendida tenant={} cycle={}", tenantId, cycle.getBillingCycleId());
            return;
        }

        sub.setSuspended(true);
        sub.setSuspendedAt(LocalDateTime.now());
        sub.setSuspendedReason(reason);
        subscriptionRepository.save(sub);

        // Notificar a orquestador para desactivar recursos en caliente (pero sin borrar)
        try {
            tenantOrchestratorClient.suspendTenant(tenantId);
        } catch (Exception e) {
            // Resiliencia: si la llamada externa falla, el scheduler volverá a intentar;
            // mantenemos estado suspendido en dominio para consistencia.
            logger.error("Fallo suspendiendo servicios para tenant {}: {}", tenantId, e.getMessage(), e);
        }

        // Evento derivado
        eventPublisher.publish(new ServiceSuspendedEvent(
                tenantId,
                cycle.getBillingCycleId(),
                reason,
                LocalDateTime.now()
        ));

        logger.warn("Servicios SUSPENDIDOS por impago tenant={} cycle={}", tenantId, cycle.getBillingCycleId());
    }

    private void cancelSubscription(String tenantId, Subscription sub, BillingCycle cycle, String reason) {
        if (sub.isCancelled()) {
            logger.info("Suscripción ya cancelada tenant={} cycle={}", tenantId, cycle.getBillingCycleId());
            return;
        }

        sub.setCancelled(true);
        sub.setCancelledAt(LocalDateTime.now());
        sub.setCancellationReason(reason);
        subscriptionRepository.save(sub);

        // Notificar a orquestador para desprovisionar/retirar recursos
        try {
            tenantOrchestratorClient.cancelTenant(tenantId);
        } catch (Exception e) {
            logger.error("Fallo cancelando recursos para tenant {}: {}", tenantId, e.getMessage(), e);
        }

        // Evento derivado
        eventPublisher.publish(new SubscriptionCancelledEvent(
                tenantId,
                cycle.getBillingCycleId(),
                reason,
                LocalDateTime.now()
        ));

        logger.error("Suscripción CANCELADA por impago tenant={} cycle={}", tenantId, cycle.getBillingCycleId());
    }

    private void reactivateServices(String tenantId, Subscription sub, BillingCycle cycle) {
        sub.setSuspended(false);
        sub.setReactivatedAt(LocalDateTime.now());
        subscriptionRepository.save(sub);

        try {
            tenantOrchestratorClient.reactivateTenant(tenantId);
        } catch (Exception e) {
            logger.error("Fallo reactivando servicios para tenant {}: {}", tenantId, e.getMessage(), e);
        }

        // No se pide explícitamente un evento de reactivación, pero podría existir (p. ej., ServiceReactivatedEvent)
        logger.info("Servicios REACTIVADOS por pago tardío tenant={} cycle={}", tenantId, cycle.getBillingCycleId());
    }
}
