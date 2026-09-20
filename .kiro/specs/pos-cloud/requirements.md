# Documento de Requisitos — POS en la Nube (SaaS Multi-Empresa)

## Introducción

Este documento define los requisitos para un sistema de Punto de Venta (POS) en la nube,
tipo SaaS multi-empresa (multi-tenant), orientado al mercado mexicano. El objetivo es superar
a MyBusiness POS en modalidades, diseño y seguridad, ofreciendo un sistema 100% en la nube,
multi-giro, responsivo, con facturación electrónica CFDI 4.0, Business Intelligence avanzado,
y una app móvil premium para el dueño del negocio.

### Objetivos de negocio

- Vender el sistema a PyMEs mexicanas (pequeñas y medianas) bajo modelo de **licencia definitiva
  (pago único) por módulos**, con un periodo de prueba previo. No se venderá por suscripción mensual,
  ya que a las pequeñas empresas no les conviene una mensualidad. Los módulos adicionales que el cliente
  desee después se venden como excedente (pago único adicional).
- Arrancar con 4 negocios ya comprometidos, con giros iniciales: abarrotes, materias primas,
  panadería y pollería, preparado para cualquier giro sin reprogramar.
- Comenzar con infraestructura de bajo costo (casi gratuita) y escalar a infraestructura de pago
  conforme crezcan las ventas, sin necesidad de reprogramar (gracias a la arquitectura hexagonal).

### Decisiones de arquitectura acordadas

- **Backend:** Java Spring Boot, arquitectura hexagonal (puertos y adaptadores), seguridad reforzada (OWASP).
- **Frontend web:** Responsivo + PWA con modo offline. Front y back separados.
- **App móvil (dueño):** Flutter (diseño premium enterprise).
- **Base de datos:** PostgreSQL con estrategia **schema-por-empresa** (aislamiento robusto y económico),
  reforzado con **Row-Level Security (RLS)**. Migrable a base de datos dedicada por empresa sin reprogramar.
- **Mercado:** México (CFDI 4.0 / SAT desde la v1).

### Convención de criterios de aceptación (EARS)

Los criterios de aceptación usan el formato EARS:
"CUANDO [evento/condición] EL SISTEMA DEBERÁ [comportamiento esperado]".

---

## Requisito 1: Gestión Multi-Empresa (Multi-Tenancy)

**Historia de usuario:** Como proveedor del SaaS, quiero que cada empresa cliente tenga sus datos
completamente aislados, para garantizar privacidad, seguridad y cumplimiento, y poder migrar una
empresa a infraestructura dedicada sin reprogramar.

### Criterios de aceptación

1. CUANDO se crea una empresa nueva EL SISTEMA DEBERÁ aprovisionar un schema PostgreSQL dedicado para esa empresa.
2. CUANDO un usuario realiza cualquier operación EL SISTEMA DEBERÁ resolver el tenant (empresa) desde el token de sesión y acceder únicamente a los datos de ese tenant.
3. CUANDO se ejecuta una consulta a la base de datos EL SISTEMA DEBERÁ aplicar Row-Level Security como segunda barrera, de modo que una empresa nunca pueda leer datos de otra aunque una consulta omita el filtro.
4. CUANDO se requiera migrar una empresa a una base de datos dedicada EL SISTEMA DEBERÁ permitir cambiar la configuración de conexión de ese tenant sin modificar la lógica de negocio ni el resto del sistema.
5. SI un usuario intenta acceder a datos de una empresa distinta a la suya EL SISTEMA DEBERÁ denegar el acceso y registrar el intento en la auditoría.
6. CUANDO se resuelve el tenant EL SISTEMA DEBERÁ soportar identificación por subdominio, encabezado o claim del JWT.

---

## Requisito 2: Licenciamiento y Periodo de Prueba

**Historia de usuario:** Como Super Admin, quiero crear negocios con periodo de prueba o con licencia
definitiva, y que al vencer la prueba se bloquee el acceso, para controlar la comercialización del sistema.

### Criterios de aceptación

1. CUANDO el Super Admin crea un negocio EL SISTEMA DEBERÁ permitir elegir entre "periodo de prueba" (con duración configurable, ej. 1 mes) o "licencia definitiva".
2. CUANDO un negocio está en periodo de prueba y la fecha de vencimiento llega EL SISTEMA DEBERÁ bloquear el acceso de todos los usuarios de ese negocio.
3. CUANDO un usuario de un negocio con licencia vencida intenta iniciar sesión EL SISTEMA DEBERÁ mostrar un mensaje indicando que contacte al administrador para adquirir la licencia definitiva.
4. CUANDO falten pocos días para el vencimiento de la prueba EL SISTEMA DEBERÁ enviar notificaciones de aviso al dueño del negocio.
5. CUANDO el Super Admin convierte una prueba en licencia definitiva EL SISTEMA DEBERÁ restaurar el acceso completo del negocio.
6. EL SISTEMA DEBERÁ permitir al Super Admin listar, ver estado, suspender y reactivar cualquier negocio.
7. SI una licencia definitiva es suspendida por el Super Admin EL SISTEMA DEBERÁ bloquear el acceso conservando los datos del negocio.
8. CUANDO se registra cualquier cambio de estado de licencia EL SISTEMA DEBERÁ dejar constancia en la auditoría (quién, qué, cuándo).
9. CUANDO el Super Admin crea o edita un negocio EL SISTEMA DEBERÁ permitir habilitar o deshabilitar los módulos que ese negocio podrá usar (venta comercial por módulos o por plan).
10. EL SISTEMA DEBERÁ ofrecer planes comerciales predefinidos (paquetes de módulos) y también la opción de habilitar módulos individuales a la medida por negocio.
11. SI un módulo no está habilitado comercialmente para un negocio EL SISTEMA DEBERÁ ocultarlo por completo para todos los usuarios de ese negocio, sin importar sus permisos internos.
12. CUANDO el Super Admin habilita un módulo adicional a un negocio ya existente (venta adicional / upgrade) EL SISTEMA DEBERÁ activarlo sin afectar los datos ni la operación en curso.
13. CUANDO el Super Admin deshabilita un módulo previamente vendido EL SISTEMA DEBERÁ conservar los datos generados por ese módulo y solo restringir su acceso.
14. EL SISTEMA DEBERÁ registrar en la auditoría cada habilitación o deshabilitación comercial de módulos por negocio.

**Nota sobre las tres capas de control de módulos (para evitar confusión):**
- **Capa comercial (Super Admin):** define qué módulos compró/tiene habilitado cada negocio.
- **Capa de giro:** ajusta valores por defecto y sugerencias según el tipo de negocio.
- **Capa de permisos (Dueño/Admin del negocio):** define qué usuario interno puede ver cada módulo ya habilitado.
Un módulo solo es visible para un usuario si está habilitado comercialmente Y el rol del usuario tiene permiso.

---

## Requisito 3: Autenticación, Autorización y Seguridad

**Historia de usuario:** Como proveedor del SaaS, quiero seguridad reforzada en todos los frentes,
para que el sistema no sea vulnerable a hackeos ni fugas de datos entre empresas.

### Criterios de aceptación

1. CUANDO un usuario inicia sesión EL SISTEMA DEBERÁ autenticarlo mediante credenciales con contraseña almacenada con hashing Argon2id.
2. CUANDO se emite una sesión EL SISTEMA DEBERÁ usar JWT firmados de vida corta y refresh tokens en cookies HttpOnly, Secure y SameSite.
3. CUANDO un usuario habilita el doble factor EL SISTEMA DEBERÁ requerir MFA en el inicio de sesión.
4. CUANDO se detectan múltiples intentos fallidos de inicio de sesión EL SISTEMA DEBERÁ aplicar rate limiting y bloqueo temporal de la cuenta.
5. CUANDO se recibe cualquier entrada del usuario EL SISTEMA DEBERÁ validarla y usar consultas parametrizadas para prevenir inyección SQL.
6. EL SISTEMA DEBERÁ cifrar los datos en tránsito (TLS) y los datos sensibles en reposo.
7. CUANDO un usuario realiza una acción EL SISTEMA DEBERÁ verificar que su rol tenga el permiso correspondiente antes de ejecutarla.
8. EL SISTEMA DEBERÁ registrar en un log de auditoría inmutable las acciones sensibles (accesos, cambios de configuración, cancelaciones, movimientos de dinero).
9. EL SISTEMA DEBERÁ seguir las prácticas de OWASP Top 10 en el diseño y la implementación.

---

## Requisito 4: Roles y Permisos

**Historia de usuario:** Como dueño del negocio, quiero definir roles con permisos finos,
para controlar qué puede hacer cada empleado.

### Criterios de aceptación

1. EL SISTEMA DEBERÁ ofrecer roles predefinidos: Super Admin, Dueño, Administrador, Supervisor y Cajero.
2. CUANDO el Dueño o Administrador configura un rol EL SISTEMA DEBERÁ permitir asignar permisos granulares por módulo y acción.
3. CUANDO un Cajero intenta una acción restringida (ej. cancelar una venta) EL SISTEMA DEBERÁ requerir autorización de un Supervisor o denegarla según configuración.
4. EL SISTEMA DEBERÁ permitir crear roles personalizados por empresa.
5. SI un usuario no tiene permiso para un módulo EL SISTEMA DEBERÁ ocultar o deshabilitar ese módulo en la interfaz.

---

## Requisito 5: Motor de Configuración Multi-Giro

**Historia de usuario:** Como proveedor del SaaS, quiero que el giro del negocio sea configuración
y no código, para agregar nuevos giros sin reprogramar ni romper lo existente.

### Criterios de aceptación

1. EL SISTEMA DEBERÁ incluir perfiles de giro preconfigurados para abarrotes, materias primas, panadería y pollería.
2. CUANDO se asigna un perfil de giro a una empresa EL SISTEMA DEBERÁ activar/desactivar módulos y aplicar valores por defecto propios de ese giro.
3. EL SISTEMA DEBERÁ soportar campos dinámicos por producto según el giro (ej. lote y caducidad para abarrotes/panadería; peso para materias primas; etc.).
4. CUANDO se necesita un giro nuevo EL SISTEMA DEBERÁ permitir crearlo mediante configuración de datos, sin cambios en el código base.
5. EL SISTEMA DEBERÁ permitir a una empresa personalizar su configuración de giro sin afectar a otras empresas.

---

## Requisito 5.B: Planes y Venta de Licencia Definitiva por Módulos (Panel del Super Admin)

**Historia de usuario:** Como Super Admin, quiero vender el sistema como licencia definitiva (pago único)
por módulos, con un catálogo de planes prearmados y precio sugerido, ofrecer un periodo de prueba, y
poder agregar módulos adicionales después cobrando un excedente, para adaptarme a pequeñas empresas
a las que no les conviene una mensualidad.

### Modelo de negocio (definición)

- El sistema **NO se vende por suscripción mensual**. La venta es de **licencia definitiva (pago único)** por módulos.
- Se ofrece un **periodo de prueba** configurable antes de la compra.
- Si un cliente quiere un módulo adicional después de comprar, se le **agrega y se le cobra un excedente (pago único adicional)**.

### Criterios de aceptación

1. EL SISTEMA DEBERÁ incluir planes predefinidos (por ejemplo: Esencial, Profesional, Empresarial) como paquetes de módulos con un precio de licencia definitiva sugerido.
2. CUANDO el Super Admin crea un negocio EL SISTEMA DEBERÁ permitir seleccionar un plan predefinido y aplicar automáticamente sus módulos como licencia definitiva.
3. CUANDO el Super Admin selecciona un plan EL SISTEMA DEBERÁ mostrar un precio de licencia sugerido, permitiendo que el Super Admin lo confirme o lo ajuste.
4. CUANDO el Super Admin da de alta un negocio EL SISTEMA DEBERÁ permitir definir la duración del periodo de prueba en meses (0, 1, 2, 3 o el valor que elija) de forma configurable.
5. CUANDO termina el periodo de prueba sin compra EL SISTEMA DEBERÁ bloquear el acceso y mostrar el aviso para contactar al administrador y adquirir la licencia definitiva.
6. CUANDO el Super Admin registra la compra de la licencia definitiva EL SISTEMA DEBERÁ habilitar el acceso permanente a los módulos adquiridos, sin fecha de vencimiento.
7. EL SISTEMA DEBERÁ permitir al Super Admin crear, editar, duplicar y desactivar planes desde su panel, sin necesidad de programación.
8. CUANDO el Super Admin edita un plan EL SISTEMA DEBERÁ permitir elegir qué módulos incluye y su precio de licencia sugerido.
9. EL SISTEMA DEBERÁ permitir vender un plan tal cual o personalizarlo por negocio (agregar o quitar módulos y ajustar el precio para ese cliente).
10. CUANDO el Super Admin agrega un módulo adicional a un negocio que ya compró EL SISTEMA DEBERÁ mostrar un precio de excedente sugerido, permitir ajustarlo, y habilitar el módulo tras registrar la venta, conservando los datos existentes.
11. EL SISTEMA DEBERÁ mantener por cada negocio el historial de la licencia comprada y de los módulos adicionales vendidos (con fecha y monto).
12. EL SISTEMA DEBERÁ registrar en la auditoría la creación y cambios de planes, la asignación de plan y precio a cada negocio, y cada venta de módulo adicional.
13. EL SISTEMA DEBERÁ mostrar al Super Admin un resumen de ventas realizadas (licencias y excedentes) por negocio y por periodo.

### Planes predefinidos sugeridos (licencia definitiva, configurables por el Super Admin)

Estos planes vienen precargados como punto de partida; el Super Admin puede editarlos, cambiar precios,
agregar o crear nuevos. Los precios son sugerencias de referencia en pesos mexicanos y son **pago único
(licencia definitiva)**, no mensualidades.

| Plan | Módulos incluidos | Precio licencia sugerido (pago único) |
|------|-------------------|----------------------------------------|
| **Prueba** | Los módulos del plan elegido, por tiempo limitado (meses configurables) | $0 durante la prueba |
| **Esencial** | Punto de venta, Inventario básico, Impresión de tickets, Clientes básico, Cortes de caja | ~$4,000 – $6,000 |
| **Profesional** | Todo lo de Esencial + Compras y proveedores, CFDI 4.0, Recargas y pago de servicios, Promociones, Reportes | ~$8,000 – $12,000 |
| **Empresarial** | Todo lo de Profesional + Multi-sucursal, Business Intelligence con IA, App móvil del dueño, Lealtad/Monedero, Roles avanzados | ~$15,000 – $25,000 |
| **A la medida** | El Super Admin arma los módulos que quiera y pone el precio | Definido por el Super Admin |

**Módulos adicionales (excedente, pago único):** cuando un cliente ya compró un plan y desea un módulo extra,
el sistema sugiere un precio de excedente por ese módulo (configurable por el Super Admin) y lo habilita al registrar la venta.

---

## Requisito 5.C: Facturación de las Ventas del Super Admin (CFDI o Comprobante PDF)

**Historia de usuario:** Como Super Admin, quiero facturar mis ventas de licencias y módulos:
emitir CFDI ante el SAT cuando el cliente lo pida, o enviar solo un comprobante en PDF al correo del
cliente cuando no requiera factura, para tener orden en mis cobros.

### Criterios de aceptación

1. CUANDO el Super Admin registra una venta (licencia o módulo adicional) EL SISTEMA DEBERÁ preguntar si el cliente requiere factura fiscal.
2. SI el cliente requiere factura EL SISTEMA DEBERÁ capturar los datos fiscales del cliente y emitir el CFDI 4.0 timbrado ante el SAT (a través del PAC).
3. SI el cliente NO requiere factura EL SISTEMA DEBERÁ generar un comprobante en PDF (no fiscal) de la venta.
4. CUANDO se genera un comprobante PDF no fiscal EL SISTEMA DEBERÁ enviarlo al correo electrónico que capture el Super Admin.
5. CUANDO se emite un CFDI EL SISTEMA DEBERÁ almacenar el XML y el PDF, y permitir enviarlos al correo del cliente.
6. EL SISTEMA DEBERÁ registrar cada venta del Super Admin con su tipo de comprobante (CFDI o PDF), monto y fecha, y dejar constancia en la auditoría.
7. SI el timbrado del CFDI falla EL SISTEMA DEBERÁ informar el error y permitir reintentar sin duplicar el comprobante.

---

## Requisito 6: Punto de Venta (Ventas)

**Historia de usuario:** Como cajero, quiero registrar ventas de forma rápida y confiable,
incluso sin internet, para atender a los clientes con agilidad.

### Criterios de aceptación

1. CUANDO el cajero escanea un código de barras EL SISTEMA DEBERÁ agregar el producto correspondiente a la venta.
2. CUANDO no hay conexión a internet EL SISTEMA DEBERÁ permitir continuar registrando ventas en modo offline (PWA) y sincronizarlas automáticamente al reconectar.
3. CUANDO el cajero cobra una venta EL SISTEMA DEBERÁ soportar múltiples métodos de pago (efectivo, tarjeta, transferencia, vales) y pagos mixtos.
4. CUANDO se vende un producto por peso EL SISTEMA DEBERÁ obtener el peso desde la báscula electrónica y calcular el importe.
5. CUANDO el cajero usa atajos de teclado (funciones rápidas) EL SISTEMA DEBERÁ ejecutar las acciones de venta correspondientes.
6. EL SISTEMA DEBERÁ soportar devoluciones, cancelaciones, cotizaciones y apartados.
7. CUANDO aplica una promoción (2x1, descuento por volumen, combo, etc.) EL SISTEMA DEBERÁ calcular automáticamente el precio correcto.
8. CUANDO se completa una venta EL SISTEMA DEBERÁ descontar el inventario y registrar el movimiento.
9. EL SISTEMA DEBERÁ soportar multi-caja y multi-sucursal por empresa.
10. SI una venta se registró offline y luego se sincroniza EL SISTEMA DEBERÁ prevenir duplicados e inconsistencias de inventario.

---

## Requisito 7: Inventario

**Historia de usuario:** Como administrador, quiero controlar el inventario en tiempo real y por sucursal,
para evitar faltantes, mermas y productos caducados.

### Criterios de aceptación

1. CUANDO ocurre una venta, compra, devolución o traspaso EL SISTEMA DEBERÁ actualizar el inventario en tiempo real.
2. EL SISTEMA DEBERÁ mantener inventario independiente por sucursal y permitir traspasos entre sucursales.
3. CUANDO un producto alcanza su stock mínimo EL SISTEMA DEBERÁ generar una alerta.
4. CUANDO un producto tiene control de caducidad y se acerca su fecha límite EL SISTEMA DEBERÁ alertar.
5. EL SISTEMA DEBERÁ soportar control por lotes y por números de serie según el giro.
6. EL SISTEMA DEBERÁ registrar un kardex con el histórico de movimientos por producto.
7. EL SISTEMA DEBERÁ permitir ajustes de inventario con motivo y registro en auditoría.

---

## Requisito 8: Compras y Proveedores

**Historia de usuario:** Como administrador, quiero gestionar compras y proveedores,
para reabastecer el inventario y controlar cuentas por pagar.

### Criterios de aceptación

1. EL SISTEMA DEBERÁ permitir registrar proveedores con sus datos fiscales y de contacto.
2. CUANDO se recibe una compra EL SISTEMA DEBERÁ incrementar el inventario y registrar el costo.
3. EL SISTEMA DEBERÁ generar órdenes de compra y permitir recepción parcial o total.
4. CUANDO una compra es a crédito EL SISTEMA DEBERÁ generar la cuenta por pagar correspondiente.
5. EL SISTEMA DEBERÁ sugerir compras con base en stock mínimo y demanda (soporte de BI).

---

## Requisito 9: Clientes, CRM y Crédito

**Historia de usuario:** Como negocio, quiero administrar clientes, otorgar crédito y premiar lealtad,
para fidelizar y aumentar ventas.

### Criterios de aceptación

1. EL SISTEMA DEBERÁ permitir registrar clientes con sus datos fiscales y de contacto.
2. CUANDO un cliente compra a crédito EL SISTEMA DEBERÁ generar y controlar su cuenta por cobrar.
3. EL SISTEMA DEBERÁ ofrecer programa de lealtad / monedero electrónico con acumulación y canje de puntos o saldo.
4. CUANDO un cliente alcanza su límite de crédito EL SISTEMA DEBERÁ impedir o requerir autorización para nuevas ventas a crédito.
5. EL SISTEMA DEBERÁ registrar el histórico de compras por cliente.

---

## Requisito 10: Facturación Electrónica CFDI 4.0

**Historia de usuario:** Como negocio en México, quiero emitir facturas CFDI 4.0 válidas ante el SAT,
para cumplir con la obligación fiscal y atender a mis clientes.

### Criterios de aceptación

1. CUANDO se emite una factura EL SISTEMA DEBERÁ generar el CFDI 4.0 y timbrarlo a través de un PAC autorizado.
2. EL SISTEMA DEBERÁ incluir los campos obligatorios del CFDI 4.0 (régimen fiscal del receptor, código postal del domicilio fiscal, uso del CFDI, clave de producto/servicio, clave de unidad).
3. CUANDO se recibe un pago de una factura a crédito EL SISTEMA DEBERÁ generar el complemento de pagos.
4. CUANDO se requiere cancelar una factura EL SISTEMA DEBERÁ ejecutar el flujo de cancelación conforme a las reglas del SAT.
5. EL SISTEMA DEBERÁ soportar la facturación global (una factura del total de tickets del público en general).
6. EL SISTEMA DEBERÁ ofrecer un portal de autofacturación donde el cliente factura su ticket con su folio.
7. EL SISTEMA DEBERÁ almacenar el XML y el PDF de cada CFDI y permitir su descarga.
8. SI el timbrado falla EL SISTEMA DEBERÁ informar el error y permitir reintentar sin duplicar el comprobante.

---

## Requisito 11: Recargas y Pago de Servicios

**Historia de usuario:** Como negocio, quiero vender tiempo aire y cobrar recibos de servicios,
para ofrecer más servicios y ganar comisiones (como lo hacen Oxxo y farmacias).

### Criterios de aceptación

1. CUANDO el cajero vende una recarga EL SISTEMA DEBERÁ procesarla a través del agregador (API) y confirmar el resultado.
2. EL SISTEMA DEBERÁ soportar recargas de las principales compañías (Telcel, Movistar, AT&T, y multimarca).
3. CUANDO un cliente paga un servicio (luz, agua, teléfono, TV, gas, etc.) EL SISTEMA DEBERÁ procesar el pago a través del agregador y emitir comprobante.
4. CUANDO una operación de recarga o pago de servicio se completa EL SISTEMA DEBERÁ registrar la comisión ganada.
5. SI la operación con el agregador falla EL SISTEMA DEBERÁ informar el error y no cobrar al cliente.
6. EL SISTEMA DEBERÁ registrar todas las operaciones de recargas y servicios para conciliación.

---

## Requisito 12: Impresión de Tickets

**Historia de usuario:** Como negocio, quiero imprimir tickets en cualquier impresora térmica
(de marca o genérica/china) con un diseño configurable, para operar con el hardware que ya tengo.

### Criterios de aceptación

1. EL SISTEMA DEBERÁ imprimir tickets usando el estándar ESC/POS compatible con impresoras de marca y genéricas/chinas.
2. EL SISTEMA DEBERÁ soportar conexión a la impresora por USB, red (Ethernet/WiFi) y Bluetooth.
3. CUANDO el sistema web necesita imprimir localmente EL SISTEMA DEBERÁ comunicarse con la impresora a través de un agente/puente de impresión local que imprime de forma silenciosa.
4. EL SISTEMA DEBERÁ ofrecer un diseñador visual de tickets (logo, textos, tamaños, datos fiscales) sin requerir programación.
5. CUANDO se cierra una venta en efectivo EL SISTEMA DEBERÁ poder enviar el comando de apertura del cajón de dinero.
6. EL SISTEMA DEBERÁ permitir configurar corte de papel automático y prueba de impresión.
7. EL SISTEMA DEBERÁ permitir perfiles de impresión distintos por sucursal y por caja.

---

## Requisito 13: Cortes de Caja, Arqueo y Turnos

**Historia de usuario:** Como negocio, quiero controlar el efectivo por turno y por caja,
para detectar diferencias y prevenir fraudes.

### Criterios de aceptación

1. CUANDO un cajero abre su turno EL SISTEMA DEBERÁ registrar el fondo de caja inicial.
2. CUANDO un cajero cierra su turno EL SISTEMA DEBERÁ calcular el corte (ventas por método de pago, entradas y salidas de efectivo) y compararlo con el conteo físico (arqueo).
3. SI existe diferencia entre el corte del sistema y el arqueo físico EL SISTEMA DEBERÁ registrarla y alertar.
4. EL SISTEMA DEBERÁ generar corte Z y reportes de corte por caja, turno y sucursal.
5. EL SISTEMA DEBERÁ registrar entradas y salidas de efectivo (retiros, gastos) con motivo.

---

## Requisito 14: Business Intelligence Avanzado con IA

**Historia de usuario:** Como dueño, quiero un BI avanzado con dashboards y predicciones,
para tomar mejores decisiones y ver mi negocio en tiempo real.

### Criterios de aceptación

1. EL SISTEMA DEBERÁ mostrar dashboards en tiempo real: ventas del día, ticket promedio, productos más vendidos y comparativos por sucursal.
2. EL SISTEMA DEBERÁ ofrecer predicción de demanda y sugerencia de compra basada en el histórico de ventas.
3. EL SISTEMA DEBERÁ ofrecer análisis ABC de inventario y rentabilidad por producto.
4. EL SISTEMA DEBERÁ generar reportes de mermas y de horarios pico.
5. EL SISTEMA DEBERÁ detectar patrones anómalos que puedan indicar mermas o fraude en caja.
6. EL SISTEMA DEBERÁ permitir exportar reportes (PDF/Excel) y filtrarlos por rango de fechas, sucursal y categoría.

---

## Requisito 15: Catálogos Precargados y Catálogo Maestro Compartido

**Historia de usuario:** Como cliente nuevo, quiero que el sistema ya tenga productos cargados,
para empezar a vender rápido; y como proveedor del SaaS, quiero que la base crezca con el uso.

### Criterios de aceptación

1. EL SISTEMA DEBERÁ incluir precargado el catálogo oficial del SAT de productos y servicios (c_ClaveProdServ) y de unidades de medida.
2. EL SISTEMA DEBERÁ incluir un catálogo semilla curado de productos comunes de abarrotes, materias primas, panadería y pollería (nombre, categoría, unidad y clave SAT sugerida).
3. CUANDO un negocio se crea EL SISTEMA DEBERÁ ofrecer cargar el catálogo semilla correspondiente a su giro.
4. CUANDO un cajero da de alta un producto no existente (por código de barras) EL SISTEMA DEBERÁ guardarlo en el catálogo maestro compartido para beneficio de futuros clientes.
5. CUANDO un producto ya existe en el catálogo maestro EL SISTEMA DEBERÁ sugerir sus datos al darlo de alta en un negocio.
6. EL SISTEMA DEBERÁ mantener el catálogo de cada empresa aislado, tomando datos del maestro solo como sugerencia.

---

## Requisito 16: Aplicación Móvil del Dueño (Flutter)

**Historia de usuario:** Como dueño, quiero una app móvil premium para monitorear mi negocio
desde donde sea, para tener el control en la palma de mi mano.

### Criterios de aceptación

1. EL SISTEMA DEBERÁ ofrecer una app móvil nativa (Flutter) para iOS y Android con diseño premium enterprise.
2. EL SISTEMA DEBERÁ mostrar en la app los dashboards de BI y ventas en tiempo real.
3. CUANDO ocurre un evento relevante (corte de caja, alerta de stock, venta importante) EL SISTEMA DEBERÁ enviar una notificación push al dueño.
4. EL SISTEMA DEBERÁ permitir al dueño ver reportes y consultar el estado de sus sucursales desde la app.
5. EL SISTEMA DEBERÁ autenticar la app con los mismos estándares de seguridad (JWT, MFA).

---

## Requisito 17: Diseño, Responsividad y Experiencia de Usuario

**Historia de usuario:** Como usuario, quiero una interfaz bonita, moderna y 100% responsiva,
para trabajar cómodamente en PC y que el dueño consulte desde el móvil.

### Criterios de aceptación

1. EL SISTEMA DEBERÁ ser 100% responsivo, optimizado para PC (uso principal) y adaptable a tablet y móvil.
2. EL SISTEMA DEBERÁ ofrecer un diseño moderno, limpio y de nivel enterprise, superior al de la competencia.
3. EL SISTEMA DEBERÁ cumplir criterios de accesibilidad (contraste, navegación por teclado, etiquetas).
4. CUANDO el POS se usa en PC EL SISTEMA DEBERÁ priorizar operación por teclado y velocidad.
5. EL SISTEMA DEBERÁ funcionar como PWA instalable con soporte offline.

---

## Requisito 18: Auditoría y Trazabilidad

**Historia de usuario:** Como administrador, quiero saber quién hizo qué y cuándo,
para tener control y trazabilidad total del sistema.

### Criterios de aceptación

1. CUANDO ocurre una acción sensible (venta, cancelación, cambio de precio, movimiento de dinero, cambio de configuración) EL SISTEMA DEBERÁ registrar usuario, acción, fecha/hora y datos relevantes.
2. EL SISTEMA DEBERÁ impedir la alteración o borrado de los registros de auditoría por usuarios operativos.
3. EL SISTEMA DEBERÁ permitir a roles autorizados consultar y filtrar la auditoría.

---

## Requisito 19: Portabilidad de Infraestructura (Costo Escalable)

**Historia de usuario:** Como proveedor del SaaS, quiero arrancar con infraestructura casi gratuita
y migrar a infraestructura de pago sin reprogramar, para controlar costos según crezcan las ventas.

### Criterios de aceptación

1. EL SISTEMA DEBERÁ aislar la dependencia de infraestructura (base de datos, almacenamiento, colas) detrás de puertos/adaptadores (arquitectura hexagonal).
2. CUANDO se cambia de proveedor de infraestructura EL SISTEMA DEBERÁ requerir solo cambios de configuración/adaptadores, sin tocar la lógica de negocio.
3. EL SISTEMA DEBERÁ poder desplegarse inicialmente en infraestructura de bajo costo (ej. PostgreSQL gestionado gratuito, hosting gratuito de front y back).
4. EL SISTEMA DEBERÁ poder escalar a infraestructura de pago (nube mayor) conservando el mismo código de dominio.
