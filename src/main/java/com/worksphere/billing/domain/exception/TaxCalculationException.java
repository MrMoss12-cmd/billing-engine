package com.worksphere.billing.domain.exception;

/**
 * Excepción que se lanza cuando ocurre un problema al calcular impuestos
 * o aplicar reglas fiscales para un tenant.
 */
public class TaxCalculationException extends RuntimeException {

    // -----------------------------
    // Contexto del fallo
    // -----------------------------
    private final String tenantId;
    private final String country;
    private final String planId;
    private final String taxRuleId; // Ej. IVA 19%, CUFE, etc.

    // -----------------------------
    // Tipo de error técnico o de configuración
    // -----------------------------
    private final FailureType failureType;

    // -----------------------------
    // Impacto legal
    // -----------------------------
    private final boolean invalidatesInvoice;

    // -----------------------------
    // Constructor principal
    // -----------------------------
    public TaxCalculationException(String message,
                                   String tenantId,
                                   String country,
                                   String planId,
                                   String taxRuleId,
                                   FailureType failureType,
                                   boolean invalidatesInvoice) {
        super(message);
        this.tenantId = tenantId;
        this.country = country;
        this.planId = planId;
        this.taxRuleId = taxRuleId;
        this.failureType = failureType;
        this.invalidatesInvoice = invalidatesInvoice;
    }

    // -----------------------------
    // Getters
    // -----------------------------
    public String getTenantId() {
        return tenantId;
    }

    public String getCountry() {
        return country;
    }

    public String getPlanId() {
        return planId;
    }

    public String getTaxRuleId() {
        return taxRuleId;
    }

    public FailureType getFailureType() {
        return failureType;
    }

    public boolean isInvalidatesInvoice() {
        return invalidatesInvoice;
    }

    // -----------------------------
    // Enum para tipo de fallo
    // -----------------------------
    public enum FailureType {
        CONFIGURATION_ERROR, // tax-rules.yml corrupto o mal cargado
        COUNTRY_INCOMPATIBLE // regla fiscal no aplicable para país del tenant
    }
}
