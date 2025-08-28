package com.worksphere.billingengine.config;

import com.worksphere.billingengine.usecase.automation.ScheduleBillingCycle;
import com.worksphere.billingengine.usecase.automation.EvaluatePlanRenewal;
import com.worksphere.billingengine.usecase.automation.TriggerCancellationDueToNonPayment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class SchedulerConfig {

    private static final Logger logger = LoggerFactory.getLogger(SchedulerConfig.class);

    private final ScheduleBillingCycle scheduleBillingCycle;
    private final EvaluatePlanRenewal evaluatePlanRenewal;
    private final TriggerCancellationDueToNonPayment triggerCancellation;

    @Value("${scheduler.billing.cron:0 0 0 * * ?}") // default: daily at midnight
    private String billingCron;

    @Value("${scheduler.renewal.cron:0 30 0 * * ?}") // default: daily at 00:30
    private String renewalCron;

    @Value("${scheduler.cancellation.cron:0 0 1 * * ?}") // default: daily at 01:00
    private String cancellationCron;

    public SchedulerConfig(ScheduleBillingCycle scheduleBillingCycle,
                           EvaluatePlanRenewal evaluatePlanRenewal,
                           TriggerCancellationDueToNonPayment triggerCancellation) {
        this.scheduleBillingCycle = scheduleBillingCycle;
        this.evaluatePlanRenewal = evaluatePlanRenewal;
        this.triggerCancellation = triggerCancellation;
    }

    /**
     * Ejecuta automáticamente el ciclo de facturación según el cron definido.
     */
    @Scheduled(cron = "${scheduler.billing.cron}")
    public void runBillingCycle() {
        logger.info("Iniciando ciclo de facturación programado");
        try {
            scheduleBillingCycle.executeAllTenants();
            logger.info("Ciclo de facturación completado exitosamente");
        } catch (Exception e) {
            logger.error("Error ejecutando ciclo de facturación programado", e);
        }
    }

    /**
     * Evalúa la renovación de planes automáticamente.
     */
    @Scheduled(cron = "${scheduler.renewal.cron}")
    public void runPlanRenewals() {
        logger.info("Iniciando evaluación de renovación de planes");
        try {
            evaluatePlanRenewal.executeAllTenants();
            logger.info("Evaluación de renovación completada exitosamente");
        } catch (Exception e) {
            logger.error("Error ejecutando evaluación de renovación", e);
        }
    }

    /**
     * Cancela servicios o planes por falta de pago.
     */
    @Scheduled(cron = "${scheduler.cancellation.cron}")
    public void runCancellations() {
        logger.info("Iniciando cancelaciones por falta de pago");
        try {
            triggerCancellation.executeAllTenants();
            logger.info("Proceso de cancelaciones completado exitosamente");
        } catch (Exception e) {
            logger.error("Error ejecutando cancelaciones automáticas", e);
        }
    }
}
