-- =====================================================================
-- Semilla de módulos comercializables y planes predefinidos.
-- Precios en pesos mexicanos (MXN), licencia definitiva (pago único).
-- El Super Admin puede editar precios y planes desde su panel.
-- =====================================================================

-- ---- Módulos ----
INSERT INTO admin.module_catalog (module_key, name, description, surcharge_suggested_price) VALUES
    ('sales',        'Punto de venta',            'Ventas, código de barras, báscula, pagos, promociones.', 1500),
    ('inventory',    'Inventario',                'Stock por sucursal, lotes, caducidad, kardex, traspasos.', 1500),
    ('printing',     'Impresión de tickets',      'ESC/POS, diseñador de tickets, cajón de dinero.',          800),
    ('customers',    'Clientes y CRM',            'Clientes, crédito, cuentas por cobrar, lealtad.',          1000),
    ('cash',         'Cortes de caja',            'Turnos, arqueo, corte Z, entradas/salidas de efectivo.',   800),
    ('purchasing',   'Compras y proveedores',     'Órdenes de compra, recepción, cuentas por pagar.',         1200),
    ('invoicing',    'Facturación CFDI 4.0',      'Timbrado, complemento de pagos, factura global, autofactura.', 2500),
    ('payments',     'Recargas y servicios',      'Tiempo aire y pago de servicios con comisión.',            1200),
    ('promotions',   'Promociones avanzadas',     '2x1, descuentos por volumen, combos.',                     800),
    ('reports',      'Reportes',                  'Reportes operativos y exportación.',                       800),
    ('multibranch',  'Multi-sucursal',            'Gestión de varias sucursales y traspasos.',                2000),
    ('bi',           'Business Intelligence + IA','Dashboards, predicción, análisis ABC, detección de fraude.', 3500),
    ('mobile',       'App móvil del dueño',       'App Flutter con reportes y notificaciones push.',          2500),
    ('loyalty',      'Lealtad / monedero',        'Programa de puntos y monedero electrónico.',               1000),
    ('roles',        'Roles avanzados',           'Permisos finos y roles personalizados.',                   800)
ON CONFLICT (module_key) DO NOTHING;

-- ---- Planes ----
INSERT INTO admin.plan (code, name, description, license_price_suggested) VALUES
    ('ESSENTIAL',    'Esencial',    'Lo básico para operar: ventas, inventario, tickets, clientes y cortes de caja.', 5000),
    ('PROFESSIONAL', 'Profesional', 'Esencial + compras, CFDI, recargas, promociones y reportes.',                    10000),
    ('ENTERPRISE',   'Empresarial', 'Profesional + multi-sucursal, BI con IA, app móvil, lealtad y roles avanzados.', 20000)
ON CONFLICT (code) DO NOTHING;

-- ---- Módulos por plan ----
-- Esencial
INSERT INTO admin.plan_module (plan_id, module_key)
SELECT p.id, m.module_key
FROM admin.plan p
CROSS JOIN (VALUES ('sales'),('inventory'),('printing'),('customers'),('cash')) AS m(module_key)
WHERE p.code = 'ESSENTIAL'
ON CONFLICT DO NOTHING;

-- Profesional (incluye lo de Esencial + extras)
INSERT INTO admin.plan_module (plan_id, module_key)
SELECT p.id, m.module_key
FROM admin.plan p
CROSS JOIN (VALUES
    ('sales'),('inventory'),('printing'),('customers'),('cash'),
    ('purchasing'),('invoicing'),('payments'),('promotions'),('reports')
) AS m(module_key)
WHERE p.code = 'PROFESSIONAL'
ON CONFLICT DO NOTHING;

-- Empresarial (incluye lo de Profesional + extras)
INSERT INTO admin.plan_module (plan_id, module_key)
SELECT p.id, m.module_key
FROM admin.plan p
CROSS JOIN (VALUES
    ('sales'),('inventory'),('printing'),('customers'),('cash'),
    ('purchasing'),('invoicing'),('payments'),('promotions'),('reports'),
    ('multibranch'),('bi'),('mobile'),('loyalty'),('roles')
) AS m(module_key)
WHERE p.code = 'ENTERPRISE'
ON CONFLICT DO NOTHING;
