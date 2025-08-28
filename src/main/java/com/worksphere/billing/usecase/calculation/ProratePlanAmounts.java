package com.worksphere.billing.usecase.calculation;

import com.worksphere.billing.domain.model.BillingRequest;
import com.worksphere.billing.domain.model.BillingCycle;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.time.LocalDate;

/**
 * Caso de uso encargado de calcular el prorrateo de montos de un tenant.
 * Se activa cuando hay cambio de plan, inicio tardío, cancelación anticipada o ajustes parciales.
 */
@Component
public class ProratePlanAmounts {

    private static final Logger logger = LoggerFactory.getLogger(ProratePlanAmounts.class);

    /**
     * Calcula el monto prorrateado basado en consumo parcial del ciclo o cambio de plan.
     *
     * @param billingRequest Información del request de facturación
     * @param billingCycle   Ciclo de facturación asociado
     * @return Monto prorrateado exacto
     */
    public BigDecimal execute(BillingRequest billingRequest, BillingCycle billingCycle) {

        LocalDate cycleStart = billingCycle.getStartDate();
        LocalDate cycleEnd = billingCycle.getEndDate();
        LocalDate usageStart = billingRequest.getUsageStartDate() != null
                               ? billingRequest.getUsageStartDate()
                               : cycleStart;
        LocalDate usageEnd = billingRequest.getUsageEndDate() != null
                             ? billingRequest.getUsageEndDate()
                             : cycleEnd;

        BigDecimal baseAmount = billingRequest.getPlanAmount();

        // -----------------------------
        // 1. Calcular días del ciclo y días efectivos de uso
        // -----------------------------
        long totalCycleDays = ChronoUnit.DAYS.between(cycleStart, cycleEnd) + 1;
        long usedDays = ChronoUnit.DAYS.between(usageStart, usageEnd) + 1;

        // -----------------------------
        // 2. Prorrateo exacto
        // -----------------------------
        BigDecimal proratedAmount = baseAmount
                .multiply(BigDecimal.valueOf(usedDays))
                .divide(BigDecimal.valueOf(totalCycleDays), 2, RoundingMode.HALF_UP);

        // -----------------------------
        // 3. Auditabilidad: log del cálculo
        // -----------------------------
        logger.info("Prorrateo tenant {}: {} días de {} total a {} = {}",
                    billingRequest.getTenantId(),
                    usedDays,
                    totalCycleDays,
                    baseAmount,
                    proratedAmount);

        return proratedAmount;
    }
}
