package com.worksphere.billing.usecase.payment;

import com.worksphere.billing.domain.model.PaymentToken;
import com.worksphere.billing.domain.model.PaymentResult;
import com.worksphere.billing.domain.model.Invoice;
import com.worksphere.billing.domain.repository.PaymentResultRepository;
import com.worksphere.billing.domain.repository.InvoiceRepository;
import com.worksphere.billing.service.PaymentGatewayAdapter;
import com.worksphere.billing.domain.exception.PaymentException;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Caso de uso para iniciar una transacción de pago hacia el proveedor o pasarela correspondiente.
 */
@Component
public class InitiatePaymentTransaction {

    private static final Logger logger = LoggerFactory.getLogger(InitiatePaymentTransaction.class);

    private final PaymentGatewayAdapter paymentGatewayAdapter;
    private final PaymentResultRepository paymentResultRepository;
    private final InvoiceRepository invoiceRepository;

    public InitiatePaymentTransaction(PaymentGatewayAdapter paymentGatewayAdapter,
                                      PaymentResultRepository paymentResultRepository,
                                      InvoiceRepository invoiceRepository) {
        this.paymentGatewayAdapter = paymentGatewayAdapter;
        this.paymentResultRepository = paymentResultRepository;
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * Inicia un pago seguro para la factura e invoice especificados.
     *
     * @param token  Token seguro de pago
     * @param amount Monto a cobrar
     * @param tenantId Identificador del tenant
     * @param invoiceId Identificador de la factura
     * @return PaymentResult con estado final
     */
    @Transactional
    public PaymentResult execute(PaymentToken token, 
                                 String tenantId, 
                                 String invoiceId, 
                                 java.math.BigDecimal amount) {

        // -----------------------------
        // 1. Validar idempotencia: no iniciar pago duplicado
        // -----------------------------
        PaymentResult existingPayment = paymentResultRepository.findByInvoiceId(invoiceId);
        if (existingPayment != null && "SUCCESS".equals(existingPayment.getStatus())) {
            logger.info("Pago ya realizado para invoice {} del tenant {}", invoiceId, tenantId);
            return existingPayment;
        }

        // -----------------------------
        // 2. Crear registro inicial de pago
        // -----------------------------
        PaymentResult paymentResult = new PaymentResult();
        paymentResult.setInvoiceId(invoiceId);
        paymentResult.setTenantId(tenantId);
        paymentResult.setAmount(amount);
        paymentResult.setStatus("PENDING");
        paymentResult.setCreatedAt(LocalDateTime.now());

        paymentResultRepository.save(paymentResult);

        try {
            // -----------------------------
            // 3. Llamada al PaymentGatewayAdapter
            // -----------------------------
            String transactionId = paymentGatewayAdapter.processPayment(token, amount, tenantId, invoiceId);

            // -----------------------------
            // 4. Actualizar resultado de pago
            // -----------------------------
            paymentResult.setTransactionId(transactionId);
            paymentResult.setStatus("SUCCESS");
            paymentResult.setCompletedAt(LocalDateTime.now());
            paymentResultRepository.save(paymentResult);

            logger.info("Pago exitoso para invoice {} del tenant {} con transactionId {}", invoiceId, tenantId, transactionId);

        } catch (PaymentException ex) {
            paymentResult.setStatus("FAILED");
            paymentResult.setCompletedAt(LocalDateTime.now());
            paymentResultRepository.save(paymentResult);

            logger.error("Fallo al procesar pago para invoice {} del tenant {}: {}", invoiceId, tenantId, ex.getMessage());
            throw ex;
        }

        return paymentResult;
    }
}
