# Plan de Implementación — MyBusiness Silva (POS en la Nube)

Este plan traduce el diseño aprobado en tareas de código incrementales y verificables. Se construye de
abajo hacia arriba (cimientos primero) y cada tarea deja algo compilable y probado antes de avanzar.
Cada tarea referencia los requisitos que satisface (ver `requirements.md`).

Convenciones:
- El backend (Spring Boot hexagonal) es la base; el front web (React PWA) y la app (Flutter) se
  construyen por módulo una vez que su API existe.
- Cada tarea incluye pruebas. No se avanza a la siguiente etapa sin compilar y pasar pruebas.

---

## Etapa 0 — Fundaciones del proyecto

- [x] 0.1 Inicializar el repositorio y la estructura de carpetas (backend, frontend-web, mobile, docs).
  - Crear `backend/` (Spring Boot), `frontend-web/` (React+Vite), `mobile/` (Flutter), `infra/`, `docs/`.
  - Configurar `.gitignore`, README raíz y convenciones de rama/commit.
  - _Requisitos: base para todos._

- [x] 0.2 Configurar el proyecto Spring Boot con estructura hexagonal base.
  - Dependencias: Web, Security, Validation, JPA, PostgreSQL, Flyway, Actuator.
  - Java 25 (LTS) + Spring Boot 4.1.1 + Maven. Paquete `platform` creado y documentado.
  - Configurar perfiles (`dev`, `prod`) y variables de entorno para secretos.
  - _Requisitos: 19._

- [x] 0.3 Configurar base de datos local y migraciones Flyway.
  - Docker Compose con PostgreSQL 17 (puerto host 5544) + script de usuario de app.
  - Migración inicial del schema `admin` (business, plan, module_catalog, business_module,
    superadmin_user, superadmin_sale, audit_log_global) + semilla de módulos y planes.
  - Verificado: migraciones aplicadas y datos semilla correctos.
  - _Requisitos: 1, 19._

- [x] 0.4 Configurar calidad y pruebas.
  - JUnit 5 + Testcontainers (PostgreSQL real). Prueba de arranque de contexto y de migraciones.
  - Verificado: `mvn test` → 2/2 pruebas en verde, BUILD SUCCESS.
  - _Requisitos: base de verificación._

---

## Etapa 1 — Plataforma: Multi-Tenancy

- [x] 1.1 Implementar `TenantContext` y resolución de tenant.
  - `TenantContext` (ThreadLocal), `TenantResolver` (encabezado X-Tenant-Id / subdominio),
    `TenantFilter` (fija y limpia el contexto por petición). `TenantSchema` valida nombres.
  - _Requisitos: 1.2, 1.6._

- [x] 1.2 Implementar enrutamiento de conexión por schema.
  - `TenantAwareDataSource` envuelve el pool y fija `search_path` por conexión.
    Registrado vía `BeanPostProcessor` (compatible con la autoconfiguración de Boot 4).
  - Preparado para base dedicada por tenant (configuración de conexión aislada).
  - _Requisitos: 1.1, 1.4._

- [x] 1.3 Implementar Row-Level Security (RLS).
  - Variable de sesión `app.current_tenant` + políticas RLS con FORCE en las tablas de tenant.
  - _Requisitos: 1.3, 1.5._

- [x] 1.4 Servicio de aprovisionamiento de tenant.
  - `TenantProvisioningService`: crea schema `tenant_<id>` y corre migraciones del tenant
    (Flyway con placeholder `${tenant_schema}`) aplicando RLS.
  - _Requisitos: 1.1._

- [x] 1.5 Pruebas de aislamiento multi-tenant.
  - Verificado con PostgreSQL real y rol NO superusuario: cada tenant ve solo sus datos y
    RLS rechaza filas de otro tenant. `mvn test` → 4/4 en verde.
  - _Requisitos: 1.3, 1.5._

---

## Etapa 2 — Plataforma: Seguridad, Roles y Auditoría

- [~] 2.1 Modelo de usuarios, roles y permisos (schema admin y por tenant).
  - Tablas base ya creadas (`superadmin_user` en admin; `app_user` en tenant). Roles predefinidos
    en `Roles`. El modelo completo de `role`/`role_permission` se completa en Etapa 3 (alta real).
  - _Requisitos: 4.1, 4.4._

- [x] 2.2 Autenticación con Argon2id + JWT + refresh token.
  - `PasswordConfig` (Argon2id), `JwtService` (access/refresh con claims userId/tenant/roles/módulos),
    `JwtAuthenticationFilter`. Endpoint de login se conecta con datos reales en Etapa 3.
  - _Requisitos: 3.1, 3.2._

- [x] 2.3 MFA (TOTP) y protección de credenciales.
  - `MfaService` (TOTP: secreto, URI QR, verificación) y `LoginRateLimiter` (bloqueo por intentos).
  - _Requisitos: 3.3, 3.4._

- [x] 2.4 Autorización por rol y por módulo habilitado.
  - `SecurityConfig` (stateless, method security), `ModuleAccessEvaluator` con la regla de tres capas
    (módulo habilitado comercialmente Y permiso del rol; Super Admin exento del gating por módulo).
  - _Requisitos: 3.7, 4.2, 4.3, 4.5, 2.11._

- [x] 2.5 Auditoría inmutable (append-only).
  - `AuditService` escribe en `admin.audit_log_global` (JSONB). Consulta para roles autorizados en Etapa 3.
  - _Requisitos: 3.8, 18.1, 18.2._

- [~] 2.6 Endurecimiento (validación de entrada, TLS, cifrado en reposo de sensibles).
  - Base establecida (Bean Validation disponible, errores sin fuga de stacktrace, CORS controlado).
    Cifrado en reposo de datos fiscales/secretos se aplica al introducir esos datos (Etapas 3/8).
  - _Requisitos: 3.5, 3.6, 3.9._

- [x] 2.7 Pruebas de seguridad (unitarias).
  - JWT (round-trip y token manipulado), Argon2id, MFA (código TOTP real), rate limiter,
    autorización por módulo. `mvn test` → 12/12 en verde (incluye integración de etapas previas).
  - _Requisitos: 3.*, 4.*._

---

## Etapa 3 — Licenciamiento, Planes y Facturación del Super Admin

- [x] 3.1 Modelo de negocios, planes y módulos.
  - Dominio hexagonal: `Business` (reglas del ciclo de licencia), `Plan`, `BusinessModule`, estados.
    Repositorios JDBC sobre schema `admin`. Login real del Super Admin (`AuthController`/`AuthService`).
  - _Requisitos: 2.9, 2.10, 5.B.1._

- [x] 3.2 Caso de uso: crear negocio con plan y periodo de prueba configurable.
  - `LicensingService.createBusiness`: registra datos (transaccional) y aprovisiona el schema del
    tenant FUERA de esa transacción (se corrigió un interbloqueo DDL/transacción real).
  - _Requisitos: 2.1, 5.B.2, 5.B.4._

- [x] 3.3 Gestión del ciclo de licencia.
  - Compra de licencia (→ ACTIVE, permanente), suspensión/reactivación (conserva datos),
    expiración de pruebas vencidas. Verificado con reloj fijo y con integración real.
  - _Requisitos: 2.2, 2.3, 2.5, 2.6, 2.7, 5.B.5, 5.B.6._

- [x] 3.4 Habilitación comercial de módulos y venta de excedentes.
  - Habilitar módulos del plan; vender módulo adicional (excedente) con precio; deshabilitar
    conservando datos. Endpoints en el panel del Super Admin.
  - _Requisitos: 2.9, 2.11, 2.12, 2.13, 5.B.9, 5.B.10._

- [~] 3.5 CRUD de planes por el Super Admin (sin programación) + precios sugeridos.
  - Planes precargados (Esencial/Profesional/Empresarial) y listado en el panel. El CRUD de edición
    de planes vía API queda pendiente para completar junto al frontend (Etapa 12.6).
  - _Requisitos: 5.B.1, 5.B.7, 5.B.8._

- [ ] 3.6 Facturación de ventas del Super Admin (CFDI o PDF).
  - Se implementa junto con CFDI (Etapa 8), reutilizando el PAC. Registrar `superadmin_sale`.
  - _Requisitos: 5.C.1–5.C.7, 5.B.11, 5.B.12, 5.B.13._

- [ ] 3.7 Notificaciones de vencimiento de prueba.
  - Se implementa junto con el módulo de notificaciones (Etapa 14.1).
  - _Requisitos: 2.4._

- [x] 3.8 Pruebas de licenciamiento y módulos.
  - Dominio (8) + integración del flujo real con PostgreSQL (crear negocio, aprovisionar schema,
    habilitar módulos, comprar licencia, vender excedente). Suite completa: 20/20 en verde.
  - _Requisitos: 2.*, 5.B.*._

---

## Etapa 4 — Catálogos y Motor Multi-Giro

- [ ] 4.1 Cargar catálogo SAT (c_ClaveProdServ, c_ClaveUnidad) en schema admin.
  - _Requisitos: 15.1._

- [ ] 4.2 Catálogo maestro compartido (crece con el uso).
  - `master_product` con clave SAT sugerida; alta desde negocios; sugerencias al dar de alta.
  - _Requisitos: 15.4, 15.5, 15.6._

- [ ] 4.3 Perfiles de giro y campos dinámicos.
  - Perfiles: abarrotes, materias primas, panadería, pollería; atributos JSONB por producto.
  - _Requisitos: 5.1, 5.2, 5.3, 5.5._

- [ ] 4.4 Catálogo semilla por giro + carga al crear negocio.
  - Semilla curada de productos comunes; opción de cargarla según el giro.
  - _Requisitos: 15.2, 15.3._

- [ ] 4.5 Alta de giro nuevo por configuración (sin código).
  - _Requisitos: 5.4._

- [ ] 4.6 Productos, categorías, precios y códigos de barras.
  - `product`, `product_barcode`, `category`, `price`.
  - _Requisitos: 6.1 (soporte), 15.*._

- [ ] 4.7 Pruebas de catálogo y giros.
  - _Requisitos: 5.*, 15.*._

---

## Etapa 5 — Inventario

- [ ] 5.1 Modelo de inventario por sucursal y kardex.
  - `branch`, `inventory_stock`, `inventory_movement`.
  - _Requisitos: 7.1, 7.6._

- [ ] 5.2 Lotes, series y caducidades.
  - `lot`, `serial`; alertas de caducidad.
  - _Requisitos: 7.4, 7.5._

- [ ] 5.3 Traspasos entre sucursales y alertas de stock mínimo.
  - _Requisitos: 7.2, 7.3._

- [ ] 5.4 Ajustes de inventario con motivo y auditoría.
  - _Requisitos: 7.7._

- [ ] 5.5 Puerto `InventoryPort` para consumo desde ventas/compras.
  - _Requisitos: 6.8, 7.1._

- [ ] 5.6 Pruebas de inventario.
  - _Requisitos: 7.*._

---

## Etapa 6 — Ventas (POS), Cortes de Caja y Offline

- [ ] 6.1 Modelo de venta, líneas, pagos y promociones.
  - `sale`, `sale_line`, `payment`, `promotion`.
  - _Requisitos: 6.3, 6.7._

- [ ] 6.2 Registrar venta con código de barras y báscula (soporte de datos).
  - Descuento de inventario transaccional; pagos mixtos.
  - _Requisitos: 6.1, 6.3, 6.4, 6.8._

- [ ] 6.3 Devoluciones, cancelaciones, cotizaciones y apartados.
  - Cancelación con autorización de supervisor (usa autz Etapa 2).
  - _Requisitos: 6.6._

- [ ] 6.4 Multi-caja y multi-sucursal.
  - `cash_register`; asociación a sucursal.
  - _Requisitos: 6.9._

- [ ] 6.5 Cortes de caja, arqueo y turnos.
  - `shift`; apertura con fondo, cierre con arqueo, corte Z, entradas/salidas de efectivo.
  - _Requisitos: 13.1–13.5._

- [ ] 6.6 Sincronización offline con idempotencia (backend).
  - Aceptar ventas encoladas con idempotency key; deduplicar; validar stock al sincronizar.
  - _Requisitos: 6.2, 6.10._

- [ ] 6.7 Pruebas de ventas, cortes y offline.
  - _Requisitos: 6.*, 13.*._

---

## Etapa 7 — Impresión de Tickets (ESC/POS)

- [ ] 7.1 Generación de comandos ESC/POS y puerto `PrintingPort`.
  - _Requisitos: 12.1._

- [ ] 7.2 Agente de impresión local (app ligera) y protocolo local.
  - Impresión silenciosa; USB/red/Bluetooth; cajón de dinero; corte de papel.
  - _Requisitos: 12.2, 12.3, 12.5, 12.6._

- [ ] 7.3 Diseñador visual de tickets y perfiles por sucursal/caja.
  - `ticket_template`, `printer_profile`; prueba de impresión.
  - _Requisitos: 12.4, 12.6, 12.7._

- [ ] 7.4 Pruebas de impresión (comandos y plantillas).
  - _Requisitos: 12.*._

---

## Etapa 8 — Facturación CFDI 4.0 del Negocio

- [ ] 8.1 Puerto `CfdiStampingPort` y adaptador del PAC.
  - _Requisitos: 10.1._

- [ ] 8.2 Emisión de CFDI 4.0 con campos obligatorios y almacenamiento XML/PDF.
  - _Requisitos: 10.1, 10.2, 10.7._

- [ ] 8.3 Complemento de pagos y cancelación conforme al SAT.
  - _Requisitos: 10.3, 10.4._

- [ ] 8.4 Factura global y portal de autofacturación.
  - _Requisitos: 10.5, 10.6._

- [ ] 8.5 Reintento idempotente ante fallo de timbrado.
  - _Requisitos: 10.8._

- [ ] 8.6 Pruebas de CFDI (con sandbox del PAC).
  - _Requisitos: 10.*._

---

## Etapa 9 — Compras/Proveedores y Clientes/CRM

- [ ] 9.1 Proveedores, órdenes de compra, recepción y cuentas por pagar.
  - _Requisitos: 8.1–8.4._

- [ ] 9.2 Sugerencia de compra por stock mínimo (base para BI).
  - _Requisitos: 8.5._

- [ ] 9.3 Clientes, cuentas por cobrar y crédito con límite.
  - _Requisitos: 9.1, 9.2, 9.4, 9.5._

- [ ] 9.4 Lealtad / monedero electrónico.
  - _Requisitos: 9.3._

- [ ] 9.5 Pruebas de compras y clientes.
  - _Requisitos: 8.*, 9.*._

---

## Etapa 10 — Recargas y Pago de Servicios

- [ ] 10.1 Puerto `RechargeProviderPort` y adaptador del agregador.
  - _Requisitos: 11.1, 11.2._

- [ ] 10.2 Pago de servicios y registro de comisiones.
  - No cobrar si falla; comprobante; registro para conciliación.
  - _Requisitos: 11.3, 11.4, 11.5, 11.6._

- [ ] 10.3 Pruebas de recargas y servicios (sandbox).
  - _Requisitos: 11.*._

---

## Etapa 11 — Business Intelligence + IA

- [ ] 11.1 Tablas de agregación / vistas materializadas y dashboards en tiempo real.
  - WebSocket para ventas del día, ticket promedio, top productos, comparativo sucursales.
  - _Requisitos: 14.1._

- [ ] 11.2 Predicción de demanda y sugerencia de compra.
  - _Requisitos: 14.2._

- [ ] 11.3 Análisis ABC y rentabilidad por producto.
  - _Requisitos: 14.3._

- [ ] 11.4 Reportes de mermas, horarios pico y detección de anomalías/fraude.
  - _Requisitos: 14.4, 14.5._

- [ ] 11.5 Exportación (PDF/Excel) con filtros.
  - _Requisitos: 14.6._

- [ ] 11.6 Pruebas de BI.
  - _Requisitos: 14.*._

---

## Etapa 12 — Frontend Web (PWA)

- [ ] 12.1 Base del front: React+Vite+TS, sistema de diseño, layout responsivo, PWA + Service Worker.
  - _Requisitos: 17.1, 17.2, 17.3, 17.5._

- [ ] 12.2 Autenticación, MFA y manejo de sesión en el cliente.
  - _Requisitos: 3.*._

- [ ] 12.3 Pantalla POS optimizada por teclado + escaneo + báscula + offline (IndexedDB, cola).
  - _Requisitos: 6.*, 17.4, 17.5._

- [ ] 12.4 Módulos web: inventario, compras, clientes, cortes de caja, catálogos.
  - _Requisitos: 7.*, 8.*, 9.*, 13.*, 15.*._

- [ ] 12.5 CFDI, recargas/servicios y diseñador de tickets en web.
  - _Requisitos: 10.*, 11.*, 12.4._

- [ ] 12.6 Panel del Super Admin (negocios, planes, módulos, ventas, facturación).
  - _Requisitos: 2.*, 5.B.*, 5.C.*._

- [ ] 12.7 Dashboards de BI en web.
  - _Requisitos: 14.*._

- [ ] 12.8 Pruebas de front (componentes y E2E de flujos críticos).
  - _Requisitos: flujos 6, 10, 13, 2._

---

## Etapa 13 — App Móvil del Dueño (Flutter)

- [ ] 13.1 Base Flutter + BFF `mobile-api` + autenticación segura (JWT/MFA).
  - _Requisitos: 16.1, 16.5._

- [ ] 13.2 Dashboards de BI y ventas en tiempo real en la app.
  - _Requisitos: 16.2, 16.4._

- [ ] 13.3 Notificaciones push (corte de caja, stock, ventas).
  - _Requisitos: 16.3._

- [ ] 13.4 Pruebas de la app.
  - _Requisitos: 16.*._

---

## Etapa 14 — Integración, Despliegue y Verificación Final

- [ ] 14.1 Adaptadores de infraestructura (correo, push, almacenamiento) y gestor de secretos.
  - _Requisitos: 8 integraciones, 3.6._

- [ ] 14.2 Despliegue inicial de bajo costo (DB gestionada, backend, front, storage).
  - _Requisitos: 19.3._

- [ ] 14.3 Observabilidad (logs, métricas, trazas) y respaldos con prueba de restauración.
  - _Requisitos: 19.*, 18.*._

- [ ] 14.4 Pruebas E2E completas multi-tenant y de seguridad; verificación de aislamiento y licencias.
  - _Requisitos: 1.*, 2.*, 3.*._

- [ ] 14.5 Preparar el paquete inicial para los 4 negocios comprometidos (alta, giro, semilla, licencia).
  - _Requisitos: objetivos de negocio._
