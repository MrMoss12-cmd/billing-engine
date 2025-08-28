package com.worksphere.billing.usecase.calculation;

import com.worksphere.billing.domain.model.BillingRequest;
import com.worksphere.billing.service.TaxRuleEngine;
import com.worksphere.billing.domain.exception.TaxCalculationException;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Caso de uso encargado de aplicar impuestos y contribuciones fiscales
 * sobre un monto base ya calculado para un tenant.
 */
@Component
public class ApplyTaxRules {

    private static final Logger logger = LoggerFactory.getLogger(ApplyTaxRules.class);

    private final TaxRuleEngine taxRuleEngine;

    public ApplyTaxRules(TaxRuleEngine taxRuleEngine) {
        this.taxRuleEngine = taxRuleEngine;
    }

    /**
     * Aplica las reglas tributarias correspondientes al monto base.
     *
     * @param billingRequest Información del request de facturación
     * @param baseAmount     Monto base calculado previamente
     * @return Monto total de impuestos
     */
    public BigDecimal execute(BillingRequest billingRequest, BigDecimal baseAmount) {

        try {
            String tenantId = billingRequest.getTenantId();
            String countryCode = billingRequest.getCountryCode(); // país/región del tenant

            // -----------------------------
            // 1. Recuperar reglas fiscales para país/región
            // -----------------------------
            var taxRules = taxRuleEngine.getTaxRulesForCountry(countryCode);

            // -----------------------------
            // 2. Aplicar cada impuesto al monto base
            // -----------------------------
            BigDecimal totalTax = BigDecimal.ZERO;
            for (var rule : taxRules) {
                BigDecimal taxAmount = rule.apply(baseAmount);
                totalTax = totalTax.add(taxAmount);

                // -----------------------------
                // 3. Auditabilidad: log de cada impuesto aplicado
                // -----------------------------
                logger.info("Tenant {} - Impuesto {} aplicado: {} sobre base {}",
                        tenantId, rule.getName(), taxAmount, baseAmount);
            }

            // -----------------------------
            // 4. Consistencia: evitar impuestos duplicados
            // -----------------------------
            // La lógica de TaxRuleEngine asegura no duplicar reglas

            logger.info("Tenant {} - Total impuestos aplicados: {}", tenantId, totalTax);

            return totalTax;

        } catch (Exception ex) {
            logger.error("Error aplicando reglas fiscales para tenant {}: {}", 
                         billingRequest.getTenantId(), ex.getMessage(), ex);
            throw new TaxCalculationException(
                    "Error al calcular impuestos", 
                    billingRequest.getTenantId(), 
                    billingRequest.getCountryCode());
        }
    }
}
