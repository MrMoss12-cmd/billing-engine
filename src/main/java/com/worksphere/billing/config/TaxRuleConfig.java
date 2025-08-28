package com.worksphere.billingengine.config;

import com.worksphere.billingengine.service.TaxRuleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Map;

/**
 * Configuración del motor de reglas fiscales y tributarias por tenant y jurisdicción.
 */
@Configuration
public class TaxRuleConfig {

    private static final Logger logger = LoggerFactory.getLogger(TaxRuleConfig.class);

    @Value("classpath:tax-rules.yml")
    private Resource taxRulesResource;

    /**
     * Carga y expone las reglas fiscales para ser utilizadas por el TaxRuleEngine.
     */
    @Bean
    public Map<String, Object> taxRules() {
        try (InputStream input = taxRulesResource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> rules = yaml.load(input);
            logger.info("Loaded tax rules for tenants: {}", rules.keySet());
            return rules;
        } catch (Exception e) {
            logger.error("Failed to load tax rules from tax-rules.yml", e);
            throw new RuntimeException("Cannot initialize tax rules", e);
        }
    }

    /**
     * Configura el motor de reglas fiscales usando las reglas cargadas.
     */
    @Bean
    public TaxRuleEngine taxRuleEngine(Map<String, Object> taxRules) {
        TaxRuleEngine engine = new TaxRuleEngine();
        engine.loadRules(taxRules);
        logger.info("TaxRuleEngine initialized with {} rules", taxRules.size());
        return engine;
    }
}
