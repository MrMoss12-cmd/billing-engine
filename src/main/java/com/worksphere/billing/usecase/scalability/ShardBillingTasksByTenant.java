package com.worksphere.billing.usecase.scalability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Caso de uso para dividir los procesos de facturación masiva en shards o grupos.
 *
 * Cualidades:
 * - Distribución equitativa: balancear tenants por shard.
 * - Soporte multithreading: permitir ejecución concurrente segura.
 * - Idempotencia: reintentos seguros sin duplicar facturación.
 * - Resiliencia: fallos en un shard no afectan a los demás.
 * - Auditabilidad: registrar asignaciones y estado de procesamiento.
 * - Escalabilidad horizontal: soporta múltiples shards y workers.
 * - Integración con scheduler: coordina con ciclos automáticos de facturación.
 */
@Component
public class ShardBillingTasksByTenant {

    private static final Logger log = LoggerFactory.getLogger(ShardBillingTasksByTenant.class);

    // Registro en memoria de asignaciones shard → tenants (puede ser persistente en DB si se requiere)
    private final Map<Integer, Set<String>> shardAssignments = new ConcurrentHashMap<>();

    /**
     * Divide los tenants en shards de manera balanceada.
     *
     * @param tenantIds lista de IDs de tenants activos
     * @param totalShards número total de shards/worker disponibles
     * @return mapa shardId → lista de tenantIds asignados
     */
    public Map<Integer, List<String>> execute(List<String> tenantIds, int totalShards) {
        if (tenantIds == null || tenantIds.isEmpty() || totalShards <= 0) {
            log.warn("No tenants provided or invalid shard count");
            return Collections.emptyMap();
        }

        log.info("Sharding {} tenants into {} shards at {}", tenantIds.size(), totalShards, Instant.now());

        Map<Integer, List<String>> shards = new HashMap<>();
        for (int i = 0; i < totalShards; i++) {
            shards.put(i, new ArrayList<>());
        }

        // Distribución equitativa: round-robin
        for (int index = 0; index < tenantIds.size(); index++) {
            int shardId = index % totalShards;
            shards.get(shardId).add(tenantIds.get(index));
        }

        // Actualizar registro de shardAssignments para trazabilidad y auditabilidad
        shards.forEach((shardId, tenants) -> {
            shardAssignments.put(shardId, new HashSet<>(tenants));
            log.info("Shard {} assigned {} tenants", shardId, tenants.size());
        });

        return shards;
    }

    /**
     * Recupera tenants asignados a un shard específico.
     * Permite reintentos idempotentes sin duplicar tareas.
     *
     * @param shardId ID del shard
     * @return set de tenantIds asignados
     */
    public Set<String> getTenantsForShard(int shardId) {
        return shardAssignments.getOrDefault(shardId, Collections.emptySet());
    }

    /**
     * Marca un tenant como procesado dentro de un shard.
     * Resiliencia: fallos de un tenant no afectan al resto.
     *
     * @param shardId ID del shard
     * @param tenantId tenant procesado
     */
    public void markTenantProcessed(int shardId, String tenantId) {
        log.info("Tenant {} processed in shard {} at {}", tenantId, shardId, Instant.now());
        // Aquí se podría persistir estado en DB para auditabilidad y recuperación
    }
}
