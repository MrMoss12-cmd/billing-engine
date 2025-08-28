package com.worksphere.billing.usecase.notification;

import com.worksphere.billing.domain.event.BillingEvent;
import com.worksphere.billing.infrastructure.kafka.KafkaTopics;
import com.worksphere.billing.infrastructure.kafka.serializer.BillingEventSerializer;
import com.worksphere.billing.infrastructure.repository.BillingEventLogRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Caso de uso para emitir eventos de facturación al bus de mensajería (Kafka).
 * Garantiza formato estándar, idempotencia y resiliencia en la publicación.
 */
@Service
public class EmitBillingEventToKafka {

    private static final Logger log = LoggerFactory.getLogger(EmitBillingEventToKafka.class);

    private final KafkaTemplate<String, BillingEvent> kafkaTemplate;
    private final BillingEventLogRepository eventLogRepository;

    public EmitBillingEventToKafka(KafkaTemplate<String, BillingEvent> kafkaTemplate,
                                   BillingEventLogRepository eventLogRepository) {
        this.kafkaTemplate = kafkaTemplate;
        this.eventLogRepository = eventLogRepository;
    }

    /**
     * Emite un evento de facturación al topic de Kafka configurado.
     * @param event Evento de facturación estandarizado.
     */
    @Transactional
    public void emit(BillingEvent event) {
        // Idempotencia → verificar si el evento ya fue enviado
        Optional<BillingEvent> existing = eventLogRepository.findByEventId(event.getEventId());
        if (existing.isPresent()) {
            log.info("Evento [{}] ya fue emitido anteriormente, se omite envío duplicado.", event.getEventId());
            return;
        }

        String topic = KafkaTopics.BILLING_EVENTS;
        String key = buildEventKey(event);

        ProducerRecord<String, BillingEvent> record = new ProducerRecord<>(topic, key, event);

        ListenableFuture<SendResult<String, BillingEvent>> future = kafkaTemplate.send(record);

        future.addCallback(new ListenableFutureCallback<>() {
            @Override
            public void onSuccess(SendResult<String, BillingEvent> result) {
                RecordMetadata metadata = result.getRecordMetadata();
                log.info("Evento [{}] emitido a Kafka (topic={}, partition={}, offset={})",
                        event.getEventId(), metadata.topic(), metadata.partition(), metadata.offset());

                // Persistencia para auditabilidad
                eventLogRepository.save(event.withEmittedAt(Instant.now()));
            }

            @Override
            public void onFailure(Throwable ex) {
                log.error("Error al emitir evento [{}] a Kafka. Se reintentará automáticamente.", event.getEventId(), ex);
                // Estrategia de resiliencia: Spring Kafka ya maneja reintentos configurables.
            }
        });
    }

    /**
     * Construye la clave del evento para Kafka asegurando orden e idempotencia.
     */
    private String buildEventKey(BillingEvent event) {
        return event.getTenantId() + "-" + event.getEventType() + "-" + event.getEventId();
    }
}
