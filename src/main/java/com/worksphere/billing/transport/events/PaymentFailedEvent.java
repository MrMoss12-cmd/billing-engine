package com.worksphere.billing.transport.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento que indica que un pago de un ciclo de facturación ha fallado.
 * Este evento puede ser emitido a Kafka, gRPC o REST para notificar a otros microservicios.
 */
public class PaymentFailedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("event_id")
    private final String eventId;

    @JsonProperty("tenant_id")
    private final String tenantId;

    @JsonProperty("invoice_id")
    private final String invoiceId;

    @JsonProperty("billing_cycle_id")
    private final String billingCycleId;

    @JsonProperty("failed_amount")
    private final BigDecimal failedAmount;

    @JsonProperty("failure_type")
    private final String failureType; // Ej: "insufficient_funds", "token_expired"

    @JsonProperty("occurred_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private final Instant occurredAt;

    public PaymentFailedEvent(String tenantId, String invoiceId, String billingCycleId, BigDecimal failedAmount, String failureType, Instant occurredAt) {
        this.eventId = UUID.randomUUID().toString(); // Idempotencia por eventId único
        this.tenantId = tenantId;
        this.invoiceId = invoiceId;
        this.billingCycleId = billingCycleId;
        this.failedAmount = failedAmount;
        this.failureType = failureType;
        this.occurredAt = occurredAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getInvoiceId() {
        return invoiceId;
    }

    public String getBillingCycleId() {
        return billingCycleId;
    }

    public BigDecimal getFailedAmount() {
        return failedAmount;
    }

    public String getFailureType() {
        return failureType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentFailedEvent)) return false;
        PaymentFailedEvent that = (PaymentFailedEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "PaymentFailedEvent{" +
                "eventId='" + eventId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", invoiceId='" + invoiceId + '\'' +
                ", billingCycleId='" + billingCycleId + '\'' +
                ", failedAmount=" + failedAmount +
                ", failureType='" + failureType + '\'' +
                ", occurredAt=" + occurredAt +
                '}';
    }
}
