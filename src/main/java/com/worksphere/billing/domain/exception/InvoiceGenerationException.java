package com.worksphere.billing.domain.exception;

/**
 * Excepción que se lanza cuando ocurre un fallo al generar una factura.
 * Puede ser un fallo técnico (PDF, firma digital) o de negocio (datos incompletos, plan inexistente).
 */
public class InvoiceGenerationException extends RuntimeException {

    // -----------------------------
    // Identificadores para contexto financiero
    // -----------------------------
    private final String invoiceId;  // Puede ser null si la factura no se llegó a crear
    private final String tenantId;

    // -----------------------------
    // Origen del fallo: técnico o de negocio
    // -----------------------------
    private final FailureType failureType;

    // -----------------------------
    // Impacto legal
    // -----------------------------
    private final boolean invalidatesFiscalObligation;

    // -----------------------------
    // Constructor principal
    // -----------------------------
    public InvoiceGenerationException(String message, String invoiceId, String tenantId,
                                      FailureType failureType, boolean invalidatesFiscalObligation) {
        super(message);
        this.invoiceId = invoiceId;
        this.tenantId = tenantId;
        this.failureType = failureType;
        this.invalidatesFiscalObligation = invalidatesFiscalObligation;
    }

    // -----------------------------
    // Getters
    // -----------------------------
    public String getInvoiceId() {
        return invoiceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public FailureType getFailureType() {
        return failureType;
    }

    public boolean isInvalidatesFiscalObligation() {
        return invalidatesFiscalObligation;
    }

    // -----------------------------
    // Enum para clasificar el origen del fallo
    // -----------------------------
    public enum FailureType {
        TECHNICAL,  // Fallos de sistema, PDF, firma digital, etc.
        BUSINESS    // Datos incompletos, plan inexistente, reglas fiscales no cumplidas
    }
}
