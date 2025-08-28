package com.worksphere.billing.usecase.scalability;

import com.worksphere.billing.domain.model.PricingRule;
import com.worksphere.billing.domain.repository.PricingRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Caso de uso para recuperar las reglas de tarifas y precios por tenant.
 *
 * Cualidades:
 * - Especificidad por tenant: reglas distintas según plan, región o modelo de cobro.
 * - Consistencia de datos: reglas auditables y verificadas.
 * - Escalabilidad: soporte para múltiples consultas concurrentes.
 * - Idempotencia: misma petición → mismo resultado mientras no se actualicen reglas.
 * - Trazabilidad: registrar qué reglas se aplicaron.
 * - Integración con persistencia: acceso seguro a base de datos o config-service.
 */
@Component
public class FetchPricingRulesByTenant {

    private static final Logger log = LoggerFactory.getLogger(FetchPricingRulesByTenant.class);

    private final PricingRuleRepository pricingRuleRepository;

    public FetchPricingRulesByTenant(PricingRuleRepository pricingRuleRepository) {
        this.pricingRuleRepository = pricingRuleRepository;
    }

    /**
     * Obtiene las reglas de facturación de un tenant específico de forma idempotente y auditada.
     *
     * @param tenantId ID del tenant
     * @param planId   ID del plan (opcional)
     * @return lista de reglas de precios aplicables
     */
    public List<PricingRule> execute(String tenantId, String planId) {
        log.info("Fetching pricing rules for tenant={} plan={} at {}", tenantId, planId, Instant.now());

        if (tenantId == null || tenantId.isBlank()) {
            log.warn("Tenant ID is null or blank, returning empty pricing rules list");
            return Collections.emptyList();
        }

        // Idempotencia: misma petición → mismo resultado mientras las reglas no cambien
        List<PricingRule> rules;
        if (planId == null || planId.isBlank()) {
            rules = pricingRuleRepository.findByTenantId(tenantId);
        } else {
            rules = pricingRuleRepository.findByTenantIdAndPlanId(tenantId, planId);
        }

        if (rules.isEmpty()) {
            log.warn("No pricing rules found for tenant={} plan={}", tenantId, planId);
        } else {
            log.debug("Fetched {} pricing rules for tenant={} plan={}", rules.size(), tenantId, planId);
        }

        return rules;
    }

    // ----------------------------
    // Interfaz mínima del repositorio
    // ----------------------------
    public interface PricingRuleRepository {
        List<PricingRule> findByTenantId(String tenantId);
        List<PricingRule> findByTenantIdAndPlanId(String tenantId, String planId);
    }
}
