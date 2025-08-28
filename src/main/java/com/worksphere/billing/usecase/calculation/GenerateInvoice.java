package com.worksphere.billing.usecase.calculation;

import com.worksphere.billing.domain.model.BillingRequest;
import com.worksphere.billing.domain.model.BillingCycle;
import com.worksphere.billing.domain.model.Invoice;
import com.worksphere.billing.service.InvoiceSigner;
import com.worksphere.billing.domain.exception.InvoiceGenerationException;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Caso de uso que consolida toda la información y genera la factura final.
 */
@Component
public class GenerateInvoice {

    private static final Logger logger = LoggerFactory.getLogger(GenerateInvoice.class);

    private final InvoiceSigner invoiceSigner;

    public GenerateInvoice(InvoiceSigner invoiceSigner) {
        this.invoiceSigner = invoiceSigner;
    }

    /**
     * Genera una factura final con prorrateo, impuestos y firma digital.
     *
     * @param billingRequest Información de facturación del tenant
     * @param billingCycle   Ciclo de facturación
     * @param baseAmount     Monto base calculado
     * @param taxAmount      Monto total de impuestos calculado
     * @param proratedAmount Monto prorrateado
     * @return Invoice generado
     */
    public Invoice execute(BillingRequest billingRequest,
                           BillingCycle billingCycle,
                           BigDecimal baseAmount,
                           BigDecimal taxAmount,
                           BigDecimal proratedAmount) {

        try {
            String tenantId = billingRequest.getTenantId();
            String billingCycleId = billingCycle.getBillingCycleId();

            // -----------------------------
            // 1. Construcción completa del Invoice
            // -----------------------------
            Invoice invoice = new Invoice();
            invoice.setTenantId(tenantId);
            invoice.setBillingCycleId(billingCycleId);
            invoice.setBaseAmount(baseAmount);
            invoice.setProratedAmount(proratedAmount);
            invoice.setTaxAmount(taxAmount);
            invoice.setTotalAmount(baseAmount.add(proratedAmount).add(taxAmount));
            invoice.setIssueDate(LocalDateTime.now());
            invoice.setDueDate(billingCycle.getEndDate().atStartOfDay());
            invoice.setStatus("GENERATED");

            // -----------------------------
            // 2. Idempotencia: verificar si ya existe una factura generada
            // (aquí se puede integrar con repositorio para chequeo)
            // -----------------------------
            // TODO: validar existencia antes de persistir

            // -----------------------------
            // 3. Firma digital
            // -----------------------------
            invoiceSigner.signInvoice(invoice);

            // -----------------------------
            // 4. Auditabilidad: log del Invoice generado
            // -----------------------------
            logger.info("Invoice generado tenant={} billingCycle={} invoiceId={}",
                        tenantId, billingCycleId, invoice.getInvoiceId());

            // -----------------------------
            // 5. Preparación para distribución
            // -----------------------------
            // Se puede serializar a PDF/XML mediante utils en otra capa

            return invoice;

        } catch (Exception ex) {
            logger.error("Error generando factura tenant={} billingCycle={}: {}",
                         billingRequest.getTenantId(), billingCycle.getBillingCycleId(), ex.getMessage(), ex);
            throw new InvoiceGenerationException(
                    "Error generando invoice", 
                    billingRequest.getTenantId(), 
                    billingCycle.getBillingCycleId());
        }
    }
}
