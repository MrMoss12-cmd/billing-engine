package com.worksphere.billing.domain.event;

import java.time.Instant;
import java.util.UUID;

public class NotificationEvent {
    private String eventId;
    private String type;
    private String tenantId;
    private String invoiceId;
    private String message;
    private Instant createdAt;

    public NotificationEvent(String type, String tenantId, String invoiceId, String message) {
        this.eventId = UUID.randomUUID().toString();
        this.type = type;
        this.tenantId = tenantId;
        this.invoiceId = invoiceId;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(String invoiceId) {
        this.invoiceId = invoiceId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
