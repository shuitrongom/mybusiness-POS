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

- [x] 4.1 Cargar catálogo SAT (c_ClaveProdServ, c_ClaveUnidad) en schema admin.
  - Tablas `sat_prod_serv` y `sat_unit` con subconjunto de claves de uso común precargado.
    (La importación del catálogo completo de ~50k claves queda como proceso de carga posterior.)
  - _Requisitos: 15.1._

- [x] 4.2 Catálogo maestro compartido (crece con el uso).
  - `master_product` + `MasterProductRepository.contribute` (aporta si es nuevo, cuenta si existe).
    Sugerencia por código de barras al dar de alta. Verificado con integración.
  - _Requisitos: 15.4, 15.5, 15.6._

- [x] 4.3 Perfiles de giro y campos dinámicos.
  - `business_line` + `business_line_field` (abarrotes, materias primas, panadería, pollería).
    Productos con atributos JSONB según el giro. Verificado (caducidad/lote guardados y leídos).
  - _Requisitos: 5.1, 5.2, 5.3, 5.5._

- [x] 4.4 Catálogo semilla por giro + carga al crear negocio.
  - Semilla curada de productos comunes en `master_product`; módulos sugeridos por giro.
  - _Requisitos: 15.2, 15.3._

- [x] 4.5 Alta de giro nuevo por configuración (sin código).
  - Un giro nuevo = filas en `business_line` + `business_line_field` (sin cambios de código).
  - _Requisitos: 5.4._

- [x] 4.6 Productos, categorías y códigos de barras.
  - `product`, `product_barcode`, `category` por tenant con RLS. `CatalogService` y `ProductController`
    (alta, búsqueda, lookup por código de barras). Protegido por módulo `inventory`.
  - _Requisitos: 6.1 (soporte), 15.*._

- [x] 4.7 Pruebas de catálogo y giros.
  - Integración: alta con atributos de giro, lookup por código de barras, sugerencia y aporte al
    maestro. Se corrigió Surefire para incluir los *IT. Suite completa: 27/27 en verde.
  - _Requisitos: 5.*, 15.*._

---

## Etapa 5 — Inventario

- [x] 5.1 Modelo de inventario por sucursal y kardex.
  - `inventory_stock` (existencia por producto/sucursal), `inventory_movement` (kardex) con RLS.
    Existencia y movimiento se actualizan en la misma transacción (consistencia).
  - _Requisitos: 7.1, 7.6._

- [x] 5.2 Lotes y caducidades.
  - Tabla `product_lot` (lote, caducidad, cantidad por producto/sucursal) con RLS e índice de
    caducidad. (Series por número quedan para cuando un giro las requiera; es aditivo.)
  - _Requisitos: 7.4, 7.5._

- [x] 5.3 Traspasos entre sucursales y alertas de stock mínimo.
  - `InventoryService.transfer` (salida+entrada atómica); `lowStockAlerts` (<= mínimo). Verificado.
  - _Requisitos: 7.2, 7.3._

- [x] 5.4 Ajustes de inventario con motivo.
  - `adjust` con motivo, registrado en el kardex como ADJUSTMENT.
  - _Requisitos: 7.7._

- [x] 5.5 Puerto `InventoryPort` para consumo desde ventas/compras.
  - `InventoryPort.applyMovement` implementado por `InventoryService` (contrato para otros módulos).
  - _Requisitos: 6.8, 7.1._

- [x] 5.6 Pruebas de inventario.
  - Integración: compra suma / venta resta / kardex, alerta de mínimo, traspaso entre sucursales.
    Suite completa: 30/30 en verde.
  - _Requisitos: 7.*._

---

## Etapa 6 — Ventas (POS), Cortes de Caja y Offline

- [x] 6.1 Modelo de venta, líneas y pagos.
  - Dominio `Sale`/`SaleLine`/`Payment` con cálculo de totales, cambio y validación de pagos.
    Tablas `sale`/`sale_line`/`sale_payment` por tenant con RLS. (Promociones: Etapa 9/reglas, aditivo.)
  - _Requisitos: 6.3, 6.7._

- [x] 6.2 Registrar venta y descuento de inventario.
  - `SaleService.registerSale` descuenta inventario vía `InventoryPort` en la misma transacción.
    Pagos mixtos y venta por cantidad decimal (soporta báscula). Verificado (100 → 95).
  - _Requisitos: 6.1, 6.3, 6.4, 6.8._

- [~] 6.3 Devoluciones, cancelaciones, cotizaciones y apartados.
  - Cancelación de venta (`voidSale`) implementada. La reposición de inventario por
    cancelación/devolución y cotizaciones/apartados se completan junto con Clientes (Etapa 9).
  - _Requisitos: 6.6._

- [x] 6.4 Multi-caja y multi-sucursal.
  - `cash_register` por sucursal; venta asociada a sucursal/caja/turno. Verificado.
  - _Requisitos: 6.9._

- [x] 6.5 Cortes de caja, arqueo y turnos.
  - `ShiftService`: abrir con fondo, movimientos de efectivo, cierre con arqueo y cálculo de
    diferencia (esperado vs contado por método de pago). Verificado (esperado 600, dif -10).
  - _Requisitos: 13.1–13.5._

- [x] 6.6 Sincronización offline con idempotencia (backend).
  - Clave de idempotencia única por venta; reenvío no duplica ni descuenta inventario doble.
    Verificado (reenvío marca duplicated=true, stock 50 → 48, no 46).
  - _Requisitos: 6.2, 6.10._

- [x] 6.7 Pruebas de ventas, cortes y offline.
  - Dominio (4) + integración (venta descuenta inventario, idempotencia, corte de caja).
    Suite completa: 37/37 en verde.
  - _Requisitos: 6.*, 13.*._

---

## Etapa 7 — Impresión de Tickets (ESC/POS)

- [x] 7.1 Generación de comandos ESC/POS.
  - `EscPosBuilder` (init, alineación, negritas, tamaño, corte, cajón) y `TicketRenderer`
    (formato de columnas por ancho de papel). Compatible con impresoras de marca y genéricas/chinas.
  - _Requisitos: 12.1._

- [x] 7.2 Contrato para el agente de impresión local (protocolo local).
  - `PrintingService` entrega el ESC/POS en base64 para que el agente local lo envíe a la
    impresora (USB/red/Bluetooth) de forma silenciosa. Cajón de dinero y corte incluidos.
    (La app agente en la PC del cajero se entrega junto al frontend/despliegue.)
  - _Requisitos: 12.2, 12.3, 12.5, 12.6._

- [x] 7.3 Plantillas de ticket y perfiles por sucursal/caja + prueba de impresión.
  - Tablas `ticket_template` y `printer_profile` (conexión, ancho, cajón, auto-corte) con RLS.
    Endpoint de ticket configurable y de prueba de impresión.
  - _Requisitos: 12.4, 12.6, 12.7._

- [x] 7.4 Pruebas de impresión (comandos y plantillas).
  - Unitarias: comandos ESC/POS correctos, base64 round-trip, contenido y totales del ticket.
    Suite completa: 40/40 en verde.
  - _Requisitos: 12.*._

---

## Etapa 8 — Facturación CFDI 4.0 del Negocio

- [x] 8.1 Puerto `CfdiStampingPort` y adaptador del PAC.
  - Puerto abstracto del PAC + adaptador SIMULADO (sandbox) activable por `app.cfdi.pac`.
    Al contratar un PAC real se agrega otro adaptador sin tocar dominio ni casos de uso.
  - _Requisitos: 10.1._

- [x] 8.2 Emisión de CFDI 4.0 con campos obligatorios y almacenamiento XML.
  - `ReceiverInfo` valida los campos obligatorios de CFDI 4.0 (RFC, nombre, CP, régimen, uso).
    Se timbra y se guarda UUID + XML. Verificado. (PDF se genera en integración con almacenamiento, Etapa 14.)
  - _Requisitos: 10.1, 10.2, 10.7._

- [~] 8.3 Complemento de pagos y cancelación conforme al SAT.
  - Cancelación implementada y verificada. Tabla de complemento de pagos creada; el flujo de
    complemento se completa junto con cuentas por cobrar (Etapa 9).
  - _Requisitos: 10.3, 10.4._

- [~] 8.4 Factura global y portal de autofacturación.
  - Modelo soporta tipo GLOBAL. El armado de la factura global (agrupar tickets del día) y el
    portal público de autofacturación se completan con el frontend (Etapa 12).
  - _Requisitos: 10.5, 10.6._

- [x] 8.5 Reintento idempotente ante fallo de timbrado.
  - Idempotencia por clave (no re-timbra) y `retryStamp` para reintentar un error sin duplicar.
    Verificado (reenvío marca duplicated=true con mismo UUID).
  - _Requisitos: 10.8._

- [x] 8.6 Pruebas de CFDI (con sandbox del PAC).
  - Integración: emisión+timbrado, idempotencia, cancelación. Suite completa: 43/43 en verde.
    Se adoptó el patrón singleton container (un solo PostgreSQL compartido) para estabilidad.
  - _Requisitos: 10.*._

---

## Etapa 9 — Compras/Proveedores y Clientes/CRM

- [x] 9.1 Proveedores, compras, recepción y cuentas por pagar.
  - `PurchasingService`: alta de proveedor, recepción de compra (aumenta inventario vía
    `InventoryPort`, genera cuenta por pagar si es a crédito), abono a cuentas por pagar.
    Tablas supplier/purchase/purchase_line/account_payable por tenant con RLS. Verificado.
  - _Requisitos: 8.1–8.4._

- [~] 9.2 Sugerencia de compra por stock mínimo (base para BI).
  - Base lista: alertas de stock mínimo (Etapa 5) + costos de compra. La sugerencia automática
    se completa en BI (Etapa 11).
  - _Requisitos: 8.5._

- [x] 9.3 Clientes, cuentas por cobrar y crédito con límite.
  - `CustomerService`: alta, cuenta por cobrar con validación de límite de crédito, abono que
    libera crédito. Verificado (rechaza al exceder, abono libera). Tablas con RLS.
  - _Requisitos: 9.1, 9.2, 9.4, 9.5._

- [x] 9.4 Lealtad / monedero electrónico.
  - Acumular/canjear puntos con historial (`loyalty_account`/`loyalty_movement`); valida saldo.
    Verificado (earn 50, redeem 30, rechaza canje sin saldo).
  - _Requisitos: 9.3._

- [x] 9.5 Pruebas de compras y clientes.
  - Integración: compra→inventario+cuenta por pagar, crédito con límite, lealtad. 46/46 en verde.
    (El módulo de clientes se desarrolló en paralelo por un sub-agente; integrado y verificado por mí.)
  - _Requisitos: 8.*, 9.*._

---

## Etapa 10 — Recargas y Pago de Servicios

- [x] 10.1 Puerto `RechargeProviderPort` y adaptador del agregador.
  - Puerto abstracto del agregador + adaptador SIMULADO (sandbox) activable por
    `app.payments.provider`. Al contratar un agregador real se agrega otro adaptador sin tocar dominio.
  - _Requisitos: 11.1, 11.2._

- [x] 10.2 Pago de servicios y registro de comisiones.
  - `PaymentsService`: recarga y pago de servicio; registra la operación con folio y comisión si
    tiene éxito; si falla, se registra FAILED sin comisión y no se considera cobro (422 en la API).
    Total de comisiones para conciliación. Tabla payment_operation por tenant con RLS.
  - _Requisitos: 11.3, 11.4, 11.5, 11.6._

- [x] 10.3 Pruebas de recargas y servicios (sandbox).
  - Integración: recarga con comisión 3%, servicio con comisión 2%, operación fallida sin comisión,
    total acumula solo éxitos. Suite completa: 50/50 en verde.
  - _Requisitos: 11.*._

---

## Etapa 11 — Business Intelligence + IA

- [x] 11.1 Dashboards de ventas (resumen del día y rankings).
  - `BiService.todaySummary` (ventas, total, ticket promedio, unidades) y `topProducts`.
    Consultas de agregación sobre ventas del tenant. (El push en tiempo real por WebSocket se
    conecta con el frontend en Etapa 12/13; los datos ya están disponibles vía API.)
  - _Requisitos: 14.1._

- [x] 11.2 Predicción de demanda y sugerencia de compra.
  - `AnalyticsService`: demanda diaria promedio del histórico y sugerencia de compra que cubre
    mínimo + demanda del horizonte. Verificado (producto bajo mínimo genera sugerencia > 0).
  - _Requisitos: 14.2._

- [x] 11.3 Análisis ABC y rentabilidad por producto.
  - `abcAnalysis` (clasificación A/B/C por Pareto de ingreso) y utilidad por producto (ingreso-costo).
    Verificado (producto dominante clasificado como A tras corregir el umbral acumulado).
  - _Requisitos: 14.3._

- [x] 11.4 Detección de anomalías/fraude en caja.
  - `cashAnomalies`: cajeros con proporción atípica de cancelaciones sobre un umbral. Base para
    revisar mermas/fraude. (Reportes de horarios pico se añaden con los reportes del frontend.)
  - _Requisitos: 14.4, 14.5._

- [~] 11.5 Exportación (PDF/Excel) con filtros.
  - Los datos y filtros están en la API; la exportación a PDF/Excel se genera desde el frontend
    (Etapa 12) reutilizando estos endpoints.
  - _Requisitos: 14.6._

- [x] 11.6 Pruebas de BI.
  - Integración end-to-end: se generan ventas reales y se verifican dashboard, ranking, ABC y
    sugerencia de compra. Suite completa: 53/53 en verde.
  - _Requisitos: 14.*._

---

## Etapa 12 — Frontend Web (PWA)

- [x] 12.1 Base del front: React+Vite+TS, sistema de diseño, layout responsivo, PWA + Service Worker.
  - Proyecto React 18 + Vite 5 + TypeScript (strict), sistema de diseño enterprise (theme.css con
    tokens), layout con barra lateral y marca, responsivo. PWA (manifest + service worker) generada.
    Build de producción en verde.
  - _Requisitos: 17.1, 17.2, 17.3, 17.5._

- [x] 12.2 Autenticación y manejo de sesión en el cliente.
  - `LoginPage` contra el endpoint del Super Admin, store de sesión (zustand), cliente axios con
    Bearer y manejo de 401. Campo de MFA incluido en el login.
  - _Requisitos: 3.*._

- [x] 12.3 Pantalla POS con escaneo por código de barras y offline (cola + idempotencia).
  - `PosPage`: escaneo/Enter, carrito, cobro; si no hay conexión encola la venta (localStorage) y
    sincroniza al reconectar con idempotencyKey (sin duplicados). Optimizada para teclado.
  - _Requisitos: 6.*, 17.4, 17.5._

- [x] 12.7 Dashboards de BI en web.
  - `DashboardPage`: métricas del día (ventas, total, ticket promedio, unidades) y top productos.
  - _Requisitos: 14.*._

- [x] 12.4 Módulos web: inventario, compras, clientes, cortes de caja, catálogos.
  - `ProductsPage`, `InventoryPage` (ajustes + alertas), `PurchasingPage` (proveedores + recepción),
    `CustomersPage` (alta, lealtad, puntos), `CashierPage` (turnos + arqueo con corte). Todas
    consumiendo la API real. Build en verde.
  - _Requisitos: 7.*, 8.*, 9.*, 13.*, 15.*._

- [x] 12.5 CFDI y recargas/servicios en web.
  - `PaymentsPage` (recargas y servicios con comisiones) e `InvoicingPage` (emitir/cancelar CFDI 4.0).
    (El diseñador visual de tickets queda como refinamiento posterior; la impresión ya funciona.)
  - _Requisitos: 10.*, 11.*, 12.4._

- [x] 12.6 Panel del Super Admin (negocios, planes, licencias).
  - `SuperAdminPage`: lista de negocios con estado, alta con plan y meses de prueba (muestra precio
    sugerido), y acciones (vender licencia, suspender, reactivar). Consume la API real.
  - Se añadió `SuperAdminSeeder` (crea el Super Admin inicial al arrancar) para usabilidad día 1.
  - Se corrigió un bug de doble registro del filtro JWT (los endpoints protegidos daban 401);
    cubierto ahora por `AuthFlowIT` (login → 401 sin token → 200 con token). Verificado E2E real.
  - (La facturación de ventas del Super Admin CFDI/PDF queda pendiente, reusará el PAC de Etapa 8.)
  - _Requisitos: 2.*, 5.B.*._

- [ ] 12.8 Pruebas de front (componentes y E2E de flujos críticos).
  - Vitest configurado; faltan las pruebas de componentes.
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
