package com.worksphere.billing.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representa un ciclo de facturación para un tenant.
 * Controla periodicidad, estado y trazabilidad de cada facturación.
 */
@Entity
@Table(name = "billing_cycles", indexes = {
        @Index(name = "idx_billing_cycle_tenant", columnList = "tenantId")
})
public class BillingCycle {

    // -----------------------------
    // Identificador único del ciclo
    // -----------------------------
    @Id
    @Column(name = "billing_cycle_id", nullable = false, updatable = false)
    private UUID billingCycleId;

    // -----------------------------
    // Asociación al tenant
    // -----------------------------
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    // -----------------------------
    // Período temporal del ciclo
    // -----------------------------
    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    // -----------------------------
    // Estado del ciclo
    // -----------------------------
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BillingCycleStatus status;

    // -----------------------------
    // Información de auditoría
    // -----------------------------
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // -----------------------------
    // Constructor
    // -----------------------------
    public BillingCycle() {
        this.billingCycleId = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
        this.status = BillingCycleStatus.SCHEDULED;
    }

    // -----------------------------
    // Getters & Setters
    // -----------------------------
    public UUID getBillingCycleId() {
        return billingCycleId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDateTime startDate) {
        this.startDate = startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        this.endDate = endDate;
    }

    public BillingCycleStatus getStatus() {
        return status;
    }

    public void setStatus(BillingCycleStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // -----------------------------
    // Enum de estados del ciclo
    // -----------------------------
    public enum BillingCycleStatus {
        SCHEDULED,    // Programado pero no iniciado
        IN_PROGRESS,  // Facturación en curso
        COMPLETED,    // Facturación finalizada con éxito
        FAILED        // Facturación fallida o interrumpida
    }

    // -----------------------------
    // Validación de duplicados y consistencia
    // -----------------------------
    public boolean canExecute() {
        return this.status == BillingCycleStatus.SCHEDULED;
    }
}
