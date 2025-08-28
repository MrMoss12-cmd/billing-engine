package com.worksphere.billingengine.test.unit;

import com.worksphere.billingengine.service.TaxRuleEngine;
import com.worksphere.billingengine.model.Invoice;
import com.worksphere.billingengine.model.TaxRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaxRuleEngineTest {

    @InjectMocks
    private TaxRuleEngine taxRuleEngine;

    @Mock
    private TaxRuleConfigService taxRuleConfigService; // Supongamos que provee reglas de impuestos por tenant

    private Invoice testInvoice;
    private List<TaxRule> testRules;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        testInvoice = new Invoice();
        testInvoice.setInvoiceId("INV-2025-08-001");
        testInvoice.setTenantId("tenant-test");
        testInvoice.setAmount(new BigDecimal("1000.00"));

        testRules = Arrays.asList(
                new TaxRule("IVA", new BigDecimal("0.19")),
                new TaxRule("Retefuente", new BigDecimal("0.04"))
        );
    }

    @Test
    void testCalculateTaxesSuccessfully() {
        when(taxRuleConfigService.getRulesForTenant("tenant-test")).thenReturn(testRules);

        BigDecimal totalTax = taxRuleEngine.calculateTaxes(testInvoice);

        // 1000 * 0.19 + 1000 * 0.04 = 230
        assertEquals(new BigDecimal("230.00"), totalTax.setScale(2));
        verify(taxRuleConfigService, times(1)).getRulesForTenant("tenant-test");
    }

    @Test
    void testCalculateTaxesWithNoRules() {
        when(taxRuleConfigService.getRulesForTenant("tenant-test")).thenReturn(Arrays.asList());

        BigDecimal totalTax = taxRuleEngine.calculateTaxes(testInvoice);

        assertEquals(BigDecimal.ZERO.setScale(2), totalTax.setScale(2));
        verify(taxRuleConfigService, times(1)).getRulesForTenant("tenant-test");
    }

    @Test
    void testInvalidRuleThrowsException() {
        // Regla inválida (por ejemplo, porcentaje negativo)
        TaxRule invalidRule = new TaxRule("IVA", new BigDecimal("-0.05"));
        when(taxRuleConfigService.getRulesForTenant("tenant-test")).thenReturn(Arrays.asList(invalidRule));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> taxRuleEngine.calculateTaxes(testInvoice));
        assertTrue(exception.getMessage().contains("Regla de impuesto inválida"));
    }

    @Test
    void testReproducibility() {
        when(taxRuleConfigService.getRulesForTenant("tenant-test")).thenReturn(testRules);

        BigDecimal firstCalculation = taxRuleEngine.calculateTaxes(testInvoice);
        BigDecimal secondCalculation = taxRuleEngine.calculateTaxes(testInvoice);

        assertEquals(firstCalculation, secondCalculation, "Los cálculos deben ser reproducibles y consistentes");
    }
}
