package com.worksphere.billing.transport.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento que indica que un ciclo de facturación para un tenant ha finalizado correctamente.
 * Este evento puede ser emitido a Kafka, gRPC o REST para notificar a otros microservicios.
 */
public class BillingCompletedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("event_id")
    private final String eventId;

    @JsonProperty("tenant_id")
    private final String tenantId;

    @JsonProperty("billing_cycle_id")
    private final String billingCycleId;

    @JsonProperty("total_amount")
    private final BigDecimal totalAmount;

    @JsonProperty("currency")
    private final String currency;

    @JsonProperty("completed_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private final Instant completedAt;

    @JsonProperty("status")
    private final String status;

    public BillingCompletedEvent(String tenantId, String billingCycleId, BigDecimal totalAmount, String currency, Instant completedAt, String status) {
        this.eventId = UUID.randomUUID().toString(); // Garantiza idempotencia por eventId único
        this.tenantId = tenantId;
        this.billingCycleId = billingCycleId;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.completedAt = completedAt;
        this.status = status;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getBillingCycleId() {
        return billingCycleId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BillingCompletedEvent)) return false;
        BillingCompletedEvent that = (BillingCompletedEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "BillingCompletedEvent{" +
                "eventId='" + eventId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", billingCycleId='" + billingCycleId + '\'' +
                ", totalAmount=" + totalAmount +
                ", currency='" + currency + '\'' +
                ", completedAt=" + completedAt +
                ", status='" + status + '\'' +
                '}';
    }
}
