#!/bin/bash
# scripts/simulate_billing_flow.sh
# Simula un ciclo completo de facturación para testing y debugging.
# Autor: Auto generado
# Fecha: $(date)

set -euo pipefail

#############################
# CONFIGURACIÓN Y PARÁMETROS
#############################

TENANT_ID="${1:-tenant_test}"             # Tenant a simular
BILLING_CYCLE="${2:-2025-08}"             # Ciclo de facturación
SCENARIO="${3:-success}"                  # Escenario: success | payment_fail | partial

LOG_FILE="./simulate_billing_flow_${TENANT_ID}_${BILLING_CYCLE}.log"

echo "==============================" | tee -a "$LOG_FILE"
echo "Iniciando simulación de facturación" | tee -a "$LOG_FILE"
echo "Tenant: $TENANT_ID, Ciclo: $BILLING_CYCLE, Escenario: $SCENARIO" | tee -a "$LOG_FILE"
echo "Fecha: $(date)" | tee -a "$LOG_FILE"
echo "==============================" | tee -a "$LOG_FILE"

#############################
# FUNCIONES AUXILIARES
#############################

log_step() {
    local step="$1"
    echo "[STEP] $step" | tee -a "$LOG_FILE"
}

call_api() {
    local endpoint="$1"
    local payload="$2"
    echo "POST $endpoint - Payload: $payload" | tee -a "$LOG_FILE"
    # Simulación de curl (descomentar en entorno real)
    # curl -s -X POST "$endpoint" -H "Content-Type: application/json" -d "$payload"
}

#############################
# SIMULACIÓN DEL FLUJO
#############################

# 1. Recuperar métricas del tenant
log_step "Recuperando métricas del tenant"
call_api "http://localhost:8080/api/metrics/$TENANT_ID/$BILLING_CYCLE" "{}"

# 2. Aplicar reglas de precios y cálculo de impuestos
log_step "Calculando facturación y aplicando reglas de impuestos"
call_api "http://localhost:8080/api/calculation/apply_tax" "{\"tenant_id\":\"$TENANT_ID\",\"billing_cycle\":\"$BILLING_CYCLE\"}"

# 3. Generar factura
log_step "Generando factura"
call_api "http://localhost:8080/api/calculation/generate_invoice" "{\"tenant_id\":\"$TENANT_ID\",\"billing_cycle\":\"$BILLING_CYCLE\"}"

# 4. Validar token de pago
log_step "Validando token de pago"
call_api "http://localhost:8080/api/payment/validate_token" "{\"tenant_id\":\"$TENANT_ID\"}"

# 5. Iniciar pago según escenario
if [ "$SCENARIO" = "success" ]; then
    log_step "Iniciando pago exitoso"
    call_api "http://localhost:8080/api/payment/initiate" "{\"tenant_id\":\"$TENANT_ID\",\"simulate\":\"success\"}"
elif [ "$SCENARIO" = "payment_fail" ]; then
    log_step "Simulando fallo de pago"
    call_api "http://localhost:8080/api/payment/initiate" "{\"tenant_id\":\"$TENANT_ID\",\"simulate\":\"fail\"}"
else
    log_step "Escenario desconocido, simulando pago parcial"
    call_api "http://localhost:8080/api/payment/initiate" "{\"tenant_id\":\"$TENANT_ID\",\"simulate\":\"partial\"}"
fi

# 6. Emitir eventos a Kafka
log_step "Emitir eventos de facturación a Kafka"
call_api "http://localhost:8080/api/notification/emit_event" "{\"tenant_id\":\"$TENANT_ID\",\"billing_cycle\":\"$BILLING_CYCLE\"}"

# 7. Enviar email al tenant
log_step "Enviando factura por email"
call_api "http://localhost:8080/api/notification/send_invoice_email" "{\"tenant_id\":\"$TENANT_ID\",\"billing_cycle\":\"$BILLING_CYCLE\"}"

# 8. Emitir webhook a sistemas externos
log_step "Disparando webhook a sistemas externos"
call_api "http://localhost:8080/api/notification/emit_webhook" "{\"tenant_id\":\"$TENANT_ID\",\"billing_cycle\":\"$BILLING_CYCLE\"}"

echo "==============================" | tee -a "$LOG_FILE"
echo "Simulación completada para Tenant: $TENANT_ID, Ciclo: $BILLING_CYCLE" | tee -a "$LOG_FILE"
echo "Logs guardados en: $LOG_FILE"
echo "==============================" | tee -a "$LOG_FILE"
