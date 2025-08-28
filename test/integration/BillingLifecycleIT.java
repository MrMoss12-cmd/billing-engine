package com.worksphere.billingengine.test.integration;

import com.worksphere.billingengine.service.BillingEngine;
import com.worksphere.billingengine.service.EmailService;
import com.worksphere.billingengine.service.InvoiceSigner;
import com.worksphere.billingengine.events.BillingCompletedEvent;
import com.worksphere.billingengine.model.Invoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("integration")
@EmbeddedKafka(partitions = 1, topics = { "billing-events" })
class BillingLifecycleIT {

    @Autowired
    private BillingEngine billingEngine;

    @Autowired
    private InvoiceSigner invoiceSigner;

    @Autowired
    private EmailService emailService;

    private Invoice testInvoice;

    @BeforeEach
    void setUp() {
        // Configurar un invoice de prueba simulando un tenant real
        testInvoice = new Invoice();
        testInvoice.setInvoiceId("INV-IT-001");
        testInvoice.setTenantId("tenant-it");
        testInvoice.setAmount(new BigDecimal("1500.00"));
        testInvoice.setBillingCycleId("2025-08");
    }

    @Test
    void testFullBillingLifecycleSuccess() {
        // 1. Ejecutar facturación completa
        billingEngine.processBillingCycle(testInvoice.getTenantId(), testInvoice.getBillingCycleId());

        // 2. Verificar que la factura se generó y fue firmada
        assertTrue(invoiceSigner.isInvoiceSigned(testInvoice.getInvoiceId()), 
            "La factura debe estar firmada digitalmente");

        // 3. Verificar que el correo fue enviado
        assertTrue(emailService.isInvoiceSent(testInvoice.getInvoiceId()), 
            "El correo con la factura debe haberse enviado");

        // 4. Verificar evento de finalización
        BillingCompletedEvent event = billingEngine.getLastBillingCompletedEvent(testInvoice.getTenantId(), testInvoice.getBillingCycleId());
        assertNotNull(event, "Debe generarse un evento BillingCompleted");
        assertEquals(testInvoice.getTenantId(), event.getTenantId());
        assertEquals(testInvoice.getBillingCycleId(), event.getBillingCycleId());
    }

    @Test
    void testBillingLifecycleWithFailedPayment() {
        // Simular pago fallido
        billingEngine.simulatePaymentFailure(testInvoice.getTenantId(), testInvoice.getBillingCycleId());

        // Ejecutar facturación
        billingEngine.processBillingCycle(testInvoice.getTenantId(), testInvoice.getBillingCycleId());

        // Verificar que el sistema registró el fallo y reintentos
        assertTrue(billingEngine.hasPaymentFailed(testInvoice.getInvoiceId()), 
            "El pago fallido debe ser registrado");
        assertEquals(1, billingEngine.getRetryCount(testInvoice.getInvoiceId()), 
            "Debe haberse ejecutado un intento de reintento");

        // Verificar que no se generó correo de éxito
        assertFalse(emailService.isInvoiceSent(testInvoice.getInvoiceId()), 
            "El correo no debe enviarse si el pago falla");
    }

    @Test
    void testReproducibilityAcrossEnvironments() {
        // Ejecutar facturación nuevamente con los mismos datos
        billingEngine.processBillingCycle(testInvoice.getTenantId(), testInvoice.getBillingCycleId());

        // Verificar que no se duplicaron facturas ni pagos
        assertEquals(1, billingEngine.getInvoiceCount(testInvoice.getInvoiceId()), 
            "No se deben generar duplicados de la misma factura");

        BillingCompletedEvent event = billingEngine.getLastBillingCompletedEvent(testInvoice.getTenantId(), testInvoice.getBillingCycleId());
        assertNotNull(event, "El evento BillingCompleted debe seguir presente");
    }
}
