package com.worksphere.billing.usecase.payment;

import com.worksphere.billing.domain.model.PaymentResult;
import com.worksphere.billing.domain.exception.PaymentException;
import com.worksphere.billing.service.PaymentGatewayAdapter;
import com.worksphere.billing.service.PaymentResultRepository;
import com.worksphere.billing.transport.events.PaymentReversedEvent;
import com.worksphere.billing.transport.events.EventPublisher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Caso de uso para revertir una transacción de pago.
 */
@Component
public class ReverseTransaction {

    private static final Logger logger = LoggerFactory.getLogger(ReverseTransaction.class);

    private final PaymentResultRepository paymentResultRepository;
    private final PaymentGatewayAdapter paymentGatewayAdapter;
    private final EventPublisher eventPublisher;

    public ReverseTransaction(PaymentResultRepository paymentResultRepository,
                              PaymentGatewayAdapter paymentGatewayAdapter,
                              EventPublisher eventPublisher) {
        this.paymentResultRepository = paymentResultRepository;
        this.paymentGatewayAdapter = paymentGatewayAdapter;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Revertir un pago previamente exitoso.
     *
     * @param transactionId ID de la transacción a revertir
     * @param reason Motivo de la reversión
     * @param actor Usuario o sistema que solicita la reversión
     * @throws PaymentException si la reversión falla
     */
    @Transactional
    public void execute(String transactionId, String reason, String actor) throws PaymentException {

        // -----------------------------
        // 1. Recuperar transacción
        // -----------------------------
        PaymentResult paymentResult = paymentResultRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new PaymentException("Transacción no encontrada: " + transactionId));

        // -----------------------------
        // 2. Condición: solo si fue exitosa
        // -----------------------------
        if (!paymentResult.isSuccess()) {
            logger.warn("No se puede revertir transacción no exitosa: {}", transactionId);
            throw new PaymentException("Solo se pueden revertir transacciones exitosas");
        }

        // -----------------------------
        // 3. Idempotencia: evitar múltiples reversos
        // -----------------------------
        if (paymentResult.isReversed()) {
            logger.info("Transacción ya revertida: {}", transactionId);
            return;
        }

        // -----------------------------
        // 4. Invocar API de reversión de la pasarela
        // -----------------------------
        try {
            paymentGatewayAdapter.reversePayment(paymentResult);
        } catch (Exception e) {
            logger.error("Error al revertir transacción {}", transactionId, e);
            throw new PaymentException("Fallo al revertir la transacción");
        }

        // -----------------------------
        // 5. Registrar reversión
        // -----------------------------
        paymentResult.setReversed(true);
        paymentResult.setReversalReason(reason);
        paymentResult.setReversalActor(actor);
        paymentResult.setReversalTimestamp(Instant.now());
        paymentResultRepository.save(paymentResult);

        // -----------------------------
        // 6. Emitir evento
        // -----------------------------
        eventPublisher.publish(new PaymentReversedEvent(paymentResult));

        // -----------------------------
        // 7. Auditabilidad
        // -----------------------------
        logger.info("Transacción revertida: transactionId={}, actor={}, reason={}",
                transactionId, actor, reason);
    }
}
