package com.worksphere.billing.usecase.calculation;

import com.worksphere.billing.domain.model.BillingRequest;
import com.worksphere.billing.domain.model.BillingCycle;
import com.worksphere.billing.domain.model.Invoice;
import com.worksphere.billing.domain.exception.InvoiceGenerationException;
import com.worksphere.billing.service.TaxRuleEngine;
import com.worksphere.billing.service.InvoiceSigner;
import com.worksphere.billing.utils.InvoiceUtils;
import com.worksphere.billing.usecase.calculation.ProratePlanAmounts;
import com.worksphere.billing.usecase.calculation.ApplyTaxRules;
import com.worksphere.billing.usecase.calculation.GenerateInvoice;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Caso de uso orquestador para calcular la facturación de un tenant específico.
 * Coordina prorrateos, aplicación de impuestos y generación de factura.
 */
@Component
public class CalculateBillingForTenant {

    private static final Logger logger = LoggerFactory.getLogger(CalculateBillingForTenant.class);

    private final ProratePlanAmounts proratePlanAmounts;
    private final ApplyTaxRules applyTaxRules;
    private final GenerateInvoice generateInvoice;
    private final TaxRuleEngine taxRuleEngine;
    private final InvoiceSigner invoiceSigner;

    public CalculateBillingForTenant(ProratePlanAmounts proratePlanAmounts,
                                     ApplyTaxRules applyTaxRules,
                                     GenerateInvoice generateInvoice,
                                     TaxRuleEngine taxRuleEngine,
                                     InvoiceSigner invoiceSigner) {
        this.proratePlanAmounts = proratePlanAmounts;
        this.applyTaxRules = applyTaxRules;
        this.generateInvoice = generateInvoice;
        this.taxRuleEngine = taxRuleEngine;
        this.invoiceSigner = invoiceSigner;
    }

    /**
     * Ejecuta todo el proceso de facturación para un tenant específico.
     *
     * @param billingRequest Información del request de facturación
     * @param billingCycle   Ciclo de facturación asociado
     * @return Factura generada
     */
    public Invoice execute(BillingRequest billingRequest, BillingCycle billingCycle) {
        logger.info("Iniciando cálculo de facturación para tenant {} y ciclo {}",
                    billingRequest.getTenantId(), billingCycle.getBillingCycleId());

        try {
            // -----------------------------
            // 1. Prorrateo de plan según consumo y fechas
            // -----------------------------
            BigDecimal proratedAmount = proratePlanAmounts.execute(billingRequest, billingCycle);
            logger.debug("Monto prorrateado calculado: {}", proratedAmount);

            // -----------------------------
            // 2. Aplicar reglas fiscales y calcular impuestos
            // -----------------------------
            BigDecimal taxAmount = applyTaxRules.execute(billingRequest, proratedAmount);
            logger.debug("Impuestos calculados: {}", taxAmount);

            // -----------------------------
            // 3. Generar factura final
            // -----------------------------
            Invoice invoice = generateInvoice.execute(billingRequest, billingCycle, proratedAmount, taxAmount);
            logger.info("Factura generada con ID: {}", invoice.getInvoiceId());

            // -----------------------------
            // 4. Firmar factura electrónicamente si aplica
            // -----------------------------
            invoiceSigner.sign(invoice);
            logger.info("Factura firmada digitalmente: {}", invoice.getInvoiceId());

            // -----------------------------
            // 5. Auditabilidad: registrar pasos y resultados
            // -----------------------------
            logger.debug("Facturación completada para tenant {}: {}",
                         billingRequest.getTenantId(), InvoiceUtils.toString(invoice));

            return invoice;

        } catch (InvoiceGenerationException ex) {
            logger.error("Error generando factura para tenant {}: {}",
                         billingRequest.getTenantId(), ex.getMessage(), ex);
            throw ex; // Permitir que el manejador superior gestione retries o alertas
        }
    }
}
