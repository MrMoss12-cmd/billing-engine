package com.worksphere.billing.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

import java.time.Instant;

/**
 * Entidad persistente para registrar operaciones de facturación.
 */
@Entity
@Table(name = "billing_operation_logs")
public class BillingOperationLog {

    @Id
    private String id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String billingCycleId;

    private String invoiceId;

    @Column(nullable = false)
    private String operationType; // CALCULATION, PAYMENT, REVERSAL, etc.

    @Column(nullable = false)
    private String actor; // usuario o sistema

    @Column(nullable = false)
    private Instant timestamp;

    @Column(columnDefinition = "TEXT")
    private String details; // JSON u otro formato extensible

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getBillingCycleId() { return billingCycleId; }
    public void setBillingCycleId(String billingCycleId) { this.billingCycleId = billingCycleId; }

    public String getInvoiceId() { return invoiceId; }
    public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }

    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
