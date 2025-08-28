package com.worksphere.billing.utils;

import com.worksphere.billing.service.TaxRuleEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.logging.Logger;

/**
 * Utilidad para cálculos fiscales y tributarios.
 */
@Component
public class TaxUtils {

    private static final Logger logger = Logger.getLogger(TaxUtils.class.getName());

    private final TaxRuleEngine taxRuleEngine;

    @Autowired
    public TaxUtils(TaxRuleEngine taxRuleEngine) {
        this.taxRuleEngine = taxRuleEngine;
    }

    /**
     * Aplica el impuesto según la regla fiscal configurada para un tenant y tipo de impuesto.
     *
     * @param tenantId   ID del tenant
     * @param amount     Monto base
     * @param taxType    Tipo de impuesto (IVA, retención, percepción, etc.)
     * @return Monto del impuesto calculado
     */
    public BigDecimal calculateTax(String tenantId, BigDecimal amount, String taxType) {
        BigDecimal rate = taxRuleEngine.getTaxRate(tenantId, taxType);

        if (rate == null) {
            logger.warning("No se encontró tasa de impuesto para tenant " + tenantId + " y tipo " + taxType);
            return BigDecimal.ZERO;
        }

        BigDecimal tax = amount.multiply(rate)
                .setScale(2, RoundingMode.HALF_UP);

        logger.info("Cálculo de impuesto: tenant=" + tenantId +
                ", amount=" + amount + ", taxType=" + taxType +
                ", tax=" + tax);

        return tax;
    }

    /**
     * Redondea un monto según las reglas fiscales.
     *
     * @param amount  Monto a redondear
     * @param scale   Número de decimales
     * @return Monto redondeado
     */
    public BigDecimal roundAmount(BigDecimal amount, int scale) {
        BigDecimal rounded = amount.setScale(scale, RoundingMode.HALF_UP);
        logger.info("Monto redondeado: original=" + amount + ", redondeado=" + rounded);
        return rounded;
    }

    /**
     * Convierte un porcentaje a factor decimal.
     * Por ejemplo, 19% -> 0.19
     *
     * @param percent Porcentaje
     * @return Factor decimal
     */
    public BigDecimal percentToDecimal(BigDecimal percent) {
        BigDecimal factor = percent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        logger.info("Conversión de porcentaje a decimal: " + percent + "% -> " + factor);
        return factor;
    }

    /**
     * Valida que un impuesto aplicado cumpla con la regla fiscal.
     *
     * @param tenantId  Tenant
     * @param taxType   Tipo de impuesto
     * @param taxAmount Monto del impuesto calculado
     * @param baseAmount Monto base
     * @return true si es válido según la tasa oficial
     */
    public boolean validateTax(String tenantId, String taxType, BigDecimal taxAmount, BigDecimal baseAmount) {
        BigDecimal expectedTax = calculateTax(tenantId, baseAmount, taxType);
        boolean valid = expectedTax.compareTo(taxAmount) == 0;

        logger.info("Validación de impuesto: tenant=" + tenantId + ", taxType=" + taxType +
                ", expected=" + expectedTax + ", actual=" + taxAmount + ", valid=" + valid);

        return valid;
    }
}
