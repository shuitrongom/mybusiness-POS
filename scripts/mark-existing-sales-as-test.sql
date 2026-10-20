-- =====================================================================
-- MyBusiness Silva / PuntoNube — SCRIPT RETROACTIVO: marcar ventas y cortes
-- EXISTENTES de 'Panaderia El Rey' (schema tenant_9) como VENTA DE PRUEBA.
--
-- QUÉ HACE
--   Marca is_test = true en las ventas (sale) y cortes (cash_cut) ya
--   registrados del tenant_9, para que el histórico de PRÁCTICA quede separado
--   del histórico REAL en los reportes. Opción B: NO borra nada, NO resetea
--   inventario/cortes/stock; solo cambia la marca de reporte.
--
-- CUÁNDO / QUIÉN
--   Lo corre A MANO el DUEÑO (o quien administre la base) en Neon, una sola vez,
--   contra el schema tenant_9. NO es una migración Flyway (es un ajuste de datos
--   puntual, no un cambio de esquema).
--
-- IMPORTANTE (irreversible-en-el-flag)
--   El flag is_test no se revierte automáticamente. Una vez marcadas como
--   prueba, estas ventas quedan fuera de los reportes "Histórico real" salvo que
--   alguien las revierta a mano (UPDATE ... SET is_test = false). Por eso el
--   COMMIT va COMENTADO: primero revisa el conteo, confirma, y descomenta.
--
-- REQUISITO
--   La migración tenant V41 (sale.is_test / cash_cut.is_test) ya debe estar
--   aplicada en tenant_9. Si no, estas columnas no existen y el script falla.
-- =====================================================================

-- Contexto del tenant: search_path + variable de RLS para que las sentencias
-- vean y puedan tocar las tablas del schema tenant_9.
SET search_path TO tenant_9, admin;
SET app.current_tenant = 'tenant_9';

-- ---------------------------------------------------------------------
-- (1) CONTEO PREVIO — cuántas filas se marcarían. Revisa estos números ANTES
--     de aplicar nada. Si no cuadran con lo esperado, DETENTE.
-- ---------------------------------------------------------------------
SELECT count(*) AS ventas_a_marcar
FROM tenant_9.sale
WHERE is_test = false;

SELECT count(*) AS cortes_a_marcar
FROM tenant_9.cash_cut
WHERE is_test = false;

-- ---------------------------------------------------------------------
-- (2) APLICACIÓN — dentro de una transacción. Revisa el conteo de arriba,
--     ejecuta los UPDATE, verifica, y SOLO ENTONCES descomenta el COMMIT.
--
--     Punto de corte: se marcan TODAS las ventas/cortes existentes al momento
--     de correr el script, porque el modo prueba sigue ACTIVO hasta que el
--     dueño apriete "Finalizar modo prueba". Si se quisiera acotar por fecha
--     (p. ej. solo hasta el fin del jueves), agrega el filtro comentado abajo:
--         AND created_at < 'YYYY-MM-DD'::timestamptz
-- ---------------------------------------------------------------------
BEGIN;

UPDATE tenant_9.sale
SET is_test = true
WHERE is_test = false;
--  AND created_at < 'YYYY-MM-DD'::timestamptz   -- (opcional) acotar por fecha

UPDATE tenant_9.cash_cut
SET is_test = true
WHERE is_test = false;
--  AND created_at < 'YYYY-MM-DD'::timestamptz   -- (opcional) acotar por fecha

-- Verifica que ya no queden filas reales que debieran ser prueba (debe dar 0/0
-- si marcaste todo, o el remanente esperado si acotaste por fecha).
SELECT
    (SELECT count(*) FROM tenant_9.sale     WHERE is_test = false) AS ventas_reales_restantes,
    (SELECT count(*) FROM tenant_9.cash_cut WHERE is_test = false) AS cortes_reales_restantes;

-- Revisa el resultado. Si todo está bien, descomenta COMMIT. Si algo no cuadra,
-- usa ROLLBACK para deshacer SIN aplicar nada.
-- COMMIT;
-- ROLLBACK;
