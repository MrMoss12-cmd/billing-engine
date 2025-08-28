package com.worksphere.billing.utils;

import com.worksphere.billing.domain.model.Invoice;
import com.worksphere.billing.service.InvoiceSigner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;

/**
 * Utilidad para generar y manipular PDFs, especialmente facturas.
 */
@Component
public class PdfUtils {

    private static final Logger logger = Logger.getLogger(PdfUtils.class.getName());

    private final InvoiceSigner invoiceSigner;

    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    @Autowired
    public PdfUtils(InvoiceSigner invoiceSigner) {
        this.invoiceSigner = invoiceSigner;
    }

    /**
     * Genera un PDF de la factura y lo devuelve como arreglo de bytes.
     *
     * @param invoice Objeto Invoice con todos los datos
     * @param pdfA    Si true, generar PDF/A
     * @return Byte array del PDF generado
     */
    public byte[] generateInvoicePdf(Invoice invoice, boolean pdfA) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdfDoc = new PdfDocument(writer);
            Document document = new Document(pdfDoc);

            // Cabecera
            document.add(new Paragraph("Factura Electrónica")
                    .setBold()
                    .setFontSize(18));
            document.add(new Paragraph("Tenant: " + invoice.getTenantId()));
            document.add(new Paragraph("Invoice ID: " + invoice.getInvoiceId()));
            document.add(new Paragraph("Fecha emisión: " + Instant.now()));

            // Tabla de items
            Table table = new Table(new float[]{4, 2, 2});
            table.addHeaderCell("Descripción");
            table.addHeaderCell("Cantidad");
            table.addHeaderCell("Subtotal");

            invoice.getItems().forEach(item -> {
                table.addCell(item.getDescription());
                table.addCell(String.valueOf(item.getQuantity()));
                table.addCell(item.getSubtotal().toPlainString());
            });

            document.add(table);

            // Totales
            BigDecimal total = invoice.getItems().stream()
                    .map(item -> item.getSubtotal())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            document.add(new Paragraph("Impuestos: " + invoice.getTaxAmount().toPlainString()));
            document.add(new Paragraph("Total: " + total.toPlainString()));

            document.close();

            // Auditabilidad
            logger.info("PDF generado para tenant " + invoice.getTenantId() +
                    ", invoice_id " + invoice.getInvoiceId());

            return baos.toByteArray();

        } catch (Exception e) {
            logger.severe("Error generando PDF: " + e.getMessage());
            throw new RuntimeException("Error generando PDF", e);
        }
    }

    /**
     * Genera y firma el PDF de la factura de manera asíncrona para no bloquear hilos críticos.
     */
    public void generateAndSignInvoicePdfAsync(Invoice invoice, boolean pdfA) {
        executorService.submit(() -> {
            try {
                byte[] pdfBytes = generateInvoicePdf(invoice, pdfA);
                byte[] signedPdf = invoiceSigner.signInvoicePdf(invoice, pdfBytes);

                // Guardar en disco o enviar a storage
                try (OutputStream os = new FileOutputStream("invoice_" + invoice.getInvoiceId() + ".pdf")) {
                    os.write(signedPdf);
                }

                logger.info("PDF firmado y almacenado para invoice " + invoice.getInvoiceId());

            } catch (Exception e) {
                logger.severe("Error generando y firmando PDF de invoice " + invoice.getInvoiceId() +
                        ": " + e.getMessage());
            }
        });
    }
}
