package com.worksphere.billing.transport.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento que indica que una factura ha sido generada y está lista para envío o firma.
 * Puede ser emitido a Kafka, gRPC o REST para notificar a otros microservicios.
 */
public class InvoiceGeneratedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("event_id")
    private final String eventId;

    @JsonProperty("tenant_id")
    private final String tenantId;

    @JsonProperty("invoice_id")
    private final String invoiceId;

    @JsonProperty("billing_cycle_id")
    private final String billingCycleId;

    @JsonProperty("total_amount")
    private final BigDecimal totalAmount;

    @JsonProperty("tax_amount")
    private final BigDecimal taxAmount;

    @JsonProperty("issued_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private final Instant issuedAt;

    public InvoiceGeneratedEvent(String tenantId, String invoiceId, String billingCycleId,
                                 BigDecimal totalAmount, BigDecimal taxAmount, Instant issuedAt) {
        this.eventId = UUID.randomUUID().toString(); // Idempotencia
        this.tenantId = tenantId;
        this.invoiceId = invoiceId;
        this.billingCycleId = billingCycleId;
        this.totalAmount = totalAmount;
        this.taxAmount = taxAmount;
        this.issuedAt = issuedAt;
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

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InvoiceGeneratedEvent)) return false;
        InvoiceGeneratedEvent that = (InvoiceGeneratedEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }

    @Override
    public String toString() {
        return "InvoiceGeneratedEvent{" +
                "eventId='" + eventId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", invoiceId='" + invoiceId + '\'' +
                ", billingCycleId='" + billingCycleId + '\'' +
                ", totalAmount=" + totalAmount +
                ", taxAmount=" + taxAmount +
                ", issuedAt=" + issuedAt +
                '}';
    }
}
