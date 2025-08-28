package com.worksphere.billing.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Representa el resultado de un intento de pago dentro del dominio billing-engine.
 * Permite trazabilidad, auditabilidad y consistencia con la factura.
 */
@Entity
@Table(name = "payment_results", indexes = {
        @Index(name = "idx_payment_result_tenant", columnList = "tenantId"),
        @Index(name = "idx_payment_result_invoice", columnList = "invoiceId")
})
public class PaymentResult {

    // -----------------------------
    // Identidad única
    // -----------------------------
    @Id
    @Column(name = "payment_result_id", nullable = false, updatable = false)
    private UUID paymentResultId;

    // -----------------------------
    // Asociación multi-tenant y referencia
    // -----------------------------
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "billing_cycle_id", nullable = false)
    private UUID billingCycleId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    // -----------------------------
    // Estado de la transacción
    // -----------------------------
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    // -----------------------------
    // Referencia del gateway de pago
    // -----------------------------
    @Column(name = "gateway_transaction_id", nullable = false)
    private String gatewayTransactionId;

    // -----------------------------
    // Timestamps y auditabilidad
    // -----------------------------
    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @ElementCollection
    @CollectionTable(name = "payment_result_attempt_log", joinColumns = @JoinColumn(name = "payment_result_id"))
    @Column(name = "attempt_detail")
    private List<String> attemptLog = new ArrayList<>();

    @Column(name = "paid", nullable = false)
    private boolean paid;

    // -----------------------------
    // Constructor
    // -----------------------------
    public PaymentResult() {
        this.paymentResultId = UUID.randomUUID();
        this.processedAt = LocalDateTime.now();
        this.status = PaymentStatus.PENDING;
        this.attempts = 0;
        this.paid = false;
    }

    // -----------------------------
    // Getters & Setters
    // -----------------------------
    public UUID getPaymentResultId() {
        return paymentResultId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getBillingCycleId() {
        return billingCycleId;
    }

    public void setBillingCycleId(UUID billingCycleId) {
        this.billingCycleId = billingCycleId;
    }

    public UUID getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(UUID invoiceId) {
        this.invoiceId = invoiceId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getGatewayTransactionId() {
        return gatewayTransactionId;
    }

    public void setGatewayTransactionId(String gatewayTransactionId) {
        this.gatewayTransactionId = gatewayTransactionId;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void incrementAttempts(String logDetail) {
        this.attempts++;
        this.attemptLog.add(logDetail);
    }

    public List<String> getAttemptLog() {
        return attemptLog;
    }

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    // -----------------------------
    // Enum de estado de pago
    // -----------------------------
    public enum PaymentStatus {
        SUCCESS,
        FAILED,
        PENDING
    }
}
