package com.worksphere.billing.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representa una factura dentro del dominio billing-engine.
 * Cumple con requisitos legales, financieros y multi-tenant.
 */
@Entity
@Table(name = "invoices", indexes = {
        @Index(name = "idx_invoice_tenant", columnList = "tenantId")
})
public class Invoice {

    // -----------------------------
    // Identidad única
    // -----------------------------
    @Id
    @Column(name = "invoice_id", nullable = false, updatable = false)
    private UUID invoiceId;

    // -----------------------------
    // Asociación multi-tenant
    // -----------------------------
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    // -----------------------------
    // Contenido financiero exacto
    // -----------------------------
    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "tax", nullable = false)
    private BigDecimal tax;

    @Column(name = "total", nullable = false)
    private BigDecimal total;

    // -----------------------------
    // Estado del documento
    // -----------------------------
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status;

    // -----------------------------
    // Cumplimiento legal
    // -----------------------------
    @Column(name = "fiscal_number")
    private String fiscalNumber;

    @Column(name = "digital_signature")
    private String digitalSignature;

    @Column(name = "cufe")
    private String cufe;

    @Column(name = "nit")
    private String nit;

    // -----------------------------
    // Trazabilidad
    // -----------------------------
    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "due_at", nullable = false)
    private LocalDateTime dueAt;

    @Column(name = "payment_reference")
    private String paymentReference;

    // -----------------------------
    // Constructor
    // -----------------------------
    public Invoice() {
        this.invoiceId = UUID.randomUUID();
        this.issuedAt = LocalDateTime.now();
        this.status = InvoiceStatus.GENERATED;
    }

    // -----------------------------
    // Getters & Setters
    // -----------------------------
    public UUID getInvoiceId() {
        return invoiceId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public InvoiceStatus getStatus() {
        return status;
    }

    public void setStatus(InvoiceStatus status) {
        this.status = status;
    }

    public String getFiscalNumber() {
        return fiscalNumber;
    }

    public void setFiscalNumber(String fiscalNumber) {
        this.fiscalNumber = fiscalNumber;
    }

    public String getDigitalSignature() {
        return digitalSignature;
    }

    public void setDigitalSignature(String digitalSignature) {
        this.digitalSignature = digitalSignature;
    }

    public String getCufe() {
        return cufe;
    }

    public void setCufe(String cufe) {
        this.cufe = cufe;
    }

    public String getNit() {
        return nit;
    }

    public void setNit(String nit) {
        this.nit = nit;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public void setDueAt(LocalDateTime dueAt) {
        this.dueAt = dueAt;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    // -----------------------------
    // Enum para estado de la factura
    // -----------------------------
    public enum InvoiceStatus {
        GENERATED,
        SIGNED,
        SENT,
        PAID,
        CANCELLED
    }
}
