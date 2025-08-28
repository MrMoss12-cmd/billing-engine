package com.worksphere.billing.usecase.audit;

import com.worksphere.billing.domain.model.BillingOperationLog;
import com.worksphere.billing.infrastructure.repository.BillingOperationLogRepository;
import com.worksphere.billing.usecase.audit.dto.BillingOperationLogFilter;
import com.worksphere.billing.usecase.audit.dto.BillingOperationLogPage;
import com.worksphere.billing.usecase.audit.export.OperationLogCsvExporter;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.springframework.data.jpa.domain.Specification.where;

/**
 * Caso de uso: recuperar registros de operaciones financieras/ de facturación
 * con filtros, seguridad estricta, y soporte de exportación.
 */
@Service
public class GetBillingOperationLog {

    private final BillingOperationLogRepository repository;
    private final LogBillingOperation logBillingOperation;

    public GetBillingOperationLog(BillingOperationLogRepository repository,
                                  LogBillingOperation logBillingOperation) {
        this.repository = repository;
        this.logBillingOperation = logBillingOperation;
    }

    /**
     * Consulta paginada de logs con filtros dinámicos.
     * Solo roles autorizados pueden acceder.
     */
    @Transactional(readOnly = true)
    @RolesAllowed({"ROLE_ADMIN", "ROLE_AUDITOR", "ROLE_COMPLIANCE"})
    public BillingOperationLogPage query(BillingOperationLogFilter filter, String actor) {
        Pageable pageable = PageRequest.of(
                filter.getPage(),
                filter.getSize(),
                Sort.by(filter.isAsc() ? Sort.Direction.ASC : Sort.Direction.DESC,
                        filter.getSortBy() == null ? "timestamp" : filter.getSortBy())
        );

        Specification<BillingOperationLog> spec = where(tenantEquals(filter.getTenantId()))
                .and(billingCycleEquals(filter.getBillingCycleId()))
                .and(operationTypeEquals(filter.getOperationType()))
                .and(betweenDates(filter.getStartDate(), filter.getEndDate()));

        Page<BillingOperationLog> page = repository.findAll(spec, pageable);

        // Auditoría de la consulta (quién consultó qué y cuándo)
        logBillingOperation.logOperation(
                filter.getTenantId() != null ? filter.getTenantId() : "MULTI",
                filter.getBillingCycleId() != null ? filter.getBillingCycleId() : "RANGE",
                null,
                "BILLING_OPERATION_LOG_QUERY",
                actor,
                "Filtros=" + filter
        );

        return new BillingOperationLogPage(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), Instant.now());
    }

    /**
     * Exporta el resultado filtrado en CSV (no paginado).
     * Útil para auditorías legales/técnicas.
     */
    @Transactional(readOnly = true)
    @RolesAllowed({"ROLE_ADMIN", "ROLE_AUDITOR", "ROLE_COMPLIANCE"})
    public byte[] exportCsv(BillingOperationLogFilter filter, String actor) {
        Specification<BillingOperationLog> spec = where(tenantEquals(filter.getTenantId()))
                .and(billingCycleEquals(filter.getBillingCycleId()))
                .and(operationTypeEquals(filter.getOperationType()))
                .and(betweenDates(filter.getStartDate(), filter.getEndDate()));

        // Exportación ordenada por timestamp asc para facilitar reconstrucción de eventos
        Sort sort = Sort.by(Sort.Direction.ASC, "timestamp");
        var all = repository.findAll(spec, sort);

        // Auditoría de exportación
        logBillingOperation.logOperation(
                filter.getTenantId() != null ? filter.getTenantId() : "MULTI",
                filter.getBillingCycleId() != null ? filter.getBillingCycleId() : "RANGE",
                null,
                "BILLING_OPERATION_LOG_EXPORT",
                actor,
                "Exportación CSV. Filtros=" + filter + ", registros=" + all.size()
        );

        return OperationLogCsvExporter.toCsv(all);
    }

    // -------- Especificaciones (filtros dinámicos) --------

    private Specification<BillingOperationLog> tenantEquals(String tenantId) {
        return (root, cq, cb) ->
                tenantId == null ? cb.conjunction() : cb.equal(root.get("tenantId"), tenantId);
    }

    private Specification<BillingOperationLog> billingCycleEquals(String billingCycleId) {
        return (root, cq, cb) ->
                billingCycleId == null ? cb.conjunction() : cb.equal(root.get("billingCycleId"), billingCycleId);
    }

    private Specification<BillingOperationLog> operationTypeEquals(String operationType) {
        return (root, cq, cb) ->
                operationType == null ? cb.conjunction() : cb.equal(root.get("operationType"), operationType);
    }

    private Specification<BillingOperationLog> betweenDates(Instant start, Instant end) {
        return (root, cq, cb) -> {
            if (start == null && end == null) return cb.conjunction();
            if (start != null && end != null) return cb.between(root.get("timestamp"), start, end);
            if (start != null) return cb.greaterThanOrEqualTo(root.get("timestamp"), start);
            return cb.lessThanOrEqualTo(root.get("timestamp"), end);
        };
    }
}
