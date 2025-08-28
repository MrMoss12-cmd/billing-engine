package com.worksphere.billing.usecase.idempotency;

import com.worksphere.billing.domain.model.BillingCycle;
import com.worksphere.billing.domain.repository.BillingCycleRepository;
import com.worksphere.billing.transport.events.PaymentFailedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Caso de uso para reintentar ciclos de facturación que fallaron previamente.
 */
@Component
public class RetryFailedBillingJob {

    private static final Logger logger = LoggerFactory.getLogger(RetryFailedBillingJob.class);

    private final BillingCycleRepository billingCycleRepository;
    private final EventPublisher eventPublisher; // Servicio para emitir eventos (Kafka, etc.)

    private final int maxRetries = 3;          // Número máximo de reintentos
    private final long backoffSeconds = 60L;   // Backoff inicial en segundos

    public RetryFailedBillingJob(BillingCycleRepository billingCycleRepository,
                                 EventPublisher eventPublisher) {
        this.billingCycleRepository = billingCycleRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Reintenta todos los ciclos fallidos que aún no superaron el máximo de reintentos.
     */
    @Transactional
    public void executeRetries() {
        List<BillingCycle> failedCycles = billingCycleRepository.findFailedCyclesPendingRetry();

        for (BillingCycle cycle : failedCycles) {
            retryCycle(cycle);
        }
    }

    /**
     * Reintento seguro de un ciclo de facturación individual.
     *
     * @param cycle Ciclo fallido
     */
    @Transactional
    public void retryCycle(BillingCycle cycle) {
        String tenantId = cycle.getTenantId();
        String cycleId = cycle.getBillingCycleId();

        // -----------------------------
        // 1. Control de número de reintentos
        // -----------------------------
        if (cycle.getRetryCount() >= maxRetries) {
            logger.warn("Ciclo {} de tenant {} agotó todos los reintentos", cycleId, tenantId);
            cycle.setStatus("FAILED_EXHAUSTED");
            cycle.setCompletedAt(LocalDateTime.now());
            billingCycleRepository.save(cycle);
            eventPublisher.publish(new PaymentFailedEvent(tenantId, cycleId, "Retries exhausted"));
            return;
        }

        // -----------------------------
        // 2. Registro de reintento
        // -----------------------------
        logger.info("Iniciando reintento {} para ciclo {} de tenant {}", cycle.getRetryCount() + 1, cycleId, tenantId);
        eventPublisher.publish(new PaymentFailedEvent(tenantId, cycleId, "Retry started"));

        try {
            // -----------------------------
            // 3. Lógica de reintento
            //    (Aquí se puede llamar al flujo de cobro o generar factura de nuevo)
            // -----------------------------
            boolean success = attemptBillingCycle(cycle);

            if (success) {
                cycle.setStatus("COMPLETED");
                cycle.setCompletedAt(LocalDateTime.now());
                logger.info("Ciclo {} completado exitosamente en reintento {}", cycleId, cycle.getRetryCount() + 1);
            } else {
                cycle.setRetryCount(cycle.getRetryCount() + 1);
                cycle.setStatus("FAILED");
                logger.warn("Ciclo {} falló en reintento {}. Reintentando luego.", cycleId, cycle.getRetryCount());
            }

            billingCycleRepository.save(cycle);

        } catch (Exception e) {
            cycle.setRetryCount(cycle.getRetryCount() + 1);
            cycle.setStatus("FAILED");
            billingCycleRepository.save(cycle);
            logger.error("Error en reintento de ciclo {} de tenant {}: {}", cycleId, tenantId, e.getMessage(), e);
        }
    }

    /**
     * Simula el intento de ejecutar el ciclo de facturación.
     *
     * @param cycle Ciclo a intentar
     * @return true si se completó correctamente, false si falló
     */
    private boolean attemptBillingCycle(BillingCycle cycle) {
        // Aquí se integraría el flujo real de cálculo y pago.
        // Para ejemplo, se simula un éxito aleatorio o transitorio.
        return Math.random() > 0.3; // 70% de éxito simulado
    }
}
