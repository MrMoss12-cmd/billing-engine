package com.worksphere.billing.service;

import com.worksphere.billing.model.*;
import com.worksphere.billing.usecase.calculation.CalculateBillingForTenant;
import com.worksphere.billing.usecase.calculation.GenerateInvoice;
import com.worksphere.billing.usecase.payment.InitiatePaymentTransaction;
import com.worksphere.billing.usecase.notification.EmitBillingEventToKafka;
import com.worksphere.billing.usecase.notification.SendInvoiceEmailToTenant;
import com.worksphere.billing.usecase.idempotency.CheckBillingCycleExecuted;
import com.worksphere.billing.usecase.idempotency.MarkBillingCycleAsComplete;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Servicio central que coordina todo el proceso de facturación para un tenant,
 * garantizando idempotencia, auditabilidad y seguridad.
 */
@Service
public class BillingEngine {

    private static final Logger log = LoggerFactory.getLogger(BillingEngine.class);

    private final CheckBillingCycleExecuted checkBillingCycleExecuted;
    private final CalculateBillingForTenant calculateBillingForTenant;
    private final GenerateInvoice generateInvoice;
    private final InitiatePaymentTransaction initiatePaymentTransaction;
    private final SendInvoiceEmailToTenant sendInvoiceEmailToTenant;
    private final EmitBillingEventToKafka emitBillingEventToKafka;
    private final MarkBillingCycleAsComplete markBillingCycleAsComplete;

    public BillingEngine(
            CheckBillingCycleExecuted checkBillingCycleExecuted,
            CalculateBillingForTenant calculateBillingForTenant,
            GenerateInvoice generateInvoice,
            InitiatePaymentTransaction initiatePaymentTransaction,
            SendInvoiceEmailToTenant sendInvoiceEmailToTenant,
            EmitBillingEventToKafka emitBillingEventToKafka,
            MarkBillingCycleAsComplete markBillingCycleAsComplete
    ) {
        this.checkBillingCycleExecuted = checkBillingCycleExecuted;
        this.calculateBillingForTenant = calculateBillingForTenant;
        this.generateInvoice = generateInvoice;
        this.initiatePaymentTransaction = initiatePaymentTransaction;
        this.sendInvoiceEmailToTenant = sendInvoiceEmailToTenant;
        this.emitBillingEventToKafka = emitBillingEventToKafka;
        this.markBillingCycleAsComplete = markBillingCycleAsComplete;
    }

    /**
     * Ejecuta el ciclo de facturación completo para un tenant y ciclo dado.
     *
     * @param tenantId    identificador del tenant
     * @param billingCycle ciclo de facturación
     */
    @Transactional
    public void runBillingCycle(String tenantId, BillingCycle billingCycle) {
        log.info("Iniciando ciclo de facturación para tenant {} y ciclo {}", tenantId, billingCycle.getBillingCycleId());

        // 1. Verificar idempotencia
        if (checkBillingCycleExecuted.isAlreadyExecuted(tenantId, billingCycle.getBillingCycleId())) {
            log.info("Ciclo {} ya ejecutado para tenant {}, abortando ejecución.", billingCycle.getBillingCycleId(), tenantId);
            return;
        }

        try {
            // 2. Calcular facturación
            BillingRequest billingRequest = new BillingRequest();
            billingRequest.setTenantId(tenantId);
            billingRequest.setBillingCycleId(billingCycle.getBillingCycleId());

            BillingCalculationResult calculationResult = calculateBillingForTenant.calculate(billingRequest, billingCycle);
            log.info("Cálculo realizado: monto total {}", calculationResult.getTotalAmount());

            // 3. Generar factura
            Invoice invoice = generateInvoice.generate(billingRequest, billingCycle, calculationResult);
            log.info("Factura generada: invoice_id {}", invoice.getInvoiceId());

            // 4. Emitir evento Kafka
            emitBillingEventToKafka.emit(invoice, "invoice_generated");

            // 5. Iniciar transacción de pago
            PaymentToken paymentToken = billingRequest.getPaymentToken();
            PaymentResult paymentResult = initiatePaymentTransaction.initiate(paymentToken, invoice.getTotalAmount(), tenantId, invoice.getInvoiceId());
            log.info("Resultado de pago: estado {}", paymentResult.getStatus());

            // 6. Enviar factura por correo
            sendInvoiceEmailToTenant.send(invoice);

            // 7. Marcar ciclo como completado
            markBillingCycleAsComplete.mark(tenantId, billingCycle.getBillingCycleId(), invoice, paymentResult);

            log.info("Ciclo de facturación completado exitosamente para tenant {}", tenantId);

        } catch (Exception e) {
            log.error("Error en ciclo de facturación para tenant {}: {}", tenantId, e.getMessage(), e);
            // Aquí se pueden implementar reintentos, reversos de pago, alertas
        }
    }
}
