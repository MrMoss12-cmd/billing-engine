package com.worksphere.billing.usecase.notification;

import com.worksphere.billing.domain.event.NotificationEvent;
import com.worksphere.billing.domain.model.Invoice;
import com.worksphere.billing.domain.model.Tenant;
import com.worksphere.billing.infrastructure.email.EmailProvider;
import com.worksphere.billing.infrastructure.repository.InvoiceEmailLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Caso de uso para enviar facturas electrónicas por correo al tenant.
 * Implementa plantillas, entregabilidad, trazabilidad y eventos derivados.
 */
@Service
public class SendInvoiceEmailToTenant {

    private static final Logger log = LoggerFactory.getLogger(SendInvoiceEmailToTenant.class);

    private final EmailProvider emailProvider;
    private final InvoiceEmailLogRepository emailLogRepository;
    private final EmitNotificationEvent emitNotificationEvent;

    public SendInvoiceEmailToTenant(EmailProvider emailProvider,
                                    InvoiceEmailLogRepository emailLogRepository,
                                    EmitNotificationEvent emitNotificationEvent) {
        this.emailProvider = emailProvider;
        this.emailLogRepository = emailLogRepository;
        this.emitNotificationEvent = emitNotificationEvent;
    }

    /**
     * Envía la factura por correo electrónico al tenant correspondiente.
     *
     * @param tenant Tenant al que se le enviará la factura
     * @param invoice Factura a enviar
     */
    @Transactional
    public void send(Tenant tenant, Invoice invoice) {
        try {
            // Construir plantilla HTML personalizada
            String subject = String.format("Factura #%s - %s", invoice.getInvoiceId(), tenant.getName());
            String body = buildInvoiceTemplate(tenant, invoice);

            // Enviar correo (con adjunto PDF si aplica)
            emailProvider.sendEmail(tenant.getEmail(), subject, body, invoice.getPdfBytes());

            // Registrar éxito
            emailLogRepository.saveInvoiceEmailLog(invoice.getInvoiceId(), tenant.getTenantId(),
                    "SENT", Instant.now(), null);

            log.info("Factura [{}] enviada exitosamente al tenant [{}]", invoice.getInvoiceId(), tenant.getTenantId());

            // Emitir evento derivado
            emitNotificationEvent.emit(new NotificationEvent(
                    "invoice_email_sent",
                    tenant.getTenantId(),
                    invoice.getInvoiceId(),
                    "Email enviado correctamente"
            ));

        } catch (Exception ex) {
            log.error("Error al enviar la factura [{}] al tenant [{}]: {}", invoice.getInvoiceId(), tenant.getTenantId(), ex.getMessage());

            // Registrar fallo
            emailLogRepository.saveInvoiceEmailLog(invoice.getInvoiceId(), tenant.getTenantId(),
                    "FAILED", Instant.now(), ex.getMessage());

            // Emitir evento derivado
            emitNotificationEvent.emit(new NotificationEvent(
                    "invoice_email_failed",
                    tenant.getTenantId(),
                    invoice.getInvoiceId(),
                    "Error al enviar factura: " + ex.getMessage()
            ));

            // Manejo de reintentos: aquí puedes implementar un retry (Quartz, Spring Retry, Dead Letter Queue, etc.)
        }
    }

    /**
     * Genera plantilla de email con datos del tenant y de la factura.
     */
    private String buildInvoiceTemplate(Tenant tenant, Invoice invoice) {
        return "<html>" +
                "<body>" +
                "<h2>Hola " + tenant.getName() + ",</h2>" +
                "<p>Te compartimos tu factura electrónica.</p>" +
                "<ul>" +
                "<li><strong>Factura:</strong> " + invoice.getInvoiceId() + "</li>" +
                "<li><strong>Plan:</strong> " + tenant.getPlanName() + "</li>" +
                "<li><strong>Monto:</strong> $" + invoice.getAmount() + "</li>" +
                "<li><strong>Vencimiento:</strong> " + invoice.getDueDate() + "</li>" +
                "</ul>" +
                "<p>Gracias por confiar en nosotros.</p>" +
                "</body>" +
                "</html>";
    }
}
