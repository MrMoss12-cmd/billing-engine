package com.worksphere.billing.domain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representa un token seguro para procesar pagos.
 * Cumple con PCI DSS, expiración controlada y soporte multi-gateway.
 */
@Entity
@Table(name = "payment_tokens", indexes = {
        @Index(name = "idx_payment_token_tenant", columnList = "tenantId")
})
public class PaymentToken {

    // -----------------------------
    // Identidad única del token
    // -----------------------------
    @Id
    @Column(name = "token_id", nullable = false, updatable = false)
    private UUID tokenId;

    // -----------------------------
    // Asociación multi-tenant
    // -----------------------------
    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    // -----------------------------
    // Token cifrado seguro (PCI DSS)
    // -----------------------------
    @Column(name = "encrypted_token", nullable = false)
    private String encryptedToken;

    // -----------------------------
    // Expiración y revocación
    // -----------------------------
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;

    // -----------------------------
    // Compatibilidad multi-gateway
    // -----------------------------
    @Column(name = "gateway_provider", nullable = false)
    private String gatewayProvider; // Stripe, PayPal, Banco local, etc.

    // -----------------------------
    // Reusabilidad controlada
    // -----------------------------
    @Column(name = "reusable", nullable = false)
    private boolean reusable;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // -----------------------------
    // Constructor
    // -----------------------------
    public PaymentToken() {
        this.tokenId = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
        this.revoked = false;
        this.reusable = false;
    }

    // -----------------------------
    // Getters & Setters
    // -----------------------------
    public UUID getTokenId() {
        return tokenId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getEncryptedToken() {
        return encryptedToken;
    }

    public void setEncryptedToken(String encryptedToken) {
        this.encryptedToken = encryptedToken;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public String getGatewayProvider() {
        return gatewayProvider;
    }

    public void setGatewayProvider(String gatewayProvider) {
        this.gatewayProvider = gatewayProvider;
    }

    public boolean isReusable() {
        return reusable;
    }

    public void setReusable(boolean reusable) {
        this.reusable = reusable;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // -----------------------------
    // Validación de validez
    // -----------------------------
    public boolean isValid() {
        return !revoked && LocalDateTime.now().isBefore(expiresAt);
    }
}
