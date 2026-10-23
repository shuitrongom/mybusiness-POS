package com.mybusinesssilva.sales.application;

import com.mybusinesssilva.inventory.application.InventoryService;
import com.mybusinesssilva.inventory.bakery.domain.model.WasteOrigin;
import com.mybusinesssilva.inventory.bakery.domain.model.WasteSummary;
import com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase;
import com.mybusinesssilva.inventory.domain.model.StockLevel;
import com.mybusinesssilva.inventory.domain.port.out.InventoryRepository;
import com.mybusinesssilva.platform.audit.AuditService;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import com.mybusinesssilva.platform.util.DeviceDetector;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del MODO PRUEBA del negocio (ventas de práctica, Opción B).
 *
 * <p>
 * Lee/actualiza el flag {@code admin.business.test_mode} del tenant en curso y
 * audita cada cambio (quién, cuándo, desde qué dispositivo) en
 * {@code admin.audit_log_global}. Opción B: activar/desactivar el modo NUNCA
 * toca datos operativos (no borra ventas, no resetea inventario ni cortes); solo
 * cambia el flag que decide si las ventas nuevas nacen {@code is_test = true}.
 */
@Service
public class TestModeService {

    private final JdbcClient jdbc;
    private final AuditService auditService;
    private final InventoryService inventoryService;
    private final InventoryRepository inventoryRepository;
    private final BakeryUseCase bakeryUseCase;

    public TestModeService(JdbcClient jdbc, AuditService auditService,
            InventoryService inventoryService, InventoryRepository inventoryRepository,
            BakeryUseCase bakeryUseCase) {
        this.jdbc = jdbc;
        this.auditService = auditService;
        this.inventoryService = inventoryService;
        this.inventoryRepository = inventoryRepository;
        this.bakeryUseCase = bakeryUseCase;
    }

    /**
     * Estado actual del modo prueba del negocio en curso, con la bitácora reciente
     * de cambios (quién/cuándo/dispositivo).
     */
    public TestModeStatus getStatus() {
        BusinessRow business = loadBusiness();
        List<AuditEvent> recent = recentChanges(business.id());
        return new TestModeStatus(business.testMode(), business.id(), recent);
    }

    /**
     * Estado LIGERO (solo el flag) para que cualquier usuario del tenant —incluida
     * la cajera— pueda pintar el banner de "modo prueba" sin exponer la bitácora.
     */
    public boolean isActive() {
        return loadBusiness().testMode();
    }

    /**
     * Activa o desactiva el modo prueba del negocio en curso y audita el cambio.
     * Opción B: no toca datos operativos.
     *
     * @param active    nuevo estado del modo prueba
     * @param actor     quién lo cambia (subject del token)
     * @param userAgent encabezado {@code User-Agent} de la petición (para derivar
     *                  el dispositivo); puede ser {@code null}
     * @return el estado actualizado, con la bitácora refrescada
     */
    @Transactional
    public TestModeStatus setActive(boolean active, String actor, String userAgent) {
        BusinessRow business = loadBusiness();
        jdbc.sql("""
                UPDATE admin.business
                SET test_mode = :active
                WHERE schema_name = :schema
                """)
                .param("active", active)
                .param("schema", TenantContext.getTenantId())
                .update();

        String device = DeviceDetector.classify(userAgent);
        auditService.recordGlobal(
                actor,
                active ? "ENABLE_TEST_MODE" : "DISABLE_TEST_MODE",
                "business",
                String.valueOf(business.id()),
                Map.of(
                        "actor", actor == null ? "" : actor,
                        "device", device,
                        "userAgent", userAgent == null ? "" : userAgent,
                        "at", OffsetDateTime.now().toString()));

        return new TestModeStatus(active, business.id(), recentChanges(business.id()));
    }

    /**
     * CORTE PRUEBA→REAL: finaliza el modo prueba e inicia la operación real, de
     * forma atómica, OWNER-only (gate en el controller) e idempotente. En una sola
     * transacción:
     * <ol>
     * <li>Bloquea la fila del negocio ({@code FOR UPDATE}) y lee el estado previo.</li>
     * <li>Cuenta (informativo) las ventas/cortes de práctica que se conservan
     * (Opción B: nada se borra).</li>
     * <li>GUARDA idempotente: si el modo prueba ya está OFF, no re-ejecuta el barrido
     * de merma ni el ajuste de inventario ni audita; lee la merma actual (solo
     * lectura) y devuelve un resumen con {@code executed=false}.</li>
     * <li>Apaga el flag {@code test_mode} del negocio.</li>
     * <li>ENVEJECE el pan remanente: el stock normal VIVO de pan de todas las
     * sucursales ({@code bakeryUseCase.bakeryNormalStock()}, barrido GLOBAL, F-2)
     * pasa a merma con origen {@code ENVEJECIMIENTO} ANTES del ajuste a 0, para no
     * perder como "ajuste" el pan de la etapa de prueba. Reconciliación (F-3): D-4
     * reemplaza deliberadamente la recomendación camino-1 del informe D.3; el
     * no-doble-conteo se cumple por construcción porque solo se pasa a merma el
     * stock_normal remanente VIVO al momento del corte.</li>
     * <li>Lee (sin tocar) el agregado de merma DESPUÉS del barrido, para informar el
     * total final conservado (piezas + $).</li>
     * <li>Lleva a 0 cada existencia vendible (todas las sucursales) vía
     * {@link InventoryService#adjust} — DEJANDO kardex por cada ajuste; nunca un
     * UPDATE silencioso de {@code inventory_stock}.</li>
     * <li>Audita el corte en {@code admin.audit_log_global} (quién/cuándo/dispositivo
     * + resumen).</li>
     * </ol>
     * Cualquier excepción revierte TODO (flag, ajustes, kardex y auditoría).
     *
     * @param actor     quién ejecuta el corte (subject del token)
     * @param userAgent encabezado {@code User-Agent} de la petición (nullable)
     * @return el resumen del corte (histórico conservado, stock reiniciado, merma
     *         arrastrada, bitácora refrescada)
     */
    @Transactional
    public CutoverSummary cutover(String actor, String userAgent) {
        BusinessRow business = loadBusinessForUpdate();
        PracticeSummary practice = countPractice();

        if (!business.testMode()) {
            // Idempotencia: el modo prueba ya está OFF ⇒ no se re-ejecuta el barrido de
            // merma ni el ajuste a 0 ni se audita un nuevo corte. Se lee la merma actual
            // (solo lectura) para informar el resumen con executed=false.
            WasteSummary wasteNow = bakeryUseCase.wasteSummary();
            return new CutoverSummary(false, false, business.id(),
                    practice, StockSummary.empty(), wasteNow, recentChanges(business.id()));
        }

        // Transición true→false la realiza ESTA ejecución (serializada por FOR UPDATE).
        jdbc.sql("UPDATE admin.business SET test_mode = false WHERE schema_name = :schema")
                .param("schema", TenantContext.getTenantId())
                .update();

        // ENVEJECIMIENTO del pan remanente ANTES de reiniciar el inventario: el stock
        // normal VIVO de pan de todas las sucursales pasa a merma (origen ENVEJECIMIENTO)
        // para que la operación real arranque con la merma del pan de la etapa de prueba
        // en su existencia correcta (no se pierde como "ajuste a 0"). F-2: se usa el barrido
        // GLOBAL bakeryNormalStock() (todas las sucursales), no por sucursal.
        //
        // F-3 (reconciliación): D-4 reemplaza DELIBERADAMENTE la recomendación camino-1 del
        // informe D.3. El no-doble-conteo se cumple POR CONSTRUCCIÓN: solo se transfiere a
        // merma el stock_normal remanente VIVO al momento del corte, y el ajuste a 0 que
        // sigue opera sobre el inventario ya descontado por esa transferencia, de modo que
        // ninguna pieza se cuenta dos veces (primero sale de normal→merma, luego el resto
        // de existencias vendibles se lleva a 0).
        for (Map<String, Object> row : bakeryUseCase.bakeryNormalStock()) {
            long productId = ((Number) row.get("productId")).longValue();
            long branchId = ((Number) row.get("branchId")).longValue();
            BigDecimal quantity = (BigDecimal) row.get("quantity");
            if (quantity != null && quantity.signum() > 0) {
                bakeryUseCase.transferToWaste(productId, branchId, quantity,
                        WasteOrigin.ENVEJECIMIENTO, actor, null, null);
            }
        }

        // Lee la merma DESPUÉS del barrido para informar el total final conservado
        // (incluye el pan que acaba de envejecer en este corte).
        WasteSummary waste = bakeryUseCase.wasteSummary();

        // Lleva a 0 cada existencia vendible dejando kardex (ADJUSTMENT) por fila.
        List<StockLevel> pending = inventoryRepository.stockWithBalance();
        List<StockAdjustmentLine> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (StockLevel s : pending) {
            inventoryService.adjust(s.productId(), s.branchId(), s.quantity().negate(),
                    "Cierre de modo prueba: ajuste a 0 para iniciar operación real", actor);
            lines.add(new StockAdjustmentLine(s.productId(), s.branchId(), s.quantity()));
            total = total.add(s.quantity());
        }
        StockSummary stock = new StockSummary(lines.size(), total, lines);

        // Auditoría del corte: 12 pares ⇒ Map.ofEntries (Map.of admite máximo 10).
        auditService.recordGlobal(actor, "TEST_MODE_CUTOVER", "business",
                String.valueOf(business.id()),
                Map.ofEntries(
                        Map.entry("actor", actor == null ? "" : actor),
                        Map.entry("device", DeviceDetector.classify(userAgent)),
                        Map.entry("userAgent", userAgent == null ? "" : userAgent),
                        Map.entry("at", OffsetDateTime.now().toString()),
                        Map.entry("salesKept", practice.salesKept()),
                        Map.entry("cashCutsKept", practice.cashCutsKept()),
                        Map.entry("stockRowsAdjusted", stock.rowsAdjusted()),
                        Map.entry("stockUnitsRemoved", stock.totalUnitsRemoved().toPlainString()),
                        Map.entry("wastePieces", waste.totalPieces().toPlainString()),
                        Map.entry("wasteProducts", waste.productCount()),
                        Map.entry("wasteValue", waste.wasteValue().toPlainString())));

        return new CutoverSummary(true, false, business.id(),
                practice, stock, waste, recentChanges(business.id()));
    }

    /**
     * Carga el negocio del tenant en curso ({@code id} + {@code test_mode}). Si no
     * hay tenant o no existe la fila, lanza error explícito (el endpoint es
     * OWNER-only sobre un tenant real).
     */
    private BusinessRow loadBusiness() {
        String schema = TenantContext.getTenantId();
        if (schema == null || schema.isBlank()) {
            throw new IllegalStateException("No hay negocio en contexto para administrar el modo prueba.");
        }
        return jdbc.sql("""
                SELECT id, COALESCE(test_mode, false) AS test_mode
                FROM admin.business
                WHERE schema_name = :schema
                """)
                .param("schema", schema)
                .query((rs, n) -> new BusinessRow(rs.getLong("id"), rs.getBoolean("test_mode")))
                .optional()
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró el negocio del tenant en curso."));
    }

    /**
     * Igual que {@link #loadBusiness()} pero con {@code SELECT ... FOR UPDATE}: toma
     * un lock de fila sobre {@code admin.business} del tenant para serializar cortes
     * concurrentes (doble clic / doble request). Usado EXCLUSIVAMENTE por
     * {@link #cutover}. No modifica {@link #loadBusiness()} (que no bloquea fila).
     */
    private BusinessRow loadBusinessForUpdate() {
        String schema = TenantContext.getTenantId();
        if (schema == null || schema.isBlank()) {
            throw new IllegalStateException("No hay negocio en contexto para administrar el modo prueba.");
        }
        return jdbc.sql("""
                SELECT id, COALESCE(test_mode, false) AS test_mode
                FROM admin.business
                WHERE schema_name = :schema
                FOR UPDATE
                """)
                .param("schema", schema)
                .query((rs, n) -> new BusinessRow(rs.getLong("id"), rs.getBoolean("test_mode")))
                .optional()
                .orElseThrow(() -> new IllegalStateException(
                        "No se encontró el negocio del tenant en curso."));
    }

    /**
     * Conteo informativo (Opción B) de la práctica que se conserva como histórico:
     * ventas y cortes de caja marcados {@code is_test = true} del tenant en curso.
     * Lectura pura sobre el schema del tenant; no recalcula inventario.
     */
    private PracticeSummary countPractice() {
        long salesKept = jdbc.sql("SELECT COUNT(*) FROM sale WHERE is_test = true")
                .query(Long.class).single();
        long cashCutsKept = jdbc.sql("SELECT COUNT(*) FROM cash_cut WHERE is_test = true")
                .query(Long.class).single();
        return new PracticeSummary(salesKept, cashCutsKept);
    }

    /**
     * Últimos cambios de modo prueba del negocio (bitácora), leídos de
     * {@code admin.audit_log_global}. El dispositivo/UA viven en {@code details}
     * (JSONB); se extraen con el operador {@code ->>}.
     */
    private List<AuditEvent> recentChanges(long businessId) {
        return jdbc.sql("""
                SELECT actor, action,
                       COALESCE(details ->> 'device', 'unknown') AS device,
                       COALESCE(details ->> 'userAgent', '')      AS user_agent,
                       created_at
                FROM admin.audit_log_global
                WHERE action IN ('ENABLE_TEST_MODE', 'DISABLE_TEST_MODE', 'TEST_MODE_CUTOVER')
                  AND target_type = 'business'
                  AND target_id = :businessId
                ORDER BY created_at DESC
                LIMIT 20
                """)
                .param("businessId", String.valueOf(businessId))
                .query((rs, n) -> new AuditEvent(
                        rs.getString("actor"),
                        rs.getString("action"),
                        rs.getString("device"),
                        rs.getString("user_agent"),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .list();
    }

    /** Fila ligera del negocio del tenant: id + flag de modo prueba. */
    private record BusinessRow(long id, boolean testMode) {
    }

    /**
     * Estado del modo prueba del negocio.
     *
     * @param active        {@code true} si el modo prueba está activo
     * @param businessId    id del negocio
     * @param recentChanges bitácora reciente de activaciones/desactivaciones
     */
    public record TestModeStatus(boolean active, long businessId, List<AuditEvent> recentChanges) {
    }

    /**
     * Evento de auditoría de un cambio de modo prueba.
     *
     * @param actor     quién lo hizo
     * @param action    {@code ENABLE_TEST_MODE} / {@code DISABLE_TEST_MODE}
     * @param device    {@code desktop} / {@code mobile} / {@code unknown}
     * @param userAgent User-Agent completo de la petición
     * @param at        cuándo ocurrió
     */
    public record AuditEvent(String actor, String action, String device, String userAgent,
            OffsetDateTime at) {
    }

    /**
     * Resumen del corte prueba→real devuelto al frontend.
     *
     * @param executed      {@code true} si ESTA llamada hizo la transición
     *                      true→false (false si el modo prueba ya estaba OFF)
     * @param active        estado final de {@code test_mode} (siempre false tras el
     *                      corte)
     * @param businessId    id del negocio
     * @param practice      histórico de prueba conservado (conteos informativos)
     * @param stock         stock vendible reiniciado a 0 (con kardex)
     * @param waste         merma conservada (solo lectura, se arrastra)
     * @param recentChanges bitácora refrescada (incluye el evento del corte)
     */
    public record CutoverSummary(
            boolean executed,
            boolean active,
            long businessId,
            PracticeSummary practice,
            StockSummary stock,
            WasteSummary waste,
            List<AuditEvent> recentChanges) {
    }

    /** Histórico de prueba conservado (Opción B): nada se borra. */
    public record PracticeSummary(long salesKept, long cashCutsKept) {
    }

    /**
     * Stock vendible reiniciado por el corte.
     *
     * @param rowsAdjusted      número de filas de {@code inventory_stock} llevadas a 0
     * @param totalUnitsRemoved Σ de las cantidades previas retiradas
     * @param lines             detalle por artículo/sucursal
     */
    public record StockSummary(
            int rowsAdjusted,
            BigDecimal totalUnitsRemoved,
            List<StockAdjustmentLine> lines) {

        /** Resumen vacío (sin ajustes), para la rama idempotente del corte. */
        public static StockSummary empty() {
            return new StockSummary(0, BigDecimal.ZERO, List.of());
        }
    }

    /** Un ajuste de stock a 0: producto, sucursal y la cantidad previa retirada. */
    public record StockAdjustmentLine(long productId, long branchId, BigDecimal previousQuantity) {
    }
}
