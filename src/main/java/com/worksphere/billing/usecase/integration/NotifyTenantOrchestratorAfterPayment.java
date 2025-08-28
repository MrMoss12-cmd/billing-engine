package com.worksphere.billing.usecase.integration;

import com.worksphere.billing.domain.model.PaymentResult;
import com.worksphere.billing.domain.repository.PaymentResultRepository;
import com.worksphere.billing.infrastructure.repository.NotificationLogRepository;
import com.worksphere.billing.transport.events.EventPublisher;
import com.worksphere.billing.transport.events.TenantOrchestratorNotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Caso de uso para notificar al tenant-orchestrator después de un pago exitoso.
 *
 * Requisitos cubiertos:
 * - Notificar únicamente si el pago fue exitoso.
 * - Incluir tenant, billingCycleId, invoiceId y monto.
 * - Idempotencia: no notificar dos veces para la misma transacción.
 * - Seguridad: se delega a TenantOrchestratorClient (mTLS/JWT).
 * - Auditabilidad: registro de notificación y respuesta.
 * - Reintentos controlados: RetryTemplate con backoff exponencial.
 */
@Component
public class NotifyTenantOrchestratorAfterPayment {

    private static final Logger log = LoggerFactory.getLogger(NotifyTenantOrchestratorAfterPayment.class);

    private final PaymentResultRepository paymentResultRepository;
    private final TenantOrchestratorClient tenantOrchestratorClient;
    private final NotificationLogRepository notificationLogRepository;
    private final EventPublisher eventPublisher;
    private final RetryTemplate retryTemplate;

    public NotifyTenantOrchestratorAfterPayment(PaymentResultRepository paymentResultRepository,
                                                TenantOrchestratorClient tenantOrchestratorClient,
                                                NotificationLogRepository notificationLogRepository,
                                                EventPublisher eventPublisher) {
        this.paymentResultRepository = paymentResultRepository;
        this.tenantOrchestratorClient = tenantOrchestratorClient;
        this.notificationLogRepository = notificationLogRepository;
        this.eventPublisher = eventPublisher;
        this.retryTemplate = buildRetryTemplate();
    }

    private RetryTemplate buildRetryTemplate() {
        RetryTemplate tpl = new RetryTemplate();

        // Retry policy: up to 4 attempts for transient errors
        SimpleRetryPolicy policy = new SimpleRetryPolicy(4, Map.of(
                // puedes ajustar las excepciones a considerar reintentables
                org.springframework.web.client.ResourceAccessException.class, true,
                java.net.SocketTimeoutException.class, true
        ), true);
        tpl.setRetryPolicy(policy);

        // Exponential backoff: 1s, 2s, 4s, 8s...
        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(8000L);
        tpl.setBackOffPolicy(backOff);

        return tpl;
    }

    /**
     * Notifica al orchestrator que un pago fue exitoso.
     *
     * @param paymentResultId id de PaymentResult (preferible) o null si se pasa PaymentResult completo
     */
    @Transactional
    public void execute(String paymentResultId) {
        log.debug("notify orchestrator triggered for paymentResultId={}", paymentResultId);

        // 1) Recuperar PaymentResult y validar estado
        PaymentResult pr = paymentResultRepository.findById(paymentResultId)
                .orElseThrow(() -> new IllegalArgumentException("PaymentResult not found: " + paymentResultId));

        if (pr.getStatus() == null || !"SUCCESS".equalsIgnoreCase(pr.getStatus())) {
            log.warn("Notify skipped for paymentResultId={} because status is not SUCCESS (status={})",
                    paymentResultId, pr.getStatus());
            return;
        }

        // Compose notification identity for idempotency (use combination of tenant + billingCycle + invoice + gatewayTxId)
        String notificationKey = buildNotificationKey(pr);

        // 2) Idempotencia: verificar si ya se notificó antes
        Optional<NotificationLogRepository.NotificationLog> existing = notificationLogRepository.findByNotificationKey(notificationKey);
        if (existing.isPresent() && existing.get().isSuccess()) {
            log.info("Notification already sent and acknowledged for key={}, skipping", notificationKey);
            return;
        }

        // 3) Preparar payload con integridad de datos
        TenantOrchestratorClient.NotificationPayload payload = TenantOrchestratorClient.NotificationPayload.builder()
                .tenantId(pr.getTenantId())
                .billingCycleId(pr.getBillingCycleId())
                .invoiceId(pr.getInvoiceId())
                .amount(pr.getAmount())
                .gatewayTransactionId(pr.getGatewayTransactionId())
                .timestamp(Instant.now())
                .build();

        // 4) Intentar notificar con política de reintentos y auditoría
        boolean notified = false;
        String responseMessage = null;

        try {
            String result = retryTemplate.execute(context -> {
                // Llamada al cliente seguro (mTLS/JWT). Lanza excepciones en errores transitorios.
                String resp = tenantOrchestratorClient.notifyPaymentSuccess(payload);
                // Si la respuesta indica aceptación, retornamos la respuesta.
                return resp;
            });

            responseMessage = result;
            notified = true;

        } catch (Exception ex) {
            responseMessage = ex.getMessage();
            log.error("Failed to notify tenant orchestrator for key={} after retries: {}", notificationKey, ex.getMessage(), ex);
        } finally {
            // 5) Persistir log de notificación (atomicidad por la transacción)
            NotificationLogRepository.NotificationLog logEntry = new NotificationLogRepository.NotificationLog();
            logEntry.setNotificationKey(notificationKey);
            logEntry.setTenantId(pr.getTenantId());
            logEntry.setBillingCycleId(pr.getBillingCycleId());
            logEntry.setInvoiceId(pr.getInvoiceId());
            logEntry.setPaymentResultId(pr.getPaymentResultId());
            logEntry.setPayload(payload.toString());
            logEntry.setResponse(responseMessage);
            logEntry.setSuccess(notified);
            logEntry.setSentAt(Instant.now());

            notificationLogRepository.save(logEntry);

            // 6) Emitir evento derivado para otros sistemas
            TenantOrchestratorNotificationEvent event = new TenantOrchestratorNotificationEvent(
                    pr.getTenantId(),
                    pr.getBillingCycleId(),
                    pr.getInvoiceId(),
                    pr.getPaymentResultId(),
                    notified,
                    responseMessage
            );
            eventPublisher.publish(event);

            if (notified) {
                log.info("Notified tenant orchestrator successfully for notificationKey={}", notificationKey);
            } else {
                log.warn("Notification to tenant orchestrator failed for notificationKey={} - recorded for retries/alerts", notificationKey);
            }
        }
    }

    private String buildNotificationKey(PaymentResult pr) {
        // Key must be deterministic and unique for the payment:
        // tenantId|billingCycleId|invoiceId|gatewayTransactionId
        return String.join("|",
                safe(pr.getTenantId()),
                safe(pr.getBillingCycleId() == null ? "" : pr.getBillingCycleId().toString()),
                safe(pr.getInvoiceId() == null ? "" : pr.getInvoiceId().toString()),
                safe(pr.getGatewayTransactionId())
        );
    }

    private String safe(Object o) {
        return o == null ? "" : o.toString();
    }

    // ---------------------------
    // Interfaces mínimas/contratos usados por este caso de uso
    // Implementa tus versiones concretas (JPA repos, HTTP client con mTLS, EventPublisher -> Kafka).
    // ---------------------------

    /**
     * Cliente que conoce cómo notificar al tenant-orchestrator de forma segura (mTLS/JWT).
     * Implementar la lógica de transporte (REST/gRPC) y autenticación en la implementación.
     */
    public interface TenantOrchestratorClient {
        /**
         * Notifica evento de pago exitoso al orchestrator. Devuelve mensaje de respuesta o lanza excepción.
         */
        String notifyPaymentSuccess(NotificationPayload payload) throws Exception;

        class NotificationPayload {
            private String tenantId;
            private String billingCycleId;
            private String invoiceId;
            private java.math.BigDecimal amount;
            private String gatewayTransactionId;
            private Instant timestamp;

            // builder convenience
            public static Builder builder() { return new Builder(); }

            public static class Builder {
                private final NotificationPayload p = new NotificationPayload();

                public Builder tenantId(String tenantId) { p.tenantId = tenantId; return this; }
                public Builder billingCycleId(String billingCycleId) { p.billingCycleId = billingCycleId; return this; }
                public Builder invoiceId(String invoiceId) { p.invoiceId = invoiceId; return this; }
                public Builder amount(java.math.BigDecimal amount) { p.amount = amount; return this; }
                public Builder gatewayTransactionId(String id) { p.gatewayTransactionId = id; return this; }
                public Builder timestamp(Instant ts) { p.timestamp = ts; return this; }
                public NotificationPayload build() { return p; }
            }

            @Override
            public String toString() {
                return "NotificationPayload{" +
                        "tenantId='" + tenantId + '\'' +
                        ", billingCycleId='" + billingCycleId + '\'' +
                        ", invoiceId='" + invoiceId + '\'' +
                        ", amount=" + amount +
                        ", gatewayTransactionId='" + gatewayTransactionId + '\'' +
                        ", timestamp=" + timestamp +
                        '}';
            }
        }
    }

    /**
     * Repositorio simplificado para logs de notificaciones. Implementar con JPA/DB real.
     */
    public interface NotificationLogRepository {
        Optional<NotificationLog> findByNotificationKey(String notificationKey);
        void save(NotificationLog log);

        class NotificationLog {
            private String notificationKey;
            private String tenantId;
            private String billingCycleId;
            private String invoiceId;
            private String paymentResultId;
            private String payload;
            private String response;
            private boolean success;
            private Instant sentAt;

            // getters / setters
            public String getNotificationKey() { return notificationKey; }
            public void setNotificationKey(String notificationKey) { this.notificationKey = notificationKey; }
            public String getTenantId() { return tenantId; }
            public void setTenantId(String tenantId) { this.tenantId = tenantId; }
            public String getBillingCycleId() { return billingCycleId; }
            public void setBillingCycleId(String billingCycleId) { this.billingCycleId = billingCycleId; }
            public String getInvoiceId() { return invoiceId; }
            public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }
            public String getPaymentResultId() { return paymentResultId; }
            public void setPaymentResultId(String paymentResultId) { this.paymentResultId = paymentResultId; }
            public String getPayload() { return payload; }
            public void setPayload(String payload) { this.payload = payload; }
            public String getResponse() { return response; }
            public void setResponse(String response) { this.response = response; }
            public boolean isSuccess() { return success; }
            public void setSuccess(boolean success) { this.success = success; }
            public Instant getSentAt() { return sentAt; }
            public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
        }
    }

    /**
     * Repositorio para PaymentResult (simplificado).
     */
    public interface PaymentResultRepository {
        Optional<PaymentResult> findById(String paymentResultId);
    }

    /**
     * Evento de dominio que se publica hacia el bus (Kafka) para notificar a otros servicios.
     */
    public static class TenantOrchestratorNotificationEvent {
        private final String tenantId;
        private final String billingCycleId;
        private final String invoiceId;
        private final String paymentResultId;
        private final boolean success;
        private final String message;

        public TenantOrchestratorNotificationEvent(String tenantId, String billingCycleId, String invoiceId, String paymentResultId, boolean success, String message) {
            this.tenantId = tenantId;
            this.billingCycleId = billingCycleId;
            this.invoiceId = invoiceId;
            this.paymentResultId = paymentResultId;
            this.success = success;
            this.message = message;
        }

        // getters...
    }
}
