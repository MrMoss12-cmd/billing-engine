package com.worksphere.billingengine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración de pasarelas de pago.
 */
@Configuration
public class PaymentConfig {

    private static final Logger logger = LoggerFactory.getLogger(PaymentConfig.class);

    // Claves y tokens cifrados (almacenados de manera segura en vault o encrypted)
    @Value("${payment.stripe.api-key}")
    private String stripeApiKey;

    @Value("${payment.paypal.client-id}")
    private String paypalClientId;

    @Value("${payment.paypal.client-secret}")
    private String paypalClientSecret;

    // Otros proveedores se pueden agregar aquí
    @Value("${payment.bank.local.api-key:}")
    private String bankLocalApiKey;

    /**
     * Mapa de proveedores de pago y sus credenciales.
     */
    @Bean
    public Map<String, PaymentProviderConfig> paymentProviders() {
        Map<String, PaymentProviderConfig> providers = new HashMap<>();

        // Stripe
        providers.put("stripe", PaymentProviderConfig.builder()
                .name("Stripe")
                .apiKey(stripeApiKey)
                .supportsRecurring(true)
                .build());

        // PayPal
        providers.put("paypal", PaymentProviderConfig.builder()
                .name("PayPal")
                .clientId(paypalClientId)
                .clientSecret(paypalClientSecret)
                .supportsRecurring(true)
                .build());

        // Banco local (opcional)
        if (!bankLocalApiKey.isEmpty()) {
            providers.put("bank_local", PaymentProviderConfig.builder()
                    .name("BancoLocal")
                    .apiKey(bankLocalApiKey)
                    .supportsRecurring(false)
                    .build());
        }

        logger.info("Payment providers configured: {}", providers.keySet());
        return providers;
    }

    /**
     * Configuración individual de cada proveedor de pago.
     */
    public static class PaymentProviderConfig {
        private String name;
        private String apiKey;
        private String clientId;
        private String clientSecret;
        private boolean supportsRecurring;

        public static Builder builder() {
            return new Builder();
        }

        public String getName() { return name; }
        public String getApiKey() { return apiKey; }
        public String getClientId() { return clientId; }
        public String getClientSecret() { return clientSecret; }
        public boolean isSupportsRecurring() { return supportsRecurring; }

        public static class Builder {
            private final PaymentProviderConfig config = new PaymentProviderConfig();

            public Builder name(String name) { config.name = name; return this; }
            public Builder apiKey(String apiKey) { config.apiKey = apiKey; return this; }
            public Builder clientId(String clientId) { config.clientId = clientId; return this; }
            public Builder clientSecret(String clientSecret) { config.clientSecret = clientSecret; return this; }
            public Builder supportsRecurring(boolean supportsRecurring) { config.supportsRecurring = supportsRecurring; return this; }
            public PaymentProviderConfig build() { return config; }
        }
    }
}
