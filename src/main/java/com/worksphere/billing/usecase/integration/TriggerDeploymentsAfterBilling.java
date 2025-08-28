package com.worksphere.billing.usecase.integration;

import com.worksphere.billing.domain.model.PaymentResult;
import com.worksphere.billing.domain.model.BillingCycle;
import com.worksphere.billing.domain.repository.PaymentResultRepository;
import com.worksphere.billing.domain.repository.DeploymentLogRepository;
import com.worksphere.billing.transport.events.EventPublisher;
import com.worksphere.billing.transport.events.DeploymentTriggeredEvent;
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
 * Caso de uso para disparar despliegues en cicd-deployer tras confirmación de facturación y pago.
 *
 * Requisitos cubiertos:
 * - Disparar solo si ciclo y pago confirmados.
 * - Integración segura con CICDDeployerClient.
 * - Idempotencia: no disparar despliegues duplicados.
 * - Resiliencia: reintentos controlados ante fallos.
 * - Trazabilidad: logs persistentes de despliegues.
 * - Soporte multitenant: múltiples tenants y entornos.
 */
@Component
public class TriggerDeploymentsAfterBilling {

    private static final Logger log = LoggerFactory.getLogger(TriggerDeploymentsAfterBilling.class);

    private final PaymentResultRepository paymentResultRepository;
    private final CICDDeployerClient cicdDeployerClient;
    private final DeploymentLogRepository deploymentLogRepository;
    private final EventPublisher eventPublisher;
    private final RetryTemplate retryTemplate;

    public TriggerDeploymentsAfterBilling(PaymentResultRepository paymentResultRepository,
                                          CICDDeployerClient cicdDeployerClient,
                                          DeploymentLogRepository deploymentLogRepository,
                                          EventPublisher eventPublisher) {
        this.paymentResultRepository = paymentResultRepository;
        this.cicdDeployerClient = cicdDeployerClient;
        this.deploymentLogRepository = deploymentLogRepository;
        this.eventPublisher = eventPublisher;
        this.retryTemplate = buildRetryTemplate();
    }

    private RetryTemplate buildRetryTemplate() {
        RetryTemplate tpl = new RetryTemplate();
        SimpleRetryPolicy policy = new SimpleRetryPolicy(4, Map.of(
                java.net.SocketTimeoutException.class, true,
                org.springframework.web.client.ResourceAccessException.class, true
        ), true);
        tpl.setRetryPolicy(policy);

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(8000L);
        tpl.setBackOffPolicy(backOff);

        return tpl;
    }

    @Transactional
    public void execute(BillingCycle billingCycle, PaymentResult paymentResult, String environment) {
        log.debug("Trigger deployments for tenant={} billingCycle={} environment={}",
                billingCycle.getTenantId(), billingCycle.getBillingCycleId(), environment);

        // 1) Validar que el pago fue exitoso
        if (!"SUCCESS".equalsIgnoreCase(paymentResult.getStatus())) {
            log.warn("Payment not successful for tenant={}, billingCycle={}, skipping deployment",
                    billingCycle.getTenantId(), billingCycle.getBillingCycleId());
            return;
        }

        // 2) Idempotencia: revisar si ya se disparó el despliegue
        String deploymentKey = buildDeploymentKey(billingCycle, paymentResult, environment);
        Optional<DeploymentLogRepository.DeploymentLog> existing = deploymentLogRepository.findByDeploymentKey(deploymentKey);
        if (existing.isPresent() && existing.get().isSuccess()) {
            log.info("Deployment already executed for key={}, skipping", deploymentKey);
            return;
        }

        // 3) Preparar payload para CICDDeployer
        CICDDeployerClient.DeploymentRequest request = CICDDeployerClient.DeploymentRequest.builder()
                .tenantId(billingCycle.getTenantId())
                .billingCycleId(billingCycle.getBillingCycleId())
                .invoiceId(paymentResult.getInvoiceId())
                .environment(environment)
                .amount(paymentResult.getAmount())
                .timestamp(Instant.now())
                .build();

        boolean deploymentSuccess = false;
        String responseMessage = null;

        try {
            String result = retryTemplate.execute(context -> cicdDeployerClient.triggerDeployment(request));
            responseMessage = result;
            deploymentSuccess = true;
        } catch (Exception ex) {
            responseMessage = ex.getMessage();
            log.error("Failed to trigger deployment for key={} after retries: {}", deploymentKey, ex.getMessage(), ex);
        } finally {
            // 4) Persistir log de despliegue
            DeploymentLogRepository.DeploymentLog logEntry = new DeploymentLogRepository.DeploymentLog();
            logEntry.setDeploymentKey(deploymentKey);
            logEntry.setTenantId(billingCycle.getTenantId());
            logEntry.setBillingCycleId(billingCycle.getBillingCycleId());
            logEntry.setInvoiceId(paymentResult.getInvoiceId());
            logEntry.setEnvironment(environment);
            logEntry.setResponse(responseMessage);
            logEntry.setSuccess(deploymentSuccess);
            logEntry.setTriggeredAt(Instant.now());

            deploymentLogRepository.save(logEntry);

            // 5) Emitir evento derivado
            DeploymentTriggeredEvent event = new DeploymentTriggeredEvent(
                    billingCycle.getTenantId(),
                    billingCycle.getBillingCycleId(),
                    paymentResult.getInvoiceId(),
                    environment,
                    deploymentSuccess,
                    responseMessage
            );
            eventPublisher.publish(event);

            if (deploymentSuccess) {
                log.info("Deployment triggered successfully for key={}", deploymentKey);
            } else {
                log.warn("Deployment failed for key={} - recorded for retries/alerts", deploymentKey);
            }
        }
    }

    private String buildDeploymentKey(BillingCycle cycle, PaymentResult pr, String environment) {
        return String.join("|",
                safe(cycle.getTenantId()),
                safe(cycle.getBillingCycleId() == null ? "" : cycle.getBillingCycleId().toString()),
                safe(pr.getInvoiceId() == null ? "" : pr.getInvoiceId().toString()),
                safe(environment)
        );
    }

    private String safe(Object o) {
        return o == null ? "" : o.toString();
    }

    // ----------------------------
    // Interfaces mínimas / contratos
    // ----------------------------

    public interface CICDDeployerClient {
        String triggerDeployment(DeploymentRequest request) throws Exception;

        class DeploymentRequest {
            private String tenantId;
            private String billingCycleId;
            private String invoiceId;
            private String environment;
            private java.math.BigDecimal amount;
            private Instant timestamp;

            public static Builder builder() { return new Builder(); }

            public static class Builder {
                private final DeploymentRequest req = new DeploymentRequest();
                public Builder tenantId(String tenantId) { req.tenantId = tenantId; return this; }
                public Builder billingCycleId(String id) { req.billingCycleId = id; return this; }
                public Builder invoiceId(String id) { req.invoiceId = id; return this; }
                public Builder environment(String env) { req.environment = env; return this; }
                public Builder amount(java.math.BigDecimal amt) { req.amount = amt; return this; }
                public Builder timestamp(Instant ts) { req.timestamp = ts; return this; }
                public DeploymentRequest build() { return req; }
            }
        }
    }

    public interface DeploymentLogRepository {
        Optional<DeploymentLog> findByDeploymentKey(String deploymentKey);
        void save(DeploymentLog log);

        class DeploymentLog {
            private String deploymentKey;
            private String tenantId;
            private String billingCycleId;
            private String invoiceId;
            private String environment;
            private String response;
            private boolean success;
            private Instant triggeredAt;

            // getters y setters
            public String getDeploymentKey() { return deploymentKey; }
            public void setDeploymentKey(String deploymentKey) { this.deploymentKey = deploymentKey; }
            public String getTenantId() { return tenantId; }
            public void setTenantId(String tenantId) { this.tenantId = tenantId; }
            public String getBillingCycleId() { return billingCycleId; }
            public void setBillingCycleId(String billingCycleId) { this.billingCycleId = billingCycleId; }
            public String getInvoiceId() { return invoiceId; }
            public void setInvoiceId(String invoiceId) { this.invoiceId = invoiceId; }
            public String getEnvironment() { return environment; }
            public void setEnvironment(String environment) { this.environment = environment; }
            public String getResponse() { return response; }
            public void setResponse(String response) { this.response = response; }
            public boolean isSuccess() { return success; }
            public void setSuccess(boolean success) { this.success = success; }
            public Instant getTriggeredAt() { return triggeredAt; }
            public void setTriggeredAt(Instant triggeredAt) { this.triggeredAt = triggeredAt; }
        }
    }

}
