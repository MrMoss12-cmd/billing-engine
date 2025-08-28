package com.worksphere.billing.usecase.audit;

import com.worksphere.billing.domain.model.BillingOperationLog;
import com.worksphere.billing.infrastructure.repository.BillingOperationLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Caso de uso: Registrar operaciones de facturación en un log persistente para fines legales, regulatorios y de trazabilidad.
 */
@Service
public class LogBillingOperation {

    private static final Logger log = LoggerFactory.getLogger(LogBillingOperation.class);

    private final BillingOperationLogRepository billingOperationLogRepository;

    public LogBillingOperation(BillingOperationLogRepository billingOperationLogRepository) {
        this.billingOperationLogRepository = billingOperationLogRepository;
    }

    /**
     * Registra una operación de facturación en la base de datos con integridad y consistencia.
     *
     * @param tenantId         Identificador del tenant
     * @param billingCycleId   Ciclo de facturación
     * @param invoiceId        Factura relacionada (opcional, null en cálculos previos)
     * @param operationType    Tipo de operación (CALCULATION, PAYMENT, REVERSAL, etc.)
     * @param actor            Actor que disparó la operación (usuario o sistema)
     * @param details          Detalles adicionales relevantes para la auditoría
     */
    @Transactional
    public void logOperation(String tenantId,
                             String billingCycleId,
                             String invoiceId,
                             String operationType,
                             String actor,
                             String details) {
        try {
            BillingOperationLog entry = new BillingOperationLog();
            entry.setId(UUID.randomUUID().toString());
            entry.setTenantId(tenantId);
            entry.setBillingCycleId(billingCycleId);
            entry.setInvoiceId(invoiceId);
            entry.setOperationType(operationType);
            entry.setActor(actor);
            entry.setDetails(details);
            entry.setTimestamp(Instant.now());

            billingOperationLogRepository.save(entry);

            log.info("Operación de facturación registrada. tenantId=[{}], billingCycleId=[{}], invoiceId=[{}], op=[{}], actor=[{}]",
                    tenantId, billingCycleId, invoiceId, operationType, actor);

        } catch (Exception ex) {
            log.error("Error registrando operación de facturación tenantId=[{}], billingCycleId=[{}], invoiceId=[{}], op=[{}]",
                    tenantId, billingCycleId, invoiceId, operationType, ex);
            // En escenarios críticos se podría escalar este fallo a un sistema de monitoreo (ej. Sentry, Prometheus, etc.)
            throw ex;
        }
    }
}
