package com.worksphere.billing.usecase.payment;

import com.worksphere.billing.domain.model.PaymentToken;
import com.worksphere.billing.domain.exception.PaymentException;
import com.worksphere.billing.service.PaymentGatewayAdapter;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * Caso de uso encargado de validar la autenticidad y vigencia del token de pago.
 */
@Component
public class ValidatePaymentToken {

    private static final Logger logger = LoggerFactory.getLogger(ValidatePaymentToken.class);

    private final PaymentGatewayAdapter paymentGatewayAdapter;

    public ValidatePaymentToken(PaymentGatewayAdapter paymentGatewayAdapter) {
        this.paymentGatewayAdapter = paymentGatewayAdapter;
    }

    /**
     * Valida un token de pago asegurando que sea legítimo, vigente y seguro.
     *
     * @param token PaymentToken a validar
     * @param tenantId Tenant asociado al token
     * @throws PaymentException si el token no es válido
     */
    public void execute(PaymentToken token, String tenantId) throws PaymentException {

        // -----------------------------
        // 1. Verificación de vigencia
        // -----------------------------
        if (token.getExpirationDate() == null || token.getExpirationDate().isBefore(LocalDateTime.now())) {
            logger.warn("Token expirado para tenant {}", tenantId);
            throw new PaymentException("Token de pago expirado");
        }

        // -----------------------------
        // 2. Asociación segura
        // -----------------------------
        if (!tenantId.equals(token.getTenantId())) {
            logger.warn("Token no pertenece al tenant {}. Token tenant: {}", tenantId, token.getTenantId());
            throw new PaymentException("Token no asociado al tenant correcto");
        }

        // -----------------------------
        // 3. Integridad del token
        // -----------------------------
        if (!token.isValidSignature()) {
            logger.warn("Token manipulado o inválido para tenant {}", tenantId);
            throw new PaymentException("Token de pago inválido o manipulado");
        }

        // -----------------------------
        // 4. Validación multi-proveedor
        // -----------------------------
        boolean providerValid = paymentGatewayAdapter.validateToken(token);
        if (!providerValid) {
            logger.warn("Token rechazado por pasarela para tenant {}", tenantId);
            throw new PaymentException("Token no válido según proveedor de pago");
        }

        // -----------------------------
        // 5. Prevención de fraude
        // -----------------------------
        if (token.isReused()) {
            logger.warn("Intento de reutilización de token detectado para tenant {}", tenantId);
            throw new PaymentException("Token ya utilizado. Posible intento de fraude.");
        }

        logger.info("Token válido para tenant {} y tokenId {}", tenantId, token.getTokenId());
    }
}
