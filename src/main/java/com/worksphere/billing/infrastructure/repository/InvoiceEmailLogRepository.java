package com.worksphere.billing.infrastructure.repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Repositorio para almacenar y consultar logs de envío de correos de facturación.
 * 
 * Soporta múltiples implementaciones (JPA, MongoDB, Cassandra, etc.).
 */
public interface InvoiceEmailLogRepository {

    /**
     * Persiste un log de intento de envío de correo de factura.
     *
     * @param invoiceId    id de la factura
     * @param tenantId     id del tenant
     * @param status       estado del envío (ej: SENT, FAILED, RETRYING)
     * @param timestamp    instante del evento
     * @param errorMessage detalle del error si aplica (null si fue exitoso)
     */
    void saveInvoiceEmailLog(String invoiceId, String tenantId, String status, Instant timestamp, String errorMessage);

    /**
     * Obtiene el último log registrado para una factura de un tenant.
     *
     * @param invoiceId id de la factura
     * @param tenantId  id del tenant
     * @return log más reciente (si existe)
     */
    Optional<InvoiceEmailLogView> findLastLogByInvoice(String invoiceId, String tenantId);
}