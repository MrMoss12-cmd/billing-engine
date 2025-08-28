package com.worksphere.billing.usecase.integration;

import com.worksphere.billing.domain.model.UsageMetric;
import com.worksphere.billing.domain.model.UsageReport;
import com.worksphere.billing.infrastructure.repository.MetricsRepository;
import com.worksphere.billing.usecase.audit.LogBillingOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Caso de uso para recuperar datos de consumo del metrics-agent y normalizarlos para facturación.
 *
 * Cualidades cubiertas:
 * - Confiabilidad: reintentos con backoff, validaciones, y persistencia.
 * - Formato estándar: normaliza al DTO UsageMetric / UsageReport.
 * - Seguridad: incluye JWT en los headers (proveedor externo).
 * - Tolerancia a fallos: reintentos y manejo de errores con logs detallados.
 * - Escalabilidad: usa WebClient/reactor para streaming y paralelismo.
 * - Auditabilidad: registra cuándo y qué datos fueron obtenidos por tenant y ciclo.
 */
@Component
public class FetchUsageFromMetricsAgent {

    private static final Logger log = LoggerFactory.getLogger(FetchUsageFromMetricsAgent.class);

    private final WebClient metricsWebClient;
    private final TokenProvider tokenProvider;
    private final MetricsRepository metricsRepository;
    private final LogBillingOperation logBillingOperation;

    // parámetros de resiliencia (podrían venir de config)
    private final int maxRetries = 3;
    private final Duration initialBackoff = Duration.ofSeconds(2);

    public FetchUsageFromMetricsAgent(WebClient metricsWebClient,
                                      TokenProvider tokenProvider,
                                      MetricsRepository metricsRepository,
                                      LogBillingOperation logBillingOperation) {
        this.metricsWebClient = metricsWebClient;
        this.tokenProvider = tokenProvider;
        this.metricsRepository = metricsRepository;
        this.logBillingOperation = logBillingOperation;
    }

    /**
     * Recupera y normaliza métricas de uso para un tenant y ciclo de facturación.
     *
     * Este método:
     * - pide al metrics-agent los datos del tenant para el periodo indicado,
     * - aplica validaciones y normalizaciones (ej. unidades uniformes),
     * - persiste el resultado para auditabilidad,
     * - devuelve un UsageReport listo para el motor de facturación.
     *
     * Nota: usa reactive WebClient y bloquea para compatibilidad con código sincrónico.
     *
     * @param tenantId       tenant a consultar
     * @param billingCycleId id del ciclo de facturación
     * @param fromTimestamp  inicio del periodo (inclusive)
     * @param toTimestamp    fin del periodo (inclusive)
     * @return UsageReport normalizado y persistido
     */
    public UsageReport fetchUsage(String tenantId, UUID billingCycleId, Instant fromTimestamp, Instant toTimestamp) {
        log.info("Solicitando métricas a metrics-agent para tenant={} billingCycle={} desde={} hasta={}",
                tenantId, billingCycleId, fromTimestamp, toTimestamp);

        String jwt = tokenProvider.getServiceToken(); // JWT para autenticación mutual/trusted

        // Llamada al endpoint del metrics-agent (se asume path /api/v1/usage)
        Flux<RawMetricDto> remoteStream = metricsWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/usage")
                        .queryParam("tenantId", tenantId)
                        .queryParam("from", fromTimestamp.toString())
                        .queryParam("to", toTimestamp.toString())
                        .build())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .retrieve()
                .bodyToFlux(RawMetricDto.class)
                .retryWhen(Retry.backoff(maxRetries, initialBackoff)
                        .transientErrors(true)
                        .doBeforeRetry(rs -> log.warn("Reintentando fetchUsage (attempt #{}) para tenant={} reason={}",
                                rs.totalRetries() + 1, tenantId, rs.failure().getMessage())))
                .doOnError(err -> log.error("Error al obtener métricas para tenant={} : {}", tenantId, err.getMessage()));

        // Convertir, validar y normalizar el stream a UsageMetric
        List<UsageMetric> normalized = remoteStream
                .map(this::normalize)
                .filter(this::validate)
                .collectList()
                .doOnNext(list -> {
                    // Persistir métricas crudas/normalizadas para auditoría y reconciliación
                    metricsRepository.saveMetrics(tenantId, billingCycleId, list);
                    // Registrar operación en log de facturación
                    logBillingOperation.logOperation(
                            tenantId,
                            billingCycleId.toString(),
                            null,
                            "FETCH_USAGE",
                            "system",
                            "Se obtuvieron " + list.size() + " métricas desde metrics-agent"
                    );
                })
                .doOnError(err -> {
                    // Log detallado para auditoría en caso de error
                    logBillingOperation.logOperation(
                            tenantId,
                            billingCycleId.toString(),
                            null,
                            "FETCH_USAGE_FAILED",
                            "system",
                            "Error obteniendo métricas: " + err.getMessage()
                    );
                })
                .block(); // se bloquea para interoperar con capas sin-reactor

        // Crear UsageReport determinístico y listo para facturación
        UsageReport report = UsageReport.builder()
                .tenantId(tenantId)
                .billingCycleId(billingCycleId)
                .from(fromTimestamp)
                .to(toTimestamp)
                .metrics(normalized != null ? normalized : List.of())
                .generatedAt(Instant.now())
                .build();

        log.info("Métricas normalizadas y persistidas tenant={} billingCycle={} metricsCount={}",
                tenantId, billingCycleId, report.getMetrics().size());

        return report;
    }

    /**
     * Normaliza una entrada cruda recibida del metrics-agent hacia el formato interno UsageMetric.
     * Aquí se deben aplicar reglas de normalización (unidades, etiquetas, agregaciones).
     */
    private UsageMetric normalize(RawMetricDto raw) {
        // Ejemplo de normalización simple — en producción aquí habría más reglas
        UsageMetric m = new UsageMetric();
        m.setMetricName(raw.getMetricName().trim().toLowerCase());
        // Convertir unidades a la unidad estándar (ej: MB -> GB) si aplica:
        BigDecimal value = raw.getValue();
        String unit = raw.getUnit() == null ? "" : raw.getUnit().trim().toLowerCase();
        if ("mb".equals(unit)) {
            m.setValue(value.divide(BigDecimal.valueOf(1024), 8, BigDecimal.ROUND_HALF_UP));
            m.setUnit("gb");
        } else {
            m.setValue(value);
            m.setUnit(unit.isEmpty() ? "unit" : unit);
        }
        m.setTimestamp(raw.getTimestamp());
        m.setMetadata(raw.getMetadata());
        return m;
    }

    /**
     * Validaciones básicas de integridad para cada métrica.
     */
    private boolean validate(UsageMetric m) {
        boolean ok = m.getValue() != null && m.getValue().compareTo(BigDecimal.ZERO) >= 0;
        if (!ok) {
            log.warn("Métrica inválida filtrada: {} @ {} value={}", m.getMetricName(), m.getTimestamp(), m.getValue());
        }
        return ok;
    }

    // ---------------------------
    // DTOs/Interfaces mínimas requeridas
    // ---------------------------

    /**
     * DTO que representa la forma en la que metrics-agent devuelve una métrica.
     * (Se espera JSON compatible con esta estructura)
     */
    public static class RawMetricDto {
        private String metricName;
        private BigDecimal value;
        private String unit;
        private Instant timestamp;
        private String metadata;

        // getters / setters
        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }
        public BigDecimal getValue() { return value; }
        public void setValue(BigDecimal value) { this.value = value; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
        public String getMetadata() { return metadata; }
        public void setMetadata(String metadata) { this.metadata = metadata; }
    }

    /**
     * Interfaz simple para obtener tokens de servicio (JWT) para autenticarse ante el metrics-agent.
     * Implementar TokenProvider que consulte Vault o servicio de identidad.
     */
    public interface TokenProvider {
        String getServiceToken();
    }
}
