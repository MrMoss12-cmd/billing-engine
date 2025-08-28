package com.worksphere.billing.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Representa un evento de facturación estándar.
 */
public class BillingEvent {
    private String eventId;
    private String tenantId;
    private String eventType; // e.g. billing_started, invoice_generated, payment_failed
    private Instant createdAt;
    private Instant emittedAt;
    private String payload;

    public BillingEvent(String tenantId, String eventType, String payload) {
        this.eventId = UUID.randomUUID().toString();
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.createdAt = Instant.now();
        this.payload = payload;
    }

    public BillingEvent withEmittedAt(Instant emittedAt) {
        this.emittedAt = emittedAt;
        return this;
    }

    // getters y setters
}
