package com.mybusinesssilva.sales.application;

import java.math.BigDecimal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de turnos y cortes de caja.
 *
 * <p>Un turno se abre con un fondo de caja, registra entradas/salidas de efectivo, y al cerrarse
 * se calcula el corte (ventas por método de pago) y se compara con el conteo físico (arqueo),
 * reportando la diferencia.
 */
@Service
public class ShiftService {

    private final JdbcClient jdbc;

    public ShiftService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Abre un turno para una caja con su fondo inicial. Valida que la caja no tenga ya un turno
     * abierto (una caja = un turno activo). Si {@code handoverFrom} viene informado, registra que
     * este turno recibe la caja de otro (cambio de cajero).
     */
    @Transactional
    public long openShift(long cashRegisterId, String openedBy, BigDecimal openingFloat,
                          Long branchId, Long handoverFrom, String notes, boolean enforceDaily) {
        // Regla: una caja no puede tener dos turnos abiertos.
        boolean alreadyOpen = jdbc.sql(
                "SELECT count(*) FROM shift WHERE cash_register_id = :reg AND status = 'OPEN'")
                .param("reg", cashRegisterId).query(Long.class).single() > 0;
        if (alreadyOpen) {
            throw new IllegalStateException(
                    "Esa caja ya tiene un turno abierto. Ciérralo o recibe la caja antes de abrir uno nuevo.");
        }
        // SEGURIDAD (cero pérdidas): si el cajero YA cerró su caja hoy (corte Z), no puede volver a
        // abrir el mismo día. Debe esperar al día siguiente. Evita ventas dobles y manipular el
        // corte reabriendo la caja. enforceDaily = true solo para cajeros; el dueño/admin puede reabrir.
        if (enforceDaily && hasClosedTodayOnRegister(openedBy, cashRegisterId)) {
            throw new IllegalStateException(
                    "Tu corte de caja de hoy ya fue cerrado. No puedes volver a abrir esta caja "
                    + "hasta el día siguiente. Si necesitas operar, solicita al administrador.");
        }
        return jdbc.sql("""
                INSERT INTO shift (cash_register_id, branch_id, opened_by, opening_float,
                                   handover_from, notes, business_date, status)
                VALUES (:reg, :branch, :by, :float, :handover, :notes, CURRENT_DATE, 'OPEN')
                RETURNING id
                """)
                .param("reg", cashRegisterId)
                .param("branch", branchId)
                .param("by", openedBy)
                .param("float", openingFloat == null ? BigDecimal.ZERO : openingFloat)
                .param("handover", handoverFrom)
                .param("notes", notes)
                .query(Long.class)
                .single();
    }

    /** Compatibilidad: apertura simple sin sucursal ni entrega (sin enforcement diario). */
    @Transactional
    public long openShift(long cashRegisterId, String openedBy, BigDecimal openingFloat) {
        return openShift(cashRegisterId, openedBy, openingFloat, null, null, null, false);
    }

    /**
     * @return true si el cajero ya cerró su caja hoy (para el POS: mostrar pantalla de "caja
     *         cerrada por hoy" y bloquear operación hasta el día siguiente).
     */
    public boolean cashierClosedToday(String cashier) {
        return jdbc.sql("""
                SELECT count(*) FROM shift
                WHERE opened_by = :by AND status = 'CLOSED' AND business_date = CURRENT_DATE
                """)
                .param("by", cashier).query(Long.class).single() > 0;
    }

    /**
     * Turno abierto del cajero indicado, o vacío si no tiene ninguno. Lo usa el POS al entrar para
     * decidir si pedir la apertura de caja.
     */
    public java.util.Optional<java.util.Map<String, Object>> activeShiftOf(String cashier) {
        return jdbc.sql("""
                SELECT s.id, s.cash_register_id, s.branch_id, s.opening_float, s.opened_at,
                       s.business_date, cr.name AS register_name
                FROM shift s
                LEFT JOIN cash_register cr ON cr.id = s.cash_register_id
                WHERE s.opened_by = :by AND s.status = 'OPEN'
                ORDER BY s.opened_at DESC LIMIT 1
                """)
                .param("by", cashier)
                .query((rs, n) -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("shiftId", rs.getLong("id"));
                    m.put("cashRegisterId", rs.getLong("cash_register_id"));
                    m.put("branchId", (Object) rs.getObject("branch_id"));
                    m.put("registerName", rs.getString("register_name"));
                    m.put("openingFloat", rs.getBigDecimal("opening_float"));
                    m.put("openedAt", rs.getObject("opened_at", java.time.OffsetDateTime.class));
                    m.put("businessDate", rs.getObject("business_date", java.time.LocalDate.class));
                    return m;
                })
                .optional();
    }

    /**
     * @return true si el cajero ya cerró un turno hoy en esa caja (para advertir/bloquear que
     *         vuelva a vender el mismo día tras cerrar su caja).
     */
    public boolean hasClosedTodayOnRegister(String cashier, long cashRegisterId) {
        return jdbc.sql("""
                SELECT count(*) FROM shift
                WHERE opened_by = :by AND cash_register_id = :reg
                  AND status = 'CLOSED' AND business_date = CURRENT_DATE
                """)
                .param("by", cashier).param("reg", cashRegisterId)
                .query(Long.class).single() > 0;
    }

    /** @return el estado de un turno (OPEN/CLOSED), o vacío si no existe. */
    public java.util.Optional<String> statusOf(long shiftId) {
        return jdbc.sql("SELECT status FROM shift WHERE id = :id")
                .param("id", shiftId).query(String.class).optional();
    }

    /**
     * Resumen EN VIVO del turno abierto del cajero (su corte parcial del día): fondo, ventas por
     * método, total, # de ventas, unidades, ticket promedio, entradas/salidas, efectivo esperado,
     * últimas ventas y productos más vendidos del turno. Vacío si el cajero no tiene turno abierto.
     */
    public java.util.Optional<java.util.Map<String, Object>> myShiftSummary(String cashier) {
        java.util.Optional<java.util.Map<String, Object>> activeOpt = activeShiftOf(cashier);
        if (activeOpt.isEmpty()) {
            return java.util.Optional.empty();
        }
        java.util.Map<String, Object> active = activeOpt.get();
        long shiftId = ((Number) active.get("shiftId")).longValue();

        BigDecimal openingFloat = jdbc.sql("SELECT opening_float FROM shift WHERE id = :id")
                .param("id", shiftId).query(BigDecimal.class).single();
        BigDecimal cashSales = sumSalesByMethod(shiftId, "CASH");
        BigDecimal cardSales = sumSalesByMethod(shiftId, "CARD");
        BigDecimal transferSales = sumSalesByMethod(shiftId, "TRANSFER");
        BigDecimal voucherSales = sumSalesByMethod(shiftId, "VOUCHER");
        BigDecimal cashIn = sumCashMovements(shiftId, "IN");
        BigDecimal cashOut = sumCashMovements(shiftId, "OUT");
        BigDecimal expectedCash = openingFloat.add(cashSales).add(cashIn).subtract(cashOut);
        BigDecimal totalSales = cashSales.add(cardSales).add(transferSales).add(voucherSales);

        Integer salesCount = jdbc.sql(
                "SELECT COUNT(*) FROM sale WHERE shift_id = :s AND status = 'COMPLETED'")
                .param("s", shiftId).query(Integer.class).single();
        BigDecimal units = jdbc.sql("""
                SELECT COALESCE(SUM(sl.quantity), 0) FROM sale_line sl
                JOIN sale s ON s.id = sl.sale_id
                WHERE s.shift_id = :s AND s.status = 'COMPLETED'
                """).param("s", shiftId).query(BigDecimal.class).single();
        BigDecimal creditSales = jdbc.sql("""
                SELECT COALESCE(SUM(total), 0) FROM sale
                WHERE shift_id = :s AND status = 'COMPLETED' AND on_credit = TRUE
                """).param("s", shiftId).query(BigDecimal.class).optional().orElse(BigDecimal.ZERO);

        BigDecimal avgTicket = (salesCount == null || salesCount == 0)
                ? BigDecimal.ZERO
                : totalSales.divide(BigDecimal.valueOf(salesCount), 2, java.math.RoundingMode.HALF_UP);

        java.util.List<java.util.Map<String, Object>> lastSales = jdbc.sql("""
                SELECT id, folio, total, created_at FROM sale
                WHERE shift_id = :s AND status = 'COMPLETED'
                ORDER BY created_at DESC LIMIT 8
                """)
                .param("s", shiftId)
                .query((rs, n) -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("folio", rs.getString("folio"));
                    m.put("total", rs.getBigDecimal("total"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();

        java.util.List<java.util.Map<String, Object>> topProducts = jdbc.sql("""
                SELECT COALESCE(p.name, sl.description) AS name, SUM(sl.quantity) AS qty,
                       SUM(sl.line_total) AS revenue
                FROM sale_line sl
                JOIN sale s ON s.id = sl.sale_id
                LEFT JOIN product p ON p.id = sl.product_id
                WHERE s.shift_id = :s AND s.status = 'COMPLETED'
                GROUP BY COALESCE(p.name, sl.description)
                ORDER BY qty DESC LIMIT 5
                """)
                .param("s", shiftId)
                .query((rs, n) -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("name", rs.getString("name"));
                    m.put("quantity", rs.getBigDecimal("qty"));
                    m.put("revenue", rs.getBigDecimal("revenue"));
                    return m;
                })
                .list();

        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("shiftId", shiftId);
        out.put("registerName", active.get("registerName"));
        out.put("openedAt", active.get("openedAt"));
        out.put("businessDate", active.get("businessDate"));
        out.put("openingFloat", openingFloat);
        out.put("cashSales", cashSales);
        out.put("cardSales", cardSales);
        out.put("transferSales", transferSales);
        out.put("voucherSales", voucherSales);
        out.put("creditSales", creditSales);
        out.put("cashIn", cashIn);
        out.put("cashOut", cashOut);
        out.put("expectedCash", expectedCash);
        out.put("totalSales", totalSales);
        out.put("salesCount", salesCount);
        out.put("units", units);
        out.put("averageTicket", avgTicket);
        out.put("lastSales", lastSales);
        out.put("topProducts", topProducts);
        return java.util.Optional.of(out);
    }

    /**
     * Reporte de ventas por cajero en un rango de fechas: total, # ventas, y desglose de efectivo
     * y tarjeta. Lo usa el admin para ver la venta de cada cajero por separado.
     */
    public java.util.List<java.util.Map<String, Object>> salesByCashier(
            java.time.LocalDate from, java.time.LocalDate to) {
        return jdbc.sql("""
                WITH pay AS (
                    SELECT sp.sale_id,
                           SUM(CASE WHEN sp.method = 'CASH' THEN sp.amount ELSE 0 END) AS cash,
                           SUM(CASE WHEN sp.method = 'CARD' THEN sp.amount ELSE 0 END) AS card,
                           SUM(CASE WHEN sp.method = 'TRANSFER' THEN sp.amount ELSE 0 END) AS transfer
                    FROM sale_payment sp GROUP BY sp.sale_id
                )
                SELECT s.cashier,
                       COUNT(*) AS sales_count,
                       COALESCE(SUM(s.total), 0) AS total,
                       COALESCE(SUM(p.cash), 0) AS cash,
                       COALESCE(SUM(p.card), 0) AS card,
                       COALESCE(SUM(p.transfer), 0) AS transfer
                FROM sale s
                LEFT JOIN pay p ON p.sale_id = s.id
                WHERE s.status = 'COMPLETED'
                  AND (CAST(:from AS date) IS NULL OR s.created_at::date >= :from)
                  AND (CAST(:to AS date) IS NULL OR s.created_at::date <= :to)
                GROUP BY s.cashier
                ORDER BY total DESC
                """)
                .param("from", from).param("to", to)
                .query((rs, n) -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("cashier", rs.getString("cashier"));
                    m.put("salesCount", rs.getInt("sales_count"));
                    m.put("total", rs.getBigDecimal("total"));
                    m.put("cash", rs.getBigDecimal("cash"));
                    m.put("card", rs.getBigDecimal("card"));
                    m.put("transfer", rs.getBigDecimal("transfer"));
                    return m;
                })
                .list();
    }

    /** Registra una entrada o salida de efectivo en el turno (retiro, gasto, ingreso). */
    @Transactional
    public void recordCashMovement(long shiftId, String direction, BigDecimal amount,
                                   String reason, String actor) {
        jdbc.sql("""
                INSERT INTO cash_movement (shift_id, direction, amount, reason, actor)
                VALUES (:shift, :dir, :amount, :reason, :actor)
                """)
                .param("shift", shiftId)
                .param("dir", direction)
                .param("amount", amount)
                .param("reason", reason)
                .param("actor", actor)
                .update();
    }

    /**
     * Cierra el turno con el efectivo contado (arqueo) y devuelve el corte con la diferencia.
     */
    @Transactional
    public ShiftClosure closeShift(long shiftId, String closedBy, BigDecimal countedCash) {
        BigDecimal openingFloat = jdbc.sql("SELECT opening_float FROM shift WHERE id = :id")
                .param("id", shiftId).query(BigDecimal.class).single();

        BigDecimal cashSales = sumSalesByMethod(shiftId, "CASH");
        BigDecimal cardSales = sumSalesByMethod(shiftId, "CARD");
        BigDecimal transferSales = sumSalesByMethod(shiftId, "TRANSFER");
        BigDecimal voucherSales = sumSalesByMethod(shiftId, "VOUCHER");

        BigDecimal cashIn = sumCashMovements(shiftId, "IN");
        BigDecimal cashOut = sumCashMovements(shiftId, "OUT");

        // Efectivo esperado en caja = fondo + ventas en efectivo + entradas - salidas.
        BigDecimal expectedCash = openingFloat.add(cashSales).add(cashIn).subtract(cashOut);
        BigDecimal difference = (countedCash == null ? BigDecimal.ZERO : countedCash)
                .subtract(expectedCash);

        jdbc.sql("""
                UPDATE shift SET status = 'CLOSED', closed_by = :by, counted_cash = :counted,
                    closed_at = now()
                WHERE id = :id
                """)
                .param("id", shiftId)
                .param("by", closedBy)
                .param("counted", countedCash)
                .update();

        // Persiste el corte Z (documento de cierre).
        persistCut(shiftId, "Z", openingFloat, cashSales, cardSales, transferSales, voucherSales,
                cashIn, cashOut, expectedCash, countedCash, difference, closedBy);

        return new ShiftClosure(shiftId, openingFloat, cashSales, cardSales, transferSales,
                voucherSales, cashIn, cashOut, expectedCash, countedCash, difference);
    }

    /**
     * Corte X: lectura parcial del turno SIN cerrarlo. Calcula el desglose actual y lo persiste
     * como documento informativo. No exige conteo físico.
     *
     * @param shiftId turno en curso
     * @param actor   quién solicita el corte
     * @return el corte calculado
     */
    @Transactional
    public ShiftClosure partialCut(long shiftId, String actor) {
        BigDecimal openingFloat = jdbc.sql("SELECT opening_float FROM shift WHERE id = :id")
                .param("id", shiftId).query(BigDecimal.class).single();

        BigDecimal cashSales = sumSalesByMethod(shiftId, "CASH");
        BigDecimal cardSales = sumSalesByMethod(shiftId, "CARD");
        BigDecimal transferSales = sumSalesByMethod(shiftId, "TRANSFER");
        BigDecimal voucherSales = sumSalesByMethod(shiftId, "VOUCHER");
        BigDecimal cashIn = sumCashMovements(shiftId, "IN");
        BigDecimal cashOut = sumCashMovements(shiftId, "OUT");
        BigDecimal expectedCash = openingFloat.add(cashSales).add(cashIn).subtract(cashOut);

        persistCut(shiftId, "X", openingFloat, cashSales, cardSales, transferSales, voucherSales,
                cashIn, cashOut, expectedCash, null, null, actor);

        return new ShiftClosure(shiftId, openingFloat, cashSales, cardSales, transferSales,
                voucherSales, cashIn, cashOut, expectedCash, null, null);
    }

    /** Inserta un corte (X o Z) como documento, con el conteo de ventas del turno. */
    private void persistCut(long shiftId, String type, BigDecimal openingFloat,
                            BigDecimal cashSales, BigDecimal cardSales, BigDecimal transferSales,
                            BigDecimal voucherSales, BigDecimal cashIn, BigDecimal cashOut,
                            BigDecimal expectedCash, BigDecimal countedCash, BigDecimal difference,
                            String actor) {
        BigDecimal totalSales = cashSales.add(cardSales).add(transferSales).add(voucherSales);
        Integer salesCount = jdbc.sql("""
                SELECT COUNT(*) FROM sale
                WHERE shift_id = :shift AND status = 'COMPLETED'
                """)
                .param("shift", shiftId).query(Integer.class).single();

        long cutId = jdbc.sql("""
                INSERT INTO cash_cut
                    (shift_id, cut_type, opening_float, cash_sales, card_sales, transfer_sales,
                     voucher_sales, cash_in, cash_out, expected_cash, counted_cash, difference,
                     sales_count, total_sales, created_by)
                VALUES (:shift, :type, :float, :cash, :card, :transfer, :voucher, :in, :out,
                        :expected, :counted, :diff, :count, :total, :actor)
                RETURNING id
                """)
                .param("shift", shiftId).param("type", type).param("float", openingFloat)
                .param("cash", cashSales).param("card", cardSales).param("transfer", transferSales)
                .param("voucher", voucherSales).param("in", cashIn).param("out", cashOut)
                .param("expected", expectedCash).param("counted", countedCash)
                .param("diff", difference).param("count", salesCount).param("total", totalSales)
                .param("actor", actor)
                .query(Long.class).single();

        jdbc.sql("UPDATE cash_cut SET folio = :folio WHERE id = :id")
                .param("folio", type + "-" + cutId).param("id", cutId).update();
    }

    /** Lista los cortes recientes (documentos X/Z) para consulta/reimpresión. */
    public java.util.List<java.util.Map<String, Object>> listCuts(int limit) {
        int max = limit <= 0 ? 50 : Math.min(limit, 200);
        return jdbc.sql("""
                SELECT id, folio, shift_id, cut_type, opening_float, cash_sales, card_sales,
                       transfer_sales, voucher_sales, cash_in, cash_out, expected_cash,
                       counted_cash, difference, sales_count, total_sales, created_by, created_at
                FROM cash_cut ORDER BY created_at DESC LIMIT :max
                """)
                .param("max", max)
                .query((rs, n) -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("folio", rs.getString("folio"));
                    m.put("shiftId", rs.getLong("shift_id"));
                    m.put("cutType", rs.getString("cut_type"));
                    m.put("openingFloat", rs.getBigDecimal("opening_float"));
                    m.put("cashSales", rs.getBigDecimal("cash_sales"));
                    m.put("cardSales", rs.getBigDecimal("card_sales"));
                    m.put("transferSales", rs.getBigDecimal("transfer_sales"));
                    m.put("voucherSales", rs.getBigDecimal("voucher_sales"));
                    m.put("cashIn", rs.getBigDecimal("cash_in"));
                    m.put("cashOut", rs.getBigDecimal("cash_out"));
                    m.put("expectedCash", rs.getBigDecimal("expected_cash"));
                    m.put("countedCash", rs.getBigDecimal("counted_cash"));
                    m.put("difference", rs.getBigDecimal("difference"));
                    m.put("salesCount", rs.getInt("sales_count"));
                    m.put("totalSales", rs.getBigDecimal("total_sales"));
                    m.put("createdBy", rs.getString("created_by"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();
    }

    private BigDecimal sumSalesByMethod(long shiftId, String method) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(sp.amount), 0) FROM sale_payment sp
                JOIN sale s ON s.id = sp.sale_id
                WHERE s.shift_id = :shift AND s.status = 'COMPLETED' AND sp.method = :method
                """)
                .param("shift", shiftId)
                .param("method", method)
                .query(BigDecimal.class)
                .single();
    }

    private BigDecimal sumCashMovements(long shiftId, String direction) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(amount), 0) FROM cash_movement
                WHERE shift_id = :shift AND direction = :dir
                """)
                .param("shift", shiftId)
                .param("dir", direction)
                .query(BigDecimal.class)
                .single();
    }

    /**
     * Resultado del corte de caja al cerrar un turno.
     */
    public record ShiftClosure(
            long shiftId,
            BigDecimal openingFloat,
            BigDecimal cashSales,
            BigDecimal cardSales,
            BigDecimal transferSales,
            BigDecimal voucherSales,
            BigDecimal cashIn,
            BigDecimal cashOut,
            BigDecimal expectedCash,
            BigDecimal countedCash,
            BigDecimal difference) {
    }
}
