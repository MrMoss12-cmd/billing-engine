package com.worksphere.billing.transport.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class WebhookDispatcher {

    private final RestTemplate restTemplate;

    public WebhookDispatcher() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Envía un evento JSON a la URL del webhook.
     * Se reintenta hasta 3 veces en caso de fallo.
     */
    @Retryable(
        value = { Exception.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000)
    )
    public void dispatch(String webhookUrl, Object eventPayload) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Object> entity = new HttpEntity<>(eventPayload, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                webhookUrl,
                HttpMethod.POST,
                entity,
                String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Webhook enviado correctamente a {} con payload: {}", webhookUrl, eventPayload);
            } else {
                log.warn("Respuesta inesperada del webhook {}: {}", webhookUrl, response.getStatusCode());
                throw new RuntimeException("Error enviando webhook");
            }
        } catch (Exception e) {
            log.error("Error enviando webhook a {}: {}", webhookUrl, e.getMessage());
            throw e;  // Para activar reintento
        }
    }
}
