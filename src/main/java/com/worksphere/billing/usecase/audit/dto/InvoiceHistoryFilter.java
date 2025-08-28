package com.worksphere.billing.usecase.audit.dto;

import java.time.Instant;

/**
 * Filtros opcionales para la consulta de historial de facturas.
 */
public class InvoiceHistoryFilter {
    private Instant startDate;
    private Instant endDate;
    private String status; // ej. PAID, PENDING, CANCELLED

    // Getters & Setters
    public Instant getStartDate() { return startDate; }
    public void setStartDate(Instant startDate) { this.startDate = startDate; }

    public Instant getEndDate() { return endDate; }
    public void setEndDate(Instant endDate) { this.endDate = endDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @Override
    public String toString() {
        return "InvoiceHistoryFilter{" +
                "startDate=" + startDate +
                ", endDate=" + endDate +
                ", status='" + status + '\'' +
                '}';
    }
}
