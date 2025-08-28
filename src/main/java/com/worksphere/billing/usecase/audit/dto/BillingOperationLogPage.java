package com.worksphere.billing.usecase.audit.dto;

import com.worksphere.billing.domain.model.BillingOperationLog;

import java.time.Instant;
import java.util.List;

/** Respuesta paginada y con metadatos para dashboards/reportes. */
public class BillingOperationLogPage {
    private final List<BillingOperationLog> items;
    private final int page;
    private final int size;
    private final long totalItems;
    private final int totalPages;
    private final Instant generatedAt;

    public BillingOperationLogPage(List<BillingOperationLog> items, int page, int size,
                                   long totalItems, int totalPages, Instant generatedAt) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.totalItems = totalItems;
        this.totalPages = totalPages;
        this.generatedAt = generatedAt;
    }

    public List<BillingOperationLog> getItems() { return items; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getTotalItems() { return totalItems; }
    public int getTotalPages() { return totalPages; }
    public Instant getGeneratedAt() { return generatedAt; }
}
