package com.worksphere.billingengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

/**
 * Clase principal que arranca el microservicio Billing Engine.
 */
@SpringBootApplication(scanBasePackages = {
        "com.worksphere.billingengine.config",
        "com.worksphere.billingengine.service",
        "com.worksphere.billingengine.usecase",
        "com.worksphere.billingengine.transport",
        "com.worksphere.billingengine.utils",
        "com.worksphere.billingengine.events"
})
public class BillingEngineApplication {

    private static final Logger logger = LoggerFactory.getLogger(BillingEngineApplication.class);

    public static void main(String[] args) {
        logger.info("Starting BillingEngineApplication...");
        ApplicationContext context = SpringApplication.run(BillingEngineApplication.class, args);

        // Log de beans cargados y perfiles activos
        String[] activeProfiles = context.getEnvironment().getActiveProfiles();
        logger.info("Active Spring profiles: {}", (activeProfiles.length == 0) ? "default" : String.join(", ", activeProfiles));

        int beanCount = context.getBeanDefinitionCount();
        logger.info("Number of beans loaded: {}", beanCount);

        logger.info("BillingEngineApplication started successfully.");
    }
}
