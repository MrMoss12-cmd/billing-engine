package com.worksphere.billing.usecase.notification;

import com.worksphere.billing.domain.event.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Instant;

@Service
public class EmitNotificationEvent {

    private static final Logger log = LoggerFactory.getLogger(EmitNotificationEvent.class);

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public EmitNotificationEvent(KafkaTemplate<String, NotificationEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void emit(NotificationEvent event) {
        Assert.notNull(event, "El evento no puede ser nulo");
        Assert.hasText(event.getTenantId(), "El tenantId no puede estar vacío");
        Assert.hasText(event.getType(), "El tipo de evento no puede estar vacío");

        try {
            // Publicar en Kafka con partición por tenant
            kafkaTemplate.send("billing.notifications", event.getTenantId(), event).get();

            log.info("✅ Evento emitido [{}] para tenant [{}], factura [{}] en [{}]",
                    event.getType(), event.getTenantId(), event.getInvoiceId(), Instant.now());

        } catch (Exception ex) {
            log.error("❌ Error al emitir evento [{}] para tenant [{}], factura [{}]: {}",
                    event.getType(), event.getTenantId(), event.getInvoiceId(), ex.getMessage(), ex);

            // TODO: aquí podrías enviar a Dead Letter Queue (DLQ) o reintentar
        }
    }
}
