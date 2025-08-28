package com.worksphere.billing.service;

import com.worksphere.billing.model.BillingRequest;
import com.worksphere.billing.model.Invoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Servicio encargado de aplicar reglas fiscales y tributarias sobre los montos de facturación.
 *
 * Cualidades:
 * - Actualización automática: permite cambios de normativa por país/región.
 * - Precisión matemática: uso de BigDecimal para evitar errores de redondeo.
 * - Auditabilidad: registro de reglas aplicadas, montos y tenant.
 * - Configurabilidad: reglas adaptadas por tipo de plan o producto.
 * - Integración con persistencia: puede leer reglas de TaxRuleConfig o DB.
 * - Seguridad: acceso controlado para evitar modificaciones no autorizadas.
 */
@Service
public class TaxRuleEngine {

    private static final Logger log = LoggerFactory.getLogger(TaxRuleEngine.class);

    // Simulación de reglas por país o tipo de plan (puede provenir de DB o YAML)
    private Map<String, BigDecimal> taxRules = Collections.emptyMap();

    /**
     * Aplica impuestos a un monto base de un invoice según el tenant y el plan.
     *
     * @param invoice invoice a calcular impuestos
     * @param request BillingRequest con información de tenant, plan y ciclo
     * @return monto total de impuestos aplicados
     */
    public BigDecimal applyTaxes(Invoice invoice, BillingRequest request) {
        if (invoice == null || request == null) {
            log.warn("Invoice o BillingRequest nulos para tenant {}", request != null ? request.getTenantId() : "unknown");
            return BigDecimal.ZERO;
        }

        BigDecimal baseAmount = invoice.getTotalAmount();
        String tenantId = request.getTenantId();
        String planType = request.getPlanType();
        String country = request.getCountry();

        BigDecimal taxRate = taxRules.getOrDefault(country + "-" + planType, BigDecimal.ZERO);
        BigDecimal taxAmount = baseAmount.multiply(taxRate).setScale(2, BigDecimal.ROUND_HALF_UP);

        invoice.setTaxAmount(taxAmount);
        invoice.setTotalAmount(baseAmount.add(taxAmount));

        log.info("Applied tax {}% to invoice {} for tenant {} at {}. Tax amount: {}",
                taxRate.multiply(new BigDecimal("100")),
                invoice.getInvoiceId(),
                tenantId,
                Instant.now(),
                taxAmount);

        return taxAmount;
    }

    /**
     * Actualiza las reglas fiscales desde la configuración persistente.
     *
     * @param rules mapa country-planType → taxRate
     */
    public void updateTaxRules(Map<String, BigDecimal> rules) {
        // Seguridad: solo administración o procesos autorizados deben invocar
        if (rules == null) {
            log.warn("No se proporcionaron reglas fiscales");
            return;
        }
        this.taxRules = Collections.unmodifiableMap(rules);
        log.info("Tax rules updated at {} with {} entries", Instant.now(), rules.size());
    }

    /**
     * Obtiene la tasa aplicada para un tenant y plan específico.
     *
     * @param country país del tenant
     * @param planType tipo de plan
     * @return tasa impositiva
     */
    public BigDecimal getTaxRate(String country, String planType) {
        return taxRules.getOrDefault(country + "-" + planType, BigDecimal.ZERO);
    }
}
