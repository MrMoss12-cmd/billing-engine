package com.worksphere.billingengine.test.unit;

import com.worksphere.billingengine.service.InvoiceSigner;
import com.worksphere.billingengine.model.Invoice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InvoiceSignerTest {

    @InjectMocks
    private InvoiceSigner invoiceSigner;

    @Mock
    private AuditService auditService; // Supongamos que AuditService registra las firmas

    private Invoice testInvoice;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testInvoice = new Invoice();
        testInvoice.setInvoiceId("INV-2025-08-001");
        testInvoice.setTenantId("tenant-test");
        testInvoice.setAmount(new BigDecimal("1234.56"));
        testInvoice.setIssueDate(LocalDateTime.of(2025,8,1,10,0));
        testInvoice.setPdfFile(new File("test-invoice.pdf")); // archivo de prueba
    }

    @Test
    void testSignPdfSuccessfully() throws IOException {
        // Mock comportamiento de auditoría
        doNothing().when(auditService).logSignature(anyString(), anyString());

        boolean signed = invoiceSigner.signInvoicePdf(testInvoice);

        assertTrue(signed, "El PDF debe firmarse correctamente");
        verify(auditService, times(1)).logSignature(testInvoice.getInvoiceId(), "PDF");
        assertTrue(testInvoice.isSigned());
    }

    @Test
    void testSignXmlSuccessfully() throws IOException {
        testInvoice.setXmlFile(new File("test-invoice.xml"));
        doNothing().when(auditService).logSignature(anyString(), anyString());

        boolean signed = invoiceSigner.signInvoiceXml(testInvoice);

        assertTrue(signed, "El XML debe firmarse correctamente");
        verify(auditService, times(1)).logSignature(testInvoice.getInvoiceId(), "XML");
        assertTrue(testInvoice.isSignedXml());
    }

    @Test
    void testSignPdfWithMissingCertificate() throws IOException {
        // Simular fallo en certificado
        invoiceSigner.setCertificate(null);

        IOException exception = assertThrows(IOException.class, () -> invoiceSigner.signInvoicePdf(testInvoice));
        assertTrue(exception.getMessage().contains("Certificado no encontrado"));
        assertFalse(testInvoice.isSigned());
        verify(auditService, never()).logSignature(anyString(), anyString());
    }

    @Test
    void testAuditMetadataRecorded() throws IOException {
        doNothing().when(auditService).logSignature(anyString(), anyString());

        invoiceSigner.signInvoicePdf(testInvoice);

        // Verificar que el registro de auditoría se hizo correctamente
        verify(auditService).logSignature(eq(testInvoice.getInvoiceId()), eq("PDF"));
    }
}
