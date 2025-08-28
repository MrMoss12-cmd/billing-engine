package com.worksphere.billing.usecase.idempotency;

import com.worksphere.billing.domain.model.BillingCycle;
import com.worksphere.billing.domain.exception.PaymentException;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.worksphere.billing.domain.repository.BillingCycleRepository;

/**
 * Caso de uso que verifica si un ciclo de facturación ya fue ejecutado.
 */
@Component
public class CheckBillingCycleExecuted {

    private static final Logger logger = LoggerFactory.getLogger(CheckBillingCycleExecuted.class);

    private final BillingCycleRepository billingCycleRepository;

    public CheckBillingCycleExecuted(BillingCycleRepository billingCycleRepository) {
        this.billingCycleRepository = billingCycleRepository;
    }

    /**
     * Verifica si un ciclo de facturación ya ha sido procesado.
     *
     * @param tenantId       Identificador del tenant
     * @param billingCycleId Identificador del ciclo de facturación
     * @return true si ya se ejecutó, false si no
     */
    public boolean execute(String tenantId, String billingCycleId) {
        // -----------------------------
        // 1. Consulta persistente: verificar existencia del ciclo
        // -----------------------------
        BillingCycle existingCycle = billingCycleRepository
                .findByTenantIdAndBillingCycleId(tenantId, billingCycleId);

        boolean alreadyExecuted = existingCycle != null && "COMPLETED".equals(existingCycle.getStatus());

        // -----------------------------
        // 2. Auditabilidad: log de verificación
        // -----------------------------
        if (alreadyExecuted) {
            logger.info("Ciclo {} ya ejecutado para tenant {}", billingCycleId, tenantId);
        } else {
            logger.info("Ciclo {} no ejecutado previamente para tenant {}", billingCycleId, tenantId);
        }

        // -----------------------------
        // 3. Determinismo: misma entrada -> mismo resultado
        // -----------------------------
        return alreadyExecuted;
    }
}
