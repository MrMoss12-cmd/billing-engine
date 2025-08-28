package com.worksphere.billing.usecase.audit.dto;

import java.time.Instant;

/** Filtros de consulta para logs de operación. */
public class BillingOperationLogFilter {
    private String tenantId;
    private String billingCycleId;
    private String operationType; // CALCULATION, PAYMENT, REVERSAL, INVOICE_HISTORY_QUERY, etc.
    private Instant startDate;
    private Instant endDate;

    // Paginación / orden
    private int page = 0;
    private int size = 50;
    private boolean asc = false; // por defecto, más recientes primero
    private String sortBy = "timestamp";

    // Getters & Setters + toString()
    // ...
    @Override
    public String toString() {
        return "BillingOperationLogFilter{" +
                "tenantId='" + tenantId + '\'' +
                ", billingCycleId='" + billingCycleId + '\'' +
                ", operationType='" + operationType + '\'' +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", page=" + page +
                ", size=" + size +
                ", asc=" + asc +
                ", sortBy='" + sortBy + '\'' +
                '}';
    }

    // getters y setters omitidos por brevedad
}
