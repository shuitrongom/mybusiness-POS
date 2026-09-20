# Documento de Diseño — MyBusiness Silva (POS en la Nube)

## Introducción

Este documento describe el diseño técnico del sistema MyBusiness Silva, un Punto de Venta SaaS
multi-empresa para el mercado mexicano. El diseño responde a los requisitos aprobados en
`requirements.md` y establece la arquitectura, la estructura de módulos, el modelo de datos, la
estrategia de multi-tenancy, la seguridad y el despliegue.

### Principios de diseño

1. **Arquitectura hexagonal (puertos y adaptadores):** el dominio (reglas de negocio) no depende de
   frameworks, base de datos ni proveedores externos. Todo lo externo entra por adaptadores.
2. **Multi-tenancy robusto:** aislamiento por schema-por-empresa en PostgreSQL, reforzado con RLS.
   Migrable a base dedicada por cliente cambiando solo configuración.
3. **Seguridad por diseño (OWASP):** autenticación fuerte, autorización por rol y por módulo habilitado,
   auditoría, cifrado.
4. **Modularidad comercial:** cada módulo se puede habilitar/deshabilitar por negocio (venta por módulos).
5. **Portabilidad de infraestructura:** empezar barato, escalar sin reprogramar.
6. **Separación front/back:** APIs REST versionadas; front web (PWA) y app móvil (Flutter) son clientes.

---

## 1. Arquitectura general

### 1.1 Vista de alto nivel

```
┌─────────────────────────────────────────────────────────────────────┐
│                            CLIENTES                                    │
│   Web PWA (React)      App Móvil Dueño (Flutter)    Portal Autofactura │
│   Agente de Impresión Local (ESC/POS)   Panel Super Admin (Web)       │
└───────────────┬───────────────────────────────────┬──────────────────┘
                │ HTTPS / REST + WebSocket           │
┌───────────────▼───────────────────────────────────▼──────────────────┐
│                        API GATEWAY / BFF                               │
│         (Autenticación, rate limiting, resolución de tenant)           │
└───────────────────────────────┬──────────────────────────────────────┘
                                 │
┌────────────────────────────────▼─────────────────────────────────────┐
│                    BACKEND — Spring Boot (Hexagonal)                    │
│                                                                        │
│   Adaptadores de entrada (REST Controllers, WebSocket)                 │
│   ────────────────────────────────────────────────────                │
│   Aplicación (Casos de uso / Servicios de aplicación)                  │
│   ────────────────────────────────────────────────────                │
│   Dominio (Entidades, Value Objects, Reglas, Puertos)                  │
│   ────────────────────────────────────────────────────                │
│   Adaptadores de salida (Persistencia JPA, PAC, Agregador,             │
│                          Correo, Almacenamiento, Notificaciones)       │
└───────────────┬───────────────────────┬───────────────┬──────────────┘
                │                       │               │
        ┌───────▼──────┐        ┌───────▼──────┐  ┌─────▼─────────┐
        │ PostgreSQL   │        │ Servicios     │  │ Almacenamiento│
        │ schema x     │        │ externos:     │  │ de archivos   │
        │ empresa +RLS │        │ PAC (CFDI),   │  │ (XML/PDF/logo)│
        │              │        │ Agregador     │  │               │
        │              │        │ recargas,     │  │               │
        │              │        │ Correo, Push  │  │               │
        └──────────────┘        └───────────────┘  └───────────────┘
```

### 1.2 Estilo arquitectónico

- **Monolito modular hexagonal** en el backend para la v1. Un solo despliegue, pero internamente
  dividido en módulos con fronteras claras. Esto reduce costo y complejidad al inicio, y permite
  extraer módulos a microservicios en el futuro sin reescribir el dominio.
- **APIs REST versionadas** (`/api/v1/...`) + **WebSocket** para tiempo real (dashboards, notificaciones).
- **BFF (Backend for Frontend)** ligero: el gateway adapta respuestas para web y móvil.

### 1.3 Justificación de decisiones

- **Monolito modular vs microservicios:** con 4 clientes iniciales e infraestructura casi gratuita,
  los microservicios agregarían costo operativo y complejidad innecesarios. El monolito modular
  hexagonal da las mismas fronteras lógicas y es extraíble después. (Trade-off aceptado: un solo
  proceso de despliegue al inicio.)
- **Spring Boot:** madurez, seguridad (Spring Security), ecosistema y soporte a JPA/Flyway/RLS.

---

## 2. Estructura hexagonal y módulos

### 2.1 Capas (por cada módulo)

- **domain:** entidades, value objects, eventos de dominio, reglas de negocio, y **puertos** (interfaces).
- **application:** casos de uso (servicios de aplicación) que orquestan el dominio; definen los puertos
  de entrada y consumen puertos de salida.
- **adapters/in:** controladores REST, manejadores WebSocket, consumidores.
- **adapters/out:** implementaciones de puertos de salida (repositorios JPA, clientes de PAC, etc.).

### 2.2 Organización de paquetes (backend)

```
com.mybusinesssilva
├── platform            # transversal (no es un módulo de negocio)
│   ├── tenancy         # resolución de tenant, enrutamiento de datasource, RLS
│   ├── security        # autenticación, JWT, MFA, autorización por rol y módulo
│   ├── audit           # registro de auditoría
│   ├── licensing       # licencias, planes, módulos habilitados, prueba, excedentes
│   ├── billing         # facturación del Super Admin (CFDI o PDF de sus ventas)
│   ├── notifications   # correo y push
│   └── shared          # tipos comunes, errores, utilidades
│
├── catalog             # productos, catálogo SAT, catálogo maestro compartido, giros
├── sales               # punto de venta, ventas, promociones, cortes de caja, turnos
├── inventory           # inventario, lotes, caducidad, kardex, traspasos
├── purchasing          # compras, proveedores, cuentas por pagar
├── customers           # clientes, CRM, crédito, lealtad, cuentas por cobrar
├── invoicing           # CFDI 4.0 del negocio (timbrado, complementos, global, autofactura)
├── payments            # recargas y pago de servicios (agregador)
├── printing            # diseño de tickets, perfiles, protocolo con agente local
├── bi                  # dashboards, reportes, predicción, análisis ABC, detección de anomalías
└── mobile-api          # BFF para la app del dueño (agregación de datos de BI/ventas)
```

Cada módulo de negocio sigue la misma sub-estructura interna:

```
sales
├── domain
│   ├── model           # Sale, SaleLine, Payment, CashRegister, Shift, Promotion...
│   ├── port
│   │   ├── in          # RegisterSaleUseCase, VoidSaleUseCase, CloseShiftUseCase...
│   │   └── out         # SaleRepository, InventoryPort, PrintingPort...
│   └── service         # reglas puras de dominio
├── application         # implementación de casos de uso
└── adapters
    ├── in.rest         # SaleController
    └── out.persistence # SaleJpaRepository, mappers
```

### 2.3 Comunicación entre módulos

- Los módulos se comunican **por puertos** (interfaces), nunca accediendo a las tablas de otro módulo.
- Ejemplo: al cerrar una venta, `sales` invoca el puerto `InventoryPort.decreaseStock(...)`, cuya
  implementación vive en `inventory`. Esto mantiene las fronteras y evita acoplamiento a tablas.
- Para eventos (ej. "venta registrada" que dispara actualización de BI), se usa un **bus de eventos
  de dominio in-process** (Spring Application Events) en la v1; migrable a un broker (ej. colas) después.

---

## 3. Multi-Tenancy (aislamiento por empresa)

### 3.1 Estrategia

- **Schema-por-empresa** dentro de un mismo PostgreSQL: cada negocio tiene su schema (`tenant_<id>`).
- **Row-Level Security (RLS)** como segunda barrera dentro de cada schema y en tablas compartidas.
- Un schema **`public`/`admin`** para datos globales: negocios, licencias, planes, usuarios del Super
  Admin, catálogo SAT y catálogo maestro compartido.

### 3.2 Resolución del tenant

```
Petición HTTP
   │
   ├─ Autenticación (JWT válido)
   │     El JWT incluye: userId, tenantId, roles, módulos habilitados (claims)
   │
   ├─ TenantContext (ThreadLocal / Reactor Context)
   │     Se fija el tenantId de la petición
   │
   ├─ Enrutamiento de conexión
   │     AbstractRoutingDataSource selecciona el schema (search_path = tenant_<id>)
   │
   └─ RLS activo: SET app.current_tenant = <id>  (política que filtra por tenant)
```

- La resolución del tenant vive **solo** en `platform.tenancy`. El resto del código no sabe de tenants.
- Identificación soportada: **subdominio** (`negocio.mybusinesssilva.com`), **encabezado** o **claim JWT**.

### 3.3 Aprovisionamiento de una empresa nueva

1. El Super Admin crea el negocio en el schema `admin`.
2. Un servicio de aprovisionamiento crea el schema `tenant_<id>` y ejecuta las migraciones (Flyway)
   sobre ese schema.
3. Se aplican políticas RLS y se crea el usuario Dueño inicial del negocio.
4. Se asigna plan, módulos habilitados y periodo de prueba.

### 3.4 Migración a base de datos dedicada (sin reprogramar)

- La conexión de cada tenant se resuelve por configuración (`TenantConnectionProvider`).
- Para migrar un cliente a base dedicada: se copia su schema a un nuevo servidor y se cambia solo su
  entrada de configuración de conexión. El dominio y los módulos no cambian.

### 3.5 Justificación

- Schema-por-empresa da aislamiento fuerte a bajo costo (todas las empresas iniciales caben en un
  servidor gratuito/económico). RLS añade defensa en profundidad contra fugas por consultas mal
  filtradas. (Trade-off: mayor cuidado en migraciones multi-schema; se mitiga automatizando Flyway.)

---

## 4. Seguridad

### 4.1 Autenticación

- **Contraseñas** con **Argon2id**.
- **JWT de acceso** de vida corta (ej. 15 min) firmados (RS256).
- **Refresh token** en **cookie HttpOnly, Secure, SameSite=Strict**, rotado en cada uso.
- **MFA** opcional/forzable (TOTP) para roles sensibles (Super Admin, Dueño, Admin).
- **Rate limiting** y bloqueo temporal tras N intentos fallidos.

### 4.2 Autorización (tres capas, según requisitos)

1. **Módulo habilitado comercialmente** para el negocio (licensing) — si no está vendido, no existe.
2. **Giro** — ajusta comportamiento/valores por defecto.
3. **Permiso del rol** del usuario dentro del negocio.

Regla efectiva: `visible = móduloHabilitado(negocio) AND permiso(rol, módulo, acción)`.

- Se implementa con Spring Security + un `PermissionEvaluator` propio que consulta módulos habilitados
  (desde el claim del JWT y/o cache) y permisos del rol.

### 4.3 Protección de datos

- **TLS** en tránsito; **cifrado en reposo** de datos sensibles (ej. datos fiscales, secretos de PAC).
- **Secretos** (llaves de PAC, agregador, correo) en un gestor de secretos / variables de entorno,
  nunca en código ni en base sin cifrar.
- **Validación de entrada** y **consultas parametrizadas** (JPA/consultas preparadas) contra inyección.
- **Auditoría inmutable** (append-only) para acciones sensibles.

### 4.4 Aislamiento entre tenants

- Doble barrera: enrutamiento por schema **+** RLS. Un fallo en una capa no expone datos de otro tenant.

---

## 5. Modelo de datos (alto nivel)

### 5.1 Schema global (`admin`)

- **business** (negocio/tenant): id, nombre, RFC, giro, estado (prueba/activo/suspendido/vencido),
  fechaAltaPrueba, mesesPrueba, fechaCompra, schemaName, tipoConexión.
- **plan**: id, nombre, precioSugerido, activo.
- **plan_module**: plan_id, module_key (módulos incluidos en el plan).
- **module_catalog**: module_key, nombre, descripción, precioExcedenteSugerido.
- **business_module**: business_id, module_key, habilitado, origen (plan/excedente), fechaVenta, monto.
- **superadmin_sale**: id, business_id, tipo (licencia/excedente), monto, tipoComprobante (CFDI/PDF),
  correoEnvío, fecha.
- **superadmin_user**: usuarios del Super Admin.
- **sat_prod_serv** / **sat_unit**: catálogo SAT (c_ClaveProdServ, c_ClaveUnidad).
- **master_product**: catálogo maestro compartido (crece con el uso), con clave SAT sugerida.
- **audit_log_global**: auditoría de acciones globales.

### 5.2 Schema por empresa (`tenant_<id>`)

- **branch** (sucursal), **cash_register** (caja), **shift** (turno).
- **user** (usuarios del negocio), **role**, **role_permission**.
- **product** (con campos dinámicos por giro), **product_barcode**, **category**, **price**.
- **inventory_stock** (por sucursal), **inventory_movement** (kardex), **lot**, **serial**.
- **sale**, **sale_line**, **payment**, **promotion**, **quote** (cotización), **layaway** (apartado).
- **supplier**, **purchase_order**, **purchase**, **account_payable**.
- **customer**, **account_receivable**, **loyalty_account**, **loyalty_movement**.
- **cfdi** (XML/PDF, estado, uuid), **cfdi_payment_complement**, **global_invoice**.
- **recharge_operation**, **service_payment** (recargas y servicios).
- **printer_profile**, **ticket_template**.
- **audit_log** (auditoría del negocio, append-only).

### 5.3 Campos dinámicos por giro

- `product` tiene atributos base + un contenedor **`attributes` (JSONB)** para campos específicos del
  giro (lote/caducidad, peso/granel, etc.), definidos por el **perfil de giro**.
- El perfil de giro (en `admin`) describe qué atributos aplican y sus reglas de validación.

---

## 6. Diseño por módulo (resumen funcional)

### 6.1 Licensing (licencias, planes, módulos)
- Casos de uso: crear negocio con plan y meses de prueba, registrar compra de licencia definitiva,
  vender módulo adicional (excedente), habilitar/deshabilitar módulos, cambiar plan.
- Al vencer la prueba sin compra: cambia estado a "vencido" y bloquea acceso (guard en el gateway).
- Emite eventos para `billing` (registrar venta) y `audit`.

### 6.2 Billing (facturación de las ventas del Super Admin)
- Al registrar una venta: pregunta si requiere CFDI.
  - **Sí:** captura datos fiscales → `invoicing`/PAC emite CFDI 4.0, guarda XML/PDF, envía por correo.
  - **No:** genera comprobante PDF no fiscal y lo envía al correo capturado.
- Registra `superadmin_sale` y audita.

### 6.3 Sales (punto de venta)
- Venta con código de barras, báscula, pagos mixtos, promociones, devoluciones, cotizaciones, apartados.
- **Offline (PWA):** el cliente web encola ventas localmente (IndexedDB) y sincroniza; el backend
  deduplica por **idempotency key** (evita duplicados e inconsistencias de inventario).
- Al confirmar venta: descuenta inventario (puerto), registra pagos, dispara evento para BI e impresión.
- Cortes de caja / arqueo / turnos.

### 6.4 Inventory
- Stock por sucursal en tiempo real, kardex, lotes/series, caducidades, traspasos, alertas de mínimos.
- Consistencia: los movimientos de inventario se aplican en la misma transacción que la venta/compra.

### 6.5 Purchasing / Customers
- Compras, proveedores, cuentas por pagar; clientes, crédito, cuentas por cobrar, lealtad/monedero.

### 6.6 Invoicing (CFDI 4.0 del negocio)
- Timbrado vía PAC, complemento de pagos, cancelación, factura global, portal de autofacturación.
- Guarda XML/PDF; reintento idempotente si el timbrado falla.

### 6.7 Payments (recargas y servicios)
- Adaptador hacia el agregador (API). Confirma resultado, registra comisión, no cobra si falla.

### 6.8 Printing
- **Protocolo ESC/POS** generado en el backend/front y enviado al **agente de impresión local**
  (app ligera en la PC) vía HTTP local/WebSocket. Imprime silencioso, controla cajón y corte.
- Diseñador visual de tickets → guarda `ticket_template`; perfiles por sucursal/caja.

### 6.9 BI
- Dashboards en tiempo real (WebSocket), reportes exportables (PDF/Excel).
- Predicción de demanda y sugerencia de compra (modelos sobre histórico de ventas).
- Análisis ABC, mermas, detección de anomalías/fraude en caja.
- Lee de vistas materializadas / tablas de agregación para no impactar la operación transaccional.

---

## 7. Frontend

### 7.1 Web (PWA)
- **React + Vite + TypeScript**, PWA instalable con **Service Worker** y **IndexedDB** para offline.
- Diseño responsivo, mobile-first en componentes, optimizado para teclado en el POS (atajos F1–F12).
- Sistema de diseño propio (tokens, componentes accesibles) para el look enterprise.
- Estado offline: cola de operaciones + sincronización con idempotency keys.

### 7.2 App móvil del dueño
- **Flutter** (iOS/Android), consume el **mobile-api (BFF)**.
- Dashboards de BI, notificaciones push (FCM/APNs), consulta de sucursales y reportes.
- Misma seguridad (JWT, MFA).

### 7.3 Agente de impresión local
- App ligera (multiplataforma) que expone un endpoint local y traduce a ESC/POS por USB/red/Bluetooth.

---

## 8. Integraciones externas (puertos de salida)

| Integración | Puerto (dominio) | Adaptador (implementación) |
|-------------|------------------|----------------------------|
| Timbrado CFDI | `CfdiStampingPort` | Cliente del PAC autorizado |
| Recargas/servicios | `RechargeProviderPort` | Cliente del agregador (API) |
| Correo | `EmailPort` | Proveedor SMTP/transaccional |
| Notificaciones push | `PushPort` | FCM / APNs |
| Almacenamiento archivos | `FileStoragePort` | Almacenamiento de objetos |
| Impresión | `PrintingPort` | Agente local ESC/POS |

Cambiar de proveedor = cambiar el adaptador, sin tocar dominio ni casos de uso.

---

## 9. Manejo de errores y consistencia

- **Errores de dominio** tipados (excepciones de negocio) → traducidos a respuestas HTTP claras.
- **Transacciones** por caso de uso; venta + inventario + pagos en la misma transacción.
- **Idempotencia** en operaciones sensibles (ventas offline, timbrado, recargas) con claves únicas.
- **Reintentos** con backoff para servicios externos (PAC, agregador), sin duplicar efectos.
- **Sincronización offline:** resolución determinista; el servidor es la fuente de verdad; conflictos
  de stock se validan al sincronizar y se informa al cajero si algo cambió.

---

## 10. Despliegue e infraestructura

### 10.1 Fase inicial (bajo costo)
- **Backend:** contenedor en hosting económico/gratuito (ej. Fly.io/Render/Railway).
- **Base de datos:** PostgreSQL gestionado con plan gratuito/económico (ej. Neon/Supabase).
- **Front web:** hosting estático/CDN gratuito (ej. Vercel/Netlify).
- **Almacenamiento de archivos:** bucket de objetos económico.
- **App móvil:** distribución en tiendas.

### 10.2 Escalado (sin reprogramar)
- Migrar backend a la nube mayor, base a instancia dedicada, y tenants grandes a base propia.
- Como todo lo externo está detrás de puertos/adaptadores y configuración, el código de dominio no cambia.

### 10.3 Observabilidad y respaldos
- Logs estructurados, métricas, trazas.
- **Respaldos** automáticos de base de datos y de archivos; pruebas de restauración.

---

## 11. Estrategia de pruebas (verificación enterprise)

- **Pruebas unitarias** del dominio (reglas puras, sin frameworks).
- **Pruebas de casos de uso** (application) con dobles de prueba para puertos.
- **Pruebas de integración** de adaptadores (persistencia, RLS, PAC/agregador con sandbox).
- **Pruebas de aislamiento multi-tenant** (asegurar que un tenant no ve datos de otro).
- **Pruebas de seguridad** (autz por rol y módulo, autenticación, RLS).
- **Pruebas E2E** de flujos críticos (venta, corte de caja, timbrado, alta de negocio).
- **Verificación por etapas:** cada módulo se compila, prueba y valida antes de avanzar.

---

## 12. Orden de construcción propuesto (para la fase de tareas)

Se construirá de forma incremental y verificada, empezando por los cimientos:

1. **Plataforma base:** proyecto Spring Boot hexagonal, tenancy (schema + RLS), seguridad (auth/JWT/roles),
   auditoría, migraciones Flyway.
2. **Licensing + Billing:** negocios, planes, módulos, prueba, licencia definitiva, excedentes,
   facturación del Super Admin (CFDI/PDF). Panel Super Admin.
3. **Catálogo + Giros:** catálogo SAT, catálogo maestro, perfiles de giro, productos con campos dinámicos.
4. **Inventario.**
5. **Ventas (POS)** con offline, cortes de caja; integración con inventario e impresión.
6. **Impresión** (agente local + diseñador de tickets).
7. **Compras/Proveedores** y **Clientes/CRM/Crédito/Lealtad.**
8. **CFDI 4.0** del negocio (timbrado, complementos, global, autofacturación).
9. **Recargas y pago de servicios.**
10. **BI + IA.**
11. **Frontend web (PWA)** en paralelo por módulo, y **App móvil (Flutter)** del dueño.

Cada etapa entrega algo funcional y verificado antes de pasar a la siguiente.
