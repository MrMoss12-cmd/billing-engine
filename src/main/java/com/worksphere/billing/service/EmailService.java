package com.worksphere.billing.service;

import com.worksphere.billing.model.Invoice;
import com.worksphere.billing.usecase.notification.EmitBillingEventToKafka;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Servicio encargado de enviar correos electrónicos confiables y auditables.
 * Principalmente para enviar facturas electrónicas y notificaciones financieras.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final EmitBillingEventToKafka eventEmitter;

    @Value("${billing.email.from:no-reply@worksphere.com}")
    private String defaultFrom;

    public EmailService(JavaMailSender mailSender, EmitBillingEventToKafka eventEmitter) {
        this.mailSender = mailSender;
        this.eventEmitter = eventEmitter;
    }

    /**
     * Envía una factura por correo electrónico al tenant.
     *
     * @param invoice factura a enviar
     */
    public void sendInvoice(Invoice invoice) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

            helper.setFrom(defaultFrom);
            helper.setTo(invoice.getTenantEmail());
            helper.setSubject("Factura Electrónica #" + invoice.getInvoiceId());
            helper.setText(buildEmailBody(invoice), true);

            if (invoice.getPdfContent() != null) {
                helper.addAttachment("invoice_" + invoice.getInvoiceId() + ".pdf",
                        new ByteArrayResource(invoice.getPdfContent()));
            }

            // Enviar email
            mailSender.send(message);

            log.info("Factura enviada correctamente a {} para invoice_id {}", invoice.getTenantEmail(), invoice.getInvoiceId());
            eventEmitter.emit(invoice, "invoice_email_sent");

        } catch (MessagingException ex) {
            log.error("Error al enviar factura a {} para invoice_id {}: {}",
                    invoice.getTenantEmail(), invoice.getInvoiceId(), ex.getMessage(), ex);
            eventEmitter.emit(invoice, "invoice_email_failed");
            // Aquí se pueden implementar reintentos automáticos según política de negocio
        }
    }

    /**
     * Construye el cuerpo del correo electrónico para la factura.
     *
     * @param invoice factura
     * @return HTML de email
     */
    private String buildEmailBody(Invoice invoice) {
        // Plantilla básica, se puede externalizar a Thymeleaf o FreeMarker para branding por tenant
        return "<html><body>"
                + "<h3>Factura Electrónica #" + invoice.getInvoiceId() + "</h3>"
                + "<p>Tenant: " + invoice.getTenantName() + "</p>"
                + "<p>Período: " + invoice.getBillingCyclePeriod() + "</p>"
                + "<p>Total a pagar: " + invoice.getTotalAmount() + " " + invoice.getCurrency() + "</p>"
                + "<p>Emitida el: " + Instant.now() + "</p>"
                + "<p>Gracias por su preferencia.</p>"
                + "</body></html>";
    }
}
