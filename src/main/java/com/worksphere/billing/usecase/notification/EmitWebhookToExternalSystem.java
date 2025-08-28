package com.worksphere.billing.usecase.notification;

import com.worksphere.billing.domain.event.NotificationEvent;
import com.worksphere.billing.domain.model.TenantWebhookConfig;
import com.worksphere.billing.infrastructure.repository.WebhookLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * Caso de uso: Emitir notificaciones vía Webhook a sistemas externos (CRM, ERP, Partners).
 */
@Service
public class EmitWebhookToExternalSystem {

    private static final Logger log = LoggerFactory.getLogger(EmitWebhookToExternalSystem.class);

    private final RestTemplate restTemplate;
    private final WebhookLogRepository webhookLogRepository;
    private final EmitNotificationEvent emitNotificationEvent;

    public EmitWebhookToExternalSystem(RestTemplate restTemplate,
                                       WebhookLogRepository webhookLogRepository,
                                       EmitNotificationEvent emitNotificationEvent) {
        this.restTemplate = restTemplate;
        this.webhookLogRepository = webhookLogRepository;
        this.emitNotificationEvent = emitNotificationEvent;
    }

    /**
     * Envía un webhook a todos los endpoints configurados para un tenant.
     */
    @Transactional
    public void emit(String tenantId, Object payload, List<TenantWebhookConfig> configs) {
        for (TenantWebhookConfig config : configs) {
            try {
                String jsonPayload = buildPayload(payload, config);
                HttpHeaders headers = buildHeaders(config, jsonPayload);

                HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);

                ResponseEntity<String> response = restTemplate.exchange(
                        config.getEndpointUrl(),
                        HttpMethod.POST,
                        request,
                        String.class
                );

                // Registrar éxito
                webhookLogRepository.saveWebhookLog(
                        tenantId,
                        config.getEndpointUrl(),
                        jsonPayload,
                        "SUCCESS",
                        Instant.now(),
                        response.getBody()
                );

                log.info("Webhook enviado exitosamente al endpoint [{}] para tenant [{}]", config.getEndpointUrl(), tenantId);

                // Emitir evento derivado
                emitNotificationEvent.emit(new NotificationEvent(
                        "webhook_sent",
                        tenantId,
                        null,
                        "Webhook enviado correctamente a " + config.getEndpointUrl()
                ));

            } catch (ResourceAccessException timeoutEx) {
                handleFailure(tenantId, config, payload, "TIMEOUT: " + timeoutEx.getMessage());
            } catch (Exception ex) {
                handleFailure(tenantId, config, payload, "ERROR: " + ex.getMessage());
            }
        }
    }

    /**
     * Construir payload en formato esperado por el sistema externo.
     */
    private String buildPayload(Object payload, TenantWebhookConfig config) {
        // Aquí podrías adaptar el formato según tipo de integración (ejemplo: JSON, XML, etc.)
        // En este ejemplo, asumimos JSON.
        return "{\"event\":\"" + payload.getClass().getSimpleName() + "\", \"data\": \"" + payload.toString() + "\"}";
    }

    /**
     * Construir headers con autenticación y firmas HMAC.
     */
    private HttpHeaders buildHeaders(TenantWebhookConfig config, String payload) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        if (config.getAuthToken() != null) {
            headers.setBearerAuth(config.getAuthToken());
        }

        if (config.getHmacSecret() != null) {
            String signature = generateHmacSignature(payload, config.getHmacSecret());
            headers.add("X-Signature", signature);
        }

        return headers;
    }

    /**
     * Generar firma HMAC-SHA256 para verificar integridad del payload.
     */
    private String generateHmacSignature(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Manejo de fallos: registrar error, emitir evento derivado y habilitar reintentos.
     */
    private void handleFailure(String tenantId, TenantWebhookConfig config, Object payload, String errorMessage) {
        log.error("Error enviando webhook al endpoint [{}] para tenant [{}]: {}",
                config.getEndpointUrl(), tenantId, errorMessage);

        webhookLogRepository.saveWebhookLog(
                tenantId,
                config.getEndpointUrl(),
                payload.toString(),
                "FAILED",
                Instant.now(),
                errorMessage
        );

        emitNotificationEvent.emit(new NotificationEvent(
                "webhook_failed",
                tenantId,
                null,
                "Error al enviar webhook a " + config.getEndpointUrl() + ": " + errorMessage
        ));

        // Aquí se podría integrar un sistema de reintentos: Quartz, Spring Retry o DLQ en Kafka/RabbitMQ.
    }
}
