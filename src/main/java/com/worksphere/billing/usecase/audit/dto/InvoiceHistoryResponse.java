package com.worksphere.billing.usecase.audit.dto;

import com.worksphere.billing.domain.model.Invoice;

import java.time.Instant;
import java.util.List;

/**
 * Respuesta estandarizada de historial de facturas para dashboards o reportes.
 */
public class InvoiceHistoryResponse {
    private String tenantId;
    private List<Invoice> invoices;
    private Instant generatedAt;

    public InvoiceHistoryResponse(String tenantId, List<Invoice> invoices, Instant generatedAt) {
        this.tenantId = tenantId;
        this.invoices = invoices;
        this.generatedAt = generatedAt;
    }

    public String getTenantId() { return tenantId; }
    public List<Invoice> getInvoices() { return invoices; }
    public Instant getGeneratedAt() { return generatedAt; }
}
