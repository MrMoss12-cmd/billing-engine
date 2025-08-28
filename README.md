# Resumen del microservicio billing-engine

## 🎯 Rol principal

El microservicio **billing-engine** es el núcleo del sistema de facturación multitenant.  
Su objetivo es **automatizar el ciclo completo de facturación** de múltiples tenants, aplicando reglas fiscales, generando facturas, procesando pagos y emitiendo notificaciones, todo con **seguridad, auditabilidad y escalabilidad**.

---

## ⚙️ Cómo debería funcionar

### Ciclo de facturación automatizado
- Usa `SchedulerConfig` para disparar cronjobs que ejecutan el `BillingEngine`.
- Genera facturas periódicas según los planes de cada tenant.

### Cálculo y aplicación de impuestos
- El `TaxRuleEngine` aplica reglas definidas en `tax-rules.yml`.
- Compatible con múltiples países, monedas y tipos de impuestos.

### Generación de facturas
- El `BillingEngine` crea facturas (PDF/XML).
- `InvoiceSigner` firma digitalmente los documentos para asegurar su validez legal.

### Integración con pasarelas de pago
- `PaymentConfig` define proveedores como Stripe, PayPal o bancos.
- Se asegura **idempotencia** en pagos y maneja reintentos ante fallos.

### Notificaciones y comunicación
- El caso de uso `EmitNotificationEvent` emite eventos (Kafka, RabbitMQ).
- `EmailProvider` envía notificaciones a clientes con la factura adjunta.
- `InvoiceEmailLogRepository` registra logs de cada correo enviado.

### Seguridad y trazabilidad
- Autenticación via JWT/mTLS en `BillingController`.
- Auditoría de todos los cálculos, eventos y pagos.

### Escalabilidad y aislamiento
- Soporta múltiples tenants concurrentes.
- Configuración flexible por entorno (`application.yml`, `application-prod.yml`).

---

## 🔄 Cómo podría evolucionar o funcionar mejor

- **Optimización con colas/eventos**: facturación y notificaciones podrían ser asincrónicas (Kafka, RabbitMQ) para mejorar la resiliencia.  
- **Feature toggles**: activar/desactivar pasarelas de pago o reglas fiscales por tenant sin recompilar.  
- **Soporte multimoneda avanzado**: conectarse a servicios de tipo de cambio en tiempo real.  
- **Monitoreo en tiempo real**: integración con `metrics-agent` para exponer métricas vía Prometheus.  
- **Self-healing**: si un pago falla o una notificación no se envía, el sistema podría reintentar automáticamente sin intervención manual.  

---

## 📂 Casos de uso principales del microservicio

Aquí te los resumo como **capas de negocio**:

### 1. Facturación
- `run_billing_cycle`: ejecutar el ciclo de facturación de un tenant.  
- `generate_invoice`: crear factura (PDF/XML).  
- `sign_invoice`: firmar digitalmente la factura.  

### 2. Impuestos y reglas fiscales
- `apply_tax_rules`: calcular impuestos según `tax-rules.yml`.  
- `validate_tax_configuration`: validar reglas fiscales antes de aplicarlas.  

### 3. Pagos
- `process_payment`: integrar con pasarela (Stripe, PayPal, banco).  
- `retry_payment`: manejar reintentos de pago fallidos.  
- `refund_payment`: procesar devoluciones.  

### 4. Notificaciones y comunicación
- `emit_notification_event`: emitir evento hacia Kafka/RabbitMQ.  
- `send_invoice_email`: enviar factura por correo.  
- `log_email_delivery`: registrar logs de correos enviados.  

### 5. Monitoreo y auditoría
- `audit_billing_events`: registrar auditoría de cálculos y notificaciones.  
- `export_billing_logs`: generar reportes de facturación.  

### 6. Seguridad y acceso
- `secure_api_endpoints`: proteger endpoints con JWT/mTLS.  
- `authorize_tenant_access`: validar permisos por tenant.  

---

## ✅ En pocas palabras
El `billing-engine` es el **cerebro de facturación**. Automatiza la creación de facturas, aplica impuestos, procesa pagos y emite notificaciones, todo de forma **segura, auditable y escalable**.

