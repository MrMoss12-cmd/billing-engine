package com.worksphere.billing.usecase.automation;

import com.worksphere.billing.domain.model.BillingCycle;
import com.worksphere.billing.domain.model.Tenant;
import com.worksphere.billing.domain.exception.BillingException;
import com.worksphere.billing.service.BillingCycleRepository;
import com.worksphere.billing.transport.events.BillingCycleStartedEvent;
import com.worksphere.billing.transport.events.EventPublisher;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Caso de uso para programar y ejecutar automáticamente los ciclos de facturación.
 */
@Component
public class ScheduleBillingCycle {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleBillingCycle.class);

    private final BillingCycleRepository billingCycleRepository;
    private final EventPublisher eventPublisher;

    public ScheduleBillingCycle(BillingCycleRepository billingCycleRepository,
                                EventPublisher eventPublisher) {
        this.billingCycleRepository = billingCycleRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Ejecutar programación de ciclos para una lista de tenants.
     *
     * @param tenants lista de tenants a facturar
     */
    @Transactional
    public void execute(List<Tenant> tenants) {

        for (Tenant tenant : tenants) {
            try {
                // -----------------------------
                // 1. Determinar fechas del ciclo
                // -----------------------------
                LocalDateTime start = determineCycleStart(tenant);
                LocalDateTime end = determineCycleEnd(tenant, start);

                // -----------------------------
                // 2. Idempotencia: verificar si ya existe ciclo para este tenant y periodo
                // -----------------------------
                boolean exists = billingCycleRepository.existsByTenantIdAndPeriod(tenant.getTenantId(), start, end);
                if (exists) {
                    logger.info("Ciclo ya programado para tenant {}: {} - {}", tenant.getTenantId(), start, end);
                    continue;
                }

                // -----------------------------
                // 3. Crear ciclo de facturación
                // -----------------------------
                BillingCycle cycle = new BillingCycle();
                cycle.setTenantId(tenant.getTenantId());
                cycle.setStartDate(start);
                cycle.setEndDate(end);
                cycle.setStatus(BillingCycle.Status.SCHEDULED);
                billingCycleRepository.save(cycle);

                // -----------------------------
                // 4. Emitir evento
                // -----------------------------
                eventPublisher.publish(new BillingCycleStartedEvent(cycle));

                logger.info("Ciclo programado correctamente: tenant {}, {} - {}", tenant.getTenantId(), start, end);

            } catch (Exception e) {
                logger.error("Error al programar ciclo para tenant {}", tenant.getTenantId(), e);
                // -----------------------------
                // 5. Resiliencia: permitir reintentos mediante scheduler
                // -----------------------------
                // Dependiendo del scheduler (ej. Spring @Scheduled) se volverá a intentar
            }
        }
    }

    // -----------------------------
    // Métodos auxiliares para determinar fechas de ciclo
    // -----------------------------
    private LocalDateTime determineCycleStart(Tenant tenant) {
        // Ejemplo: iniciar al primer día del mes
        return LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
    }

    private LocalDateTime determineCycleEnd(Tenant tenant, LocalDateTime start) {
        // Ejemplo: fin al último día del mes
        return start.plusMonths(1).minusSeconds(1);
    }
}
