package com.worksphere.billing.utils;

import com.worksphere.billing.domain.model.Invoice;
import com.worksphere.billing.service.BillingEngine;
import com.worksphere.billing.service.EmailService;
import com.worksphere.billing.service.InvoiceSigner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Utilidades para la creación, validación y manipulación de facturas.
 */
@Component
public class InvoiceUtils {

    private static final Logger logger = Logger.getLogger(InvoiceUtils.class.getName());

    private final BillingEngine billingEngine;
    private final InvoiceSigner invoiceSigner;
    private final EmailService emailService;

    @Autowired
    public InvoiceUtils(BillingEngine billingEngine,
                        InvoiceSigner invoiceSigner,
                        EmailService emailService) {
        this.billingEngine = billingEngine;
        this.invoiceSigner = invoiceSigner;
        this.emailService = emailService;
    }

    /**
     * Valida que todos los campos obligatorios de la factura estén presentes.
     *
     * @param invoice Factura a validar
     * @return true si la factura es válida, false si faltan campos
     */
    public boolean validateInvoice(Invoice invoice) {
        boolean valid = invoice != null
                && Objects.nonNull(invoice.getInvoiceId())
                && Objects.nonNull(invoice.getTenantId())
                && Objects.nonNull(invoice.getBillingCycleId())
                && invoice.getAmount() != null
                && invoice.getAmount().compareTo(invoice.getAmount()) >= 0;

        logger.info("Validación de factura: invoiceId=" + (invoice != null ? invoice.getInvoiceId() : "null")
                + ", resultado=" + valid);

        return valid;
    }

    /**
     * Verifica la consistencia de la factura antes de enviarla o firmarla.
     *
     * @param invoice Factura a revisar
     * @return true si la factura es consistente, false si hay inconsistencias
     */
    public boolean checkIntegrity(Invoice invoice) {
        boolean consistent = invoice != null
                && invoice.getAmount().compareTo(invoice.getTax().add(invoice.getBaseAmount())) == 0;

        logger.info("Chequeo de integridad: invoiceId=" + (invoice != null ? invoice.getInvoiceId() : "null")
                + ", consistente=" + consistent);

        return consistent;
    }

    /**
     * Firma digitalmente la factura usando InvoiceSigner.
     *
     * @param invoice Factura a firmar
     */
    public void signInvoice(Invoice invoice) {
        if (!validateInvoice(invoice)) {
            logger.warning("No se puede firmar factura inválida: invoiceId=" + invoice.getInvoiceId());
            return;
        }

        try {
            invoiceSigner.sign(invoice);
            logger.info("Factura firmada correctamente: invoiceId=" + invoice.getInvoiceId());
        } catch (Exception e) {
            logger.severe("Error al firmar factura: invoiceId=" + invoice.getInvoiceId() + ", error=" + e.getMessage());
        }
    }

    /**
     * Envía la factura por email al tenant usando EmailService.
     *
     * @param invoice Factura a enviar
     */
    public void sendInvoiceEmail(Invoice invoice) {
        if (!validateInvoice(invoice)) {
            logger.warning("No se puede enviar factura inválida: invoiceId=" + invoice.getInvoiceId());
            return;
        }

        try {
            emailService.sendInvoice(invoice);
            logger.info("Factura enviada por email: invoiceId=" + invoice.getInvoiceId());
        } catch (Exception e) {
            logger.severe("Error al enviar email de factura: invoiceId=" + invoice.getInvoiceId() + ", error=" + e.getMessage());
        }
    }

    /**
     * Aplica transformaciones o ajustes antes de emitir la factura.
     *
     * @param invoice Factura a transformar
     */
    public void preprocessInvoice(Invoice invoice) {
        // Ejemplo: asegurarse de que el número de factura tenga un formato estándar
        if (invoice != null && invoice.getInvoiceNumber() != null) {
            invoice.setInvoiceNumber(invoice.getInvoiceNumber().toUpperCase());
            logger.info("Preprocesamiento de factura: invoiceNumber normalizado=" + invoice.getInvoiceNumber());
        }
    }
}
