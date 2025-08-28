package com.worksphere.billing.usecase.payment;

import com.worksphere.billing.domain.model.PaymentResult;
import com.worksphere.billing.domain.exception.PaymentException;
import com.worksphere.billing.domain.model.Invoice;
import com.worksphere.billing.service.PaymentResultRepository;
import com.worksphere.billing.transport.events.PaymentFailedEvent;
import com.worksphere.billing.transport.events.PaymentSuccessEvent;
import com.worksphere.billing.transport.events.EventPublisher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caso de uso que registra el resultado de una transacción de pago.
 */
@Component
public class StorePaymentResult {

    private static final Logger logger = LoggerFactory.getLogger(StorePaymentResult.class);

    private final PaymentResultRepository paymentResultRepository;
    private final EventPublisher eventPublisher;

    public StorePaymentResult(PaymentResultRepository paymentResultRepository,
                              EventPublisher eventPublisher) {
        this.paymentResultRepository = paymentResultRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Almacena de manera confiable el resultado de un pago.
     *
     * @param paymentResult Resultado de la transacción a almacenar
     * @param invoice Factura asociada al pago
     * @throws PaymentException si ocurre un error de persistencia
     */
    @Transactional
    public void execute(PaymentResult paymentResult, Invoice invoice) throws PaymentException {

        // -----------------------------
        // 1. Idempotencia: evitar duplicados
        // -----------------------------
        if (paymentResultRepository.existsByTransactionId(paymentResult.getTransactionId())) {
            logger.info("Resultado de pago ya registrado para transactionId {}", paymentResult.getTransactionId());
            return;
        }

        // -----------------------------
        // 2. Asociación con factura y ciclo
        // -----------------------------
        paymentResult.setInvoiceId(invoice.getInvoiceId());
        paymentResult.setBillingCycleId(invoice.getBillingCycleId());

        // -----------------------------
        // 3. Persistencia atómica
        // -----------------------------
        try {
            paymentResultRepository.save(paymentResult);
            logger.info("Pago registrado con transactionId {}", paymentResult.getTransactionId());
        } catch (Exception e) {
            logger.error("Error al almacenar el resultado de pago", e);
            throw new PaymentException("No se pudo almacenar el resultado del pago");
        }

        // -----------------------------
        // 4. Emisión de eventos según resultado
        // -----------------------------
        if (paymentResult.isSuccess()) {
            eventPublisher.publish(new PaymentSuccessEvent(paymentResult));
        } else {
            eventPublisher.publish(new PaymentFailedEvent(paymentResult));
        }

        // -----------------------------
        // 5. Trazabilidad y auditabilidad
        // -----------------------------
        logger.info("PaymentResult almacenado: transactionId={}, invoiceId={}, status={}",
                paymentResult.getTransactionId(),
                paymentResult.getInvoiceId(),
                paymentResult.getStatus());
    }
}
