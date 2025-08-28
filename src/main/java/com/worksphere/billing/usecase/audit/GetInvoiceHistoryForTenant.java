package com.worksphere.billing.usecase.audit;

import com.worksphere.billing.domain.model.Invoice;
import com.worksphere.billing.infrastructure.repository.InvoiceRepository;
import com.worksphere.billing.usecase.audit.dto.InvoiceHistoryFilter;
import com.worksphere.billing.usecase.audit.dto.InvoiceHistoryResponse;
import com.worksphere.billing.usecase.audit.LogBillingOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Caso de uso: Consultar historial de facturas emitidas para un tenant específico.
 */
@Service
public class GetInvoiceHistoryForTenant {

    private static final Logger log = LoggerFactory.getLogger(GetInvoiceHistoryForTenant.class);

    private final InvoiceRepository invoiceRepository;
    private final LogBillingOperation logBillingOperation;

    public GetInvoiceHistoryForTenant(InvoiceRepository invoiceRepository,
                                      LogBillingOperation logBillingOperation) {
        this.invoiceRepository = invoiceRepository;
        this.logBillingOperation = logBillingOperation;
    }

    /**
     * Obtiene historial de facturas emitidas para un tenant, con filtros opcionales.
     *
     * @param tenantId Tenant al que pertenecen las facturas.
     * @param filter   Filtros opcionales (fecha inicio, fecha fin, estado factura).
     * @param actor    Usuario o sistema que solicita la información.
     * @return Lista de facturas verificadas para el tenant.
     */
    @Transactional(readOnly = true)
    public InvoiceHistoryResponse execute(String tenantId, InvoiceHistoryFilter filter, String actor) {
        log.debug("Consultando historial de facturas tenantId={}, filtros={}", tenantId, filter);

        List<Invoice> invoices;

        if (filter.getStartDate() != null && filter.getEndDate() != null && filter.getStatus() != null) {
            invoices = invoiceRepository.findByTenantIdAndDateRangeAndStatus(
                    tenantId, filter.getStartDate(), filter.getEndDate(), filter.getStatus());
        } else if (filter.getStartDate() != null && filter.getEndDate() != null) {
            invoices = invoiceRepository.findByTenantIdAndDateRange(
                    tenantId, filter.getStartDate(), filter.getEndDate());
        } else if (filter.getStatus() != null) {
            invoices = invoiceRepository.findByTenantIdAndStatus(tenantId, filter.getStatus());
        } else {
            invoices = invoiceRepository.findByTenantId(tenantId);
        }

        // Registrar auditoría de la consulta
        logBillingOperation.logOperation(
                tenantId,
                "N/A", // no es un ciclo de facturación específico
                null,
                "INVOICE_HISTORY_QUERY",
                actor,
                "Consulta de historial de facturas realizada con filtros=" + filter.toString()
        );

        return new InvoiceHistoryResponse(tenantId, invoices, Instant.now());
    }
}
