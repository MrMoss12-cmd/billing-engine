package com.worksphere.billingengine.test.unit;

import com.worksphere.billingengine.service.BillingEngine;
import com.worksphere.billingengine.service.PaymentService;
import com.worksphere.billingengine.service.MetricsService;
import com.worksphere.billingengine.service.NotificationService;
import com.worksphere.billingengine.model.BillingCycle;
import com.worksphere.billingengine.model.Invoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class BillingEngineTest {

    @InjectMocks
    private BillingEngine billingEngine;

    @Mock
    private PaymentService paymentService;

    @Mock
    private MetricsService metricsService;

    @Mock
    private NotificationService notificationService;

    private BillingCycle testCycle;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testCycle = new BillingCycle();
        testCycle.setTenantId("tenant-test");
        testCycle.setCycleId("2025-08");
        testCycle.setStartDate(LocalDateTime.of(2025, 8, 1, 0, 0));
        testCycle.setEndDate(LocalDateTime.of(2025, 8, 31, 23, 59));
    }

    @Test
    void testBillingCalculationSuccess() {
        // Mock métricas
        when(metricsService.fetchUsage("tenant-test", "2025-08"))
                .thenReturn(BigDecimal.valueOf(1000));

        // Mock pago
        when(paymentService.initiatePayment(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(true);

        // Ejecutar BillingEngine
        Invoice invoice = billingEngine.processBillingCycle(testCycle);

        // Validaciones
        assertNotNull(invoice);
        assertEquals("tenant-test", invoice.getTenantId());
        assertEquals("2025-08", invoice.getBillingCycleId());
        assertEquals(BigDecimal.valueOf(1000), invoice.getAmount());
        verify(notificationService, times(1)).sendInvoiceEmail(invoice);
        verify(paymentService, times(1)).initiatePayment(anyString(), any(BigDecimal.class), anyString());
    }

    @Test
    void testBillingIdempotency() {
        // Simular que la factura ya fue generada
        when(metricsService.fetchUsage("tenant-test", "2025-08"))
                .thenReturn(BigDecimal.valueOf(1000));
        when(billingEngine.isBillingCycleCompleted(testCycle)).thenReturn(true);

        Invoice invoice = billingEngine.processBillingCycle(testCycle);

        // Validar que no genere duplicado
        assertNull(invoice, "Invoice should be null if billing cycle already completed");
        verify(paymentService, never()).initiatePayment(anyString(), any(BigDecimal.class), anyString());
        verify(notificationService, never()).sendInvoiceEmail(any());
    }

    @Test
    void testBillingWithPaymentFailure() {
        when(metricsService.fetchUsage("tenant-test", "2025-08"))
                .thenReturn(BigDecimal.valueOf(500));

        when(paymentService.initiatePayment(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(false);

        Invoice invoice = billingEngine.processBillingCycle(testCycle);

        assertNotNull(invoice);
        assertEquals(BigDecimal.valueOf(500), invoice.getAmount());
        assertFalse(invoice.isPaid());
        verify(notificationService, times(1)).sendPaymentFailedNotification(invoice);
    }

    @Test
    void testBillingCalculationPrecision() {
        when(metricsService.fetchUsage("tenant-test", "2025-08"))
                .thenReturn(new BigDecimal("1234.5678"));

        when(paymentService.initiatePayment(anyString(), any(BigDecimal.class), anyString()))
                .thenReturn(true);

        Invoice invoice = billingEngine.processBillingCycle(testCycle);

        assertEquals(new BigDecimal("1234.5678"), invoice.getAmount());
    }
}
