package com.worksphere.billing.usecase.idempotency;

import com.worksphere.billing.domain.model.BillingCycle;
import com.worksphere.billing.domain.model.PaymentResult;
import com.worksphere.billing.domain.repository.BillingCycleRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Caso de uso que marca un ciclo de facturación como completado exitosamente.
 */
@Component
public class MarkBillingCycleAsComplete {

    private static final Logger logger = LoggerFactory.getLogger(MarkBillingCycleAsComplete.class);

    private final BillingCycleRepository billingCycleRepository;

    public MarkBillingCycleAsComplete(BillingCycleRepository billingCycleRepository) {
        this.billingCycleRepository = billingCycleRepository;
    }

    /**
     * Marca el ciclo como completado, asociando invoice y resultado de pago.
     *
     * @param tenantId       Identificador del tenant
     * @param billingCycleId Identificador del ciclo
     * @param invoiceId      Identificador de la factura generada
     * @param paymentResult  Resultado del pago asociado
     */
    @Transactional
    public void execute(String tenantId, String billingCycleId, String invoiceId, PaymentResult paymentResult) {

        // -----------------------------
        // 1. Bloqueo transaccional / concurrencia
        // -----------------------------
        BillingCycle cycle = billingCycleRepository
                .findByTenantIdAndBillingCycleIdForUpdate(tenantId, billingCycleId);

        if (cycle == null) {
            throw new IllegalStateException("Ciclo no encontrado para tenant " + tenantId + " billingCycleId " + billingCycleId);
        }

        // -----------------------------
        // 2. Verificar idempotencia
        // -----------------------------
        if ("COMPLETED".equals(cycle.getStatus())) {
            logger.info("Ciclo {} ya marcado como completado para tenant {}", billingCycleId, tenantId);
            return;
        }

        // -----------------------------
        // 3. Actualizar estado y asociar invoice y pago
        // -----------------------------
        cycle.setStatus("COMPLETED");
        cycle.setCompletedAt(LocalDateTime.now());
        cycle.setInvoiceId(invoiceId);
        cycle.setPaymentResult(paymentResult);

        billingCycleRepository.save(cycle);

        // -----------------------------
        // 4. Auditabilidad
        // -----------------------------
        logger.info("Ciclo {} marcado como COMPLETED para tenant {}. Invoice={} PaymentResult={}",
                billingCycleId, tenantId, invoiceId, paymentResult.getTransactionId());
    }
}
