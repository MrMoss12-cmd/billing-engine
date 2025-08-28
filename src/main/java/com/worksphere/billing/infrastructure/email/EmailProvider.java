package com.worksphere.billing.infrastructure.email;

/**
 * Interfaz para proveedores de correo electrónico.
 * 
 * Soporta múltiples implementaciones: SMTP, SendGrid, Amazon SES, etc.
 * Permite enviar correos con adjuntos PDF.
 */
public interface EmailProvider {

    /**
     * Envía un correo electrónico transaccional con soporte para adjuntar factura en PDF.
     *
     * @param to destinatario
     * @param subject asunto del correo
     * @param htmlBody cuerpo HTML del correo
     * @param pdfAttachment archivo PDF de la factura (puede ser null)
     * @throws EmailSendException si ocurre un error en el envío
     */
    void sendEmail(String to, String subject, String htmlBody, byte[] pdfAttachment) throws EmailSendException;
}
