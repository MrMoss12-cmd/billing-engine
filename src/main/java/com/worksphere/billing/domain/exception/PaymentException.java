package com.worksphere.billing.domain.exception;

/**
 * Excepción que se lanza cuando un pago no puede procesarse correctamente.
 * Puede deberse a rechazo del gateway, token inválido, saldo insuficiente o error de comunicación.
 */
public class PaymentException extends RuntimeException {

    // -----------------------------
    // Contexto financiero
    // -----------------------------
    private final String paymentId;
    private final String billingCycleId;
    private final String invoiceId;

    // -----------------------------
    // Causa específica del fallo
    // -----------------------------
    private final FailureReason failureReason;

    // -----------------------------
    // Indica si el error es recuperable
    // -----------------------------
    private final boolean recoverable;

    // -----------------------------
    // Constructor principal
    // -----------------------------
    public PaymentException(String message,
                            String paymentId,
                            String billingCycleId,
                            String invoiceId,
                            FailureReason failureReason,
                            boolean recoverable) {
        super(message);
        this.paymentId = paymentId;
        this.billingCycleId = billingCycleId;
        this.invoiceId = invoiceId;
        this.failureReason = failureReason;
        this.recoverable = recoverable;
    }

    // -----------------------------
    // Getters
    // -----------------------------
    public String getPaymentId() {
        return paymentId;
    }

    public String getBillingCycleId() {
        return billingCycleId;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public FailureReason getFailureReason() {
        return failureReason;
    }

    public boolean isRecoverable() {
        return recoverable;
    }

    // -----------------------------
    // Enum de causas específicas de fallo
    // -----------------------------
    public enum FailureReason {
        NETWORK_ERROR,         // Problema de comunicación con el gateway
        BANK_REJECTED,         // Banco o pasarela rechazó la transacción
        TOKEN_EXPIRED,         // Token de pago caducado
        INVALID_CREDENTIALS,   // Datos de autenticación incorrectos
        INSUFFICIENT_FUNDS,    // Saldo insuficiente en la cuenta del usuario
        UNKNOWN_ERROR          // Error no categorizado
    }
}
