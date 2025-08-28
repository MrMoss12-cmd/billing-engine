package com.worksphere.billing.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representa una solicitud de facturación dentro del dominio billing-engine.
 * Se usa para iniciar procesos de facturación de manera automática o manual.
 */
@Entity
@Table(name = "billing_requests", indexes = {
        @Index(name = "idx_billing_request_tenant", columnList = "tenantId"),
        @Index(name = "idx_billing_request_cycle", columnList = "billingCycleId")
})
public class BillingRequest implements Serializable {

    // -----------------------------
    // Identidad única de la solicitud
    // -----------------------------
    @Id
    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    // -----------------------------
    // Contexto de facturación
    // -----------------------------
    @NotNull
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @NotNull
    @Column(name = "plan_id", nullable = false)
    private String planId;

    @NotNull
    @Column(name = "billing_period_start", nullable = false)
    private LocalDateTime billingPeriodStart;

    @NotNull
    @Column(name = "billing_period_end", nullable = false)
    private LocalDateTime billingPeriodEnd;

    // -----------------------------
    // Consumo asociado
    // -----------------------------
    @Column(name = "usage_units", nullable = false)
    private BigDecimal usageUnits; // por ejemplo: minutos, GB, instancias

    @Column(name = "usage_details")
    private String usageDetails; // JSON o texto libre con métricas adicionales

    // -----------------------------
    // Planificación y ciclo asociado
    // -----------------------------
    @Column(name = "billing_cycle_id", nullable = false)
    private UUID billingCycleId;

    // -----------------------------
    // Validación previa
    // -----------------------------
    @Column(name = "validated", nullable = false)
    private boolean validated;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // -----------------------------
    // Constructor
    // -----------------------------
    public BillingRequest() {
        this.requestId = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
        this.validated = false;
    }

    // -----------------------------
    // Getters & Setters
    // -----------------------------
    public UUID getRequestId() {
        return requestId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public LocalDateTime getBillingPeriodStart() {
        return billingPeriodStart;
    }

    public void setBillingPeriodStart(LocalDateTime billingPeriodStart) {
        this.billingPeriodStart = billingPeriodStart;
    }

    public LocalDateTime getBillingPeriodEnd() {
        return billingPeriodEnd;
    }

    public void setBillingPeriodEnd(LocalDateTime billingPeriodEnd) {
        this.billingPeriodEnd = billingPeriodEnd;
    }

    public BigDecimal getUsageUnits() {
        return usageUnits;
    }

    public void setUsageUnits(BigDecimal usageUnits) {
        this.usageUnits = usageUnits;
    }

    public String getUsageDetails() {
        return usageDetails;
    }

    public void setUsageDetails(String usageDetails) {
        this.usageDetails = usageDetails;
    }

    public UUID getBillingCycleId() {
        return billingCycleId;
    }

    public void setBillingCycleId(UUID billingCycleId) {
        this.billingCycleId = billingCycleId;
    }

    public boolean isValidated() {
        return validated;
    }

    public void setValidated(boolean validated) {
        this.validated = validated;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

}
