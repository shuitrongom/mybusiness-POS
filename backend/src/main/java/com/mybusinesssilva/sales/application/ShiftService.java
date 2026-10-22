package com.mybusinesssilva.sales.application;

import com.mybusinesssilva.inventory.bakery.domain.model.WasteOrigin;
import com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase;
import com.mybusinesssilva.platform.security.ModuleAccessEvaluator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de turnos y cortes de caja.
 *
 * <p>
 * Un turno se abre con un fondo de caja, registra entradas/salidas de efectivo,
 * y al cerrarse
 * se calcula el corte (ventas por método de pago) y se compara con el conteo
 * físico (arqueo),
 * reportando la diferencia.
 *
 * <p>
 * En giro panadería, al cerrar la caja se aplica el <strong>candado de
 * merma</strong>: el
 * sobrante de pan en existencia normal de la sucursal pasa automáticamente a
 * merma (origen
 * {@code CIERRE}), dentro de la misma transacción del cierre.
 */
@Service
public class ShiftService {

        private final JdbcClient jdbc;
        private final BakeryUseCase bakeryUseCase;
        private final ModuleAccessEvaluator moduleAccess;
        private final com.mybusinesssilva.sales.domain.port.out.TestModeQueryPort testModeQueryPort;

        public ShiftService(JdbcClient jdbc, BakeryUseCase bakeryUseCase,
                        ModuleAccessEvaluator moduleAccess,
                        com.mybusinesssilva.sales.domain.port.out.TestModeQueryPort testModeQueryPort) {
                this.jdbc = jdbc;
                this.bakeryUseCase = bakeryUseCase;
                this.moduleAccess = moduleAccess;
                this.testModeQueryPort = testModeQueryPort;
        }

        /**
         * Resuelve el {@link ReportScope} efectivo de un reporte: usa el scope pedido
         * si viene, o el default por modo (BOTH si test_mode ON, REAL si OFF).
         */
        private ReportScope resolveScope(String scope) {
                return ReportScope.resolve(scope, testModeQueryPort.isTestModeActive());
        }

        /**
         * Abre un turno para una caja con su fondo inicial. Valida que la caja no tenga
         * ya un turno
         * abierto (una caja = un turno activo). Si {@code handoverFrom} viene
         * informado, registra que
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
                // SEGURIDAD (cero pérdidas): si el cajero YA cerró su caja hoy (corte Z), no
                // puede volver a
                // abrir el mismo día. Debe esperar al día siguiente. Evita ventas dobles y
                // manipular el
                // corte reabriendo la caja. enforceDaily = true solo para cajeros; el
                // dueño/admin puede reabrir.
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

        /**
         * Compatibilidad: apertura simple sin sucursal ni entrega (sin enforcement
         * diario).
         */
        @Transactional
        public long openShift(long cashRegisterId, String openedBy, BigDecimal openingFloat) {
                return openShift(cashRegisterId, openedBy, openingFloat, null, null, null, false);
        }

        /**
         * @return true si el cajero ya cerró su caja hoy (para el POS: mostrar pantalla
         *         de "caja
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
         * Turno abierto del cajero indicado, o vacío si no tiene ninguno. Lo usa el POS
         * al entrar para
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
         * @return true si el cajero ya cerró un turno hoy en esa caja (para
         *         advertir/bloquear que
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
         * Resumen EN VIVO del turno abierto del cajero (su corte parcial del día):
         * fondo, ventas por
         * método, total, # de ventas, unidades, ticket promedio, entradas/salidas,
         * efectivo esperado,
         * últimas ventas y productos más vendidos del turno. Vacío si el cajero no
         * tiene turno abierto.
         *
         * <p>
         * Incluye además el split NORMAL/ESPECIAL del turno
         * ({@code normalSales*}/{@code specialSales*}),
         * calculado sobre el importe pagado por método (ver
         * {@link #salesTypeSplit(long)}), de modo que
         * {@code normalSalesTotal + specialSalesTotal == totalSales}.
         */
        public java.util.Optional<java.util.Map<String, Object>> myShiftSummary(String cashier) {
                return myShiftSummary(cashier, null);
        }

        /**
         * Igual que {@link #myShiftSummary(String)} pero filtrando las ventas del
         * resumen por el {@link ReportScope} pedido (real/test/both). Default por modo
         * si {@code scope} es {@code null}.
         */
        public java.util.Optional<java.util.Map<String, Object>> myShiftSummary(String cashier, String scope) {
                java.util.Optional<java.util.Map<String, Object>> activeOpt = activeShiftOf(cashier);
                if (activeOpt.isEmpty()) {
                        return java.util.Optional.empty();
                }
                java.util.Map<String, Object> active = activeOpt.get();
                long shiftId = ((Number) active.get("shiftId")).longValue();
                return java.util.Optional.of(summaryForShift(shiftId, active, resolveScope(scope)));
        }

        /**
         * Resumen del DÍA del cajero en solo lectura: busca su ÚLTIMO turno de hoy
         * ({@code business_date = CURRENT_DATE}), esté abierto o cerrado, y devuelve el
         * mismo mapa que {@link #myShiftSummary(String)} MÁS los campos de cierre
         * ({@code closed}, {@code closedAt}; {@code countedCash}/{@code difference}
         * cuando el turno esté cerrado). Vacío si el cajero no tiene ningún turno hoy.
         *
         * <p>
         * Es puramente informativo: permite que una cajera que ya cerró su turno hoy
         * siga viendo cómo le fue en el día, sin poder vender ni reabrir caja.
         */
        public java.util.Optional<java.util.Map<String, Object>> myDaySummary(String cashier) {
                return myDaySummary(cashier, null);
        }

        /**
         * Igual que {@link #myDaySummary(String)} pero filtrando las ventas del resumen
         * por el {@link ReportScope} pedido (real/test/both). Default por modo si
         * {@code scope} es {@code null}.
         */
        public java.util.Optional<java.util.Map<String, Object>> myDaySummary(String cashier, String scope) {
                java.util.Optional<java.util.Map<String, Object>> lastOpt = jdbc.sql("""
                                SELECT s.id, s.cash_register_id, s.branch_id, s.opening_float, s.opened_at,
                                       s.business_date, s.status, s.counted_cash, s.closed_at,
                                       cr.name AS register_name
                                FROM shift s
                                LEFT JOIN cash_register cr ON cr.id = s.cash_register_id
                                WHERE s.opened_by = :by AND s.business_date = CURRENT_DATE
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
                                        m.put("status", rs.getString("status"));
                                        m.put("countedCash", rs.getBigDecimal("counted_cash"));
                                        m.put("closedAt", rs.getObject("closed_at", java.time.OffsetDateTime.class));
                                        return m;
                                })
                                .optional();
                if (lastOpt.isEmpty()) {
                        return java.util.Optional.empty();
                }
                java.util.Map<String, Object> meta = lastOpt.get();
                long shiftId = ((Number) meta.get("shiftId")).longValue();
                boolean closed = "CLOSED".equals(meta.get("status"));
                java.util.Map<String, Object> out = summaryForShift(shiftId, meta, resolveScope(scope));
                out.put("closed", closed);
                out.put("closedAt", meta.get("closedAt"));
                if (closed) {
                        BigDecimal counted = (BigDecimal) meta.get("countedCash");
                        BigDecimal expected = (BigDecimal) out.get("expectedCash");
                        out.put("countedCash", counted);
                        out.put("difference", counted == null ? null : counted.subtract(expected));
                } else {
                        out.put("countedCash", null);
                        out.put("difference", null);
                }
                return java.util.Optional.of(out);
        }

        /**
         * Calcula el mapa de resumen de un turno (abierto o cerrado) a partir de su id
         * y un mapa con los metadatos del turno ({@code registerName},
         * {@code openedAt},
         * {@code businessDate}). Reutilizado por el resumen en vivo
         * ({@link #myShiftSummary(String)}) y por el resumen del día tras cierre
         * ({@link #myDaySummary(String)}), para no duplicar las consultas de métodos,
         * unidades, top y últimas ventas.
         */
        private java.util.Map<String, Object> summaryForShift(long shiftId,
                        java.util.Map<String, Object> meta, ReportScope scope) {
                // Predicados de scope (modo prueba) sobre la venta, según el alias usado en
                // cada consulta ("s" o la tabla sin alias). Vacío para BOTH.
                String onSale = scope.sqlPredicate("s.is_test");
                String onSaleBare = scope.sqlPredicate("is_test");

                BigDecimal openingFloat = jdbc.sql("SELECT opening_float FROM shift WHERE id = :id")
                                .param("id", shiftId).query(BigDecimal.class).single();
                BigDecimal cashSales = sumSalesByMethod(shiftId, "CASH", scope);
                BigDecimal cardSales = sumSalesByMethod(shiftId, "CARD", scope);
                BigDecimal transferSales = sumSalesByMethod(shiftId, "TRANSFER", scope);
                BigDecimal voucherSales = sumSalesByMethod(shiftId, "VOUCHER", scope);
                BigDecimal cashIn = sumCashMovements(shiftId, "IN");
                BigDecimal cashOut = sumCashMovements(shiftId, "OUT");
                BigDecimal expectedCash = openingFloat.add(cashSales).add(cashIn).subtract(cashOut);
                BigDecimal totalSales = cashSales.add(cardSales).add(transferSales).add(voucherSales);

                Integer salesCount = jdbc.sql(
                                "SELECT COUNT(*) FROM sale WHERE shift_id = :s AND status = 'COMPLETED'" + onSaleBare)
                                .param("s", shiftId).query(Integer.class).single();
                BigDecimal units = jdbc.sql("""
                                SELECT COALESCE(SUM(sl.quantity), 0) FROM sale_line sl
                                JOIN sale s ON s.id = sl.sale_id
                                WHERE s.shift_id = :s AND s.status = 'COMPLETED'""" + onSale + """
                                """).param("s", shiftId).query(BigDecimal.class).single();
                BigDecimal creditSales = jdbc.sql("""
                                SELECT COALESCE(SUM(total), 0) FROM sale
                                WHERE shift_id = :s AND status = 'COMPLETED' AND on_credit = TRUE""" + onSaleBare + """
                                """).param("s", shiftId).query(BigDecimal.class).optional().orElse(BigDecimal.ZERO);

                TypeSplit split = salesTypeSplit(shiftId, scope);

                BigDecimal avgTicket = (salesCount == null || salesCount == 0)
                                ? BigDecimal.ZERO
                                : totalSales.divide(BigDecimal.valueOf(salesCount), 2, java.math.RoundingMode.HALF_UP);

                java.util.List<java.util.Map<String, Object>> lastSales = jdbc.sql("""
                                SELECT id, folio, total, created_at FROM sale
                                WHERE shift_id = :s AND status = 'COMPLETED' """ + onSaleBare + """

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
                                WHERE s.shift_id = :s AND s.status = 'COMPLETED' """ + onSale + """

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
                out.put("registerName", meta.get("registerName"));
                out.put("openedAt", meta.get("openedAt"));
                out.put("businessDate", meta.get("businessDate"));
                out.put("openingFloat", openingFloat);
                out.put("cashSales", cashSales);
                out.put("cardSales", cardSales);
                out.put("transferSales", transferSales);
                out.put("voucherSales", voucherSales);
                out.put("creditSales", creditSales);
                out.put("cashIn", cashIn);
                out.put("cashOut", cashOut);
                out.put("movements", cashMovementsOf(shiftId));
                out.put("expectedCash", expectedCash);
                out.put("totalSales", totalSales);
                out.put("salesCount", salesCount);
                out.put("units", units);
                out.put("averageTicket", avgTicket);
                out.put("normalSalesCount", split.normalCount());
                out.put("normalSalesTotal", split.normalTotal());
                out.put("specialSalesCount", split.specialCount());
                out.put("specialSalesTotal", split.specialTotal());
                out.put("lastSales", lastSales);
                out.put("topProducts", topProducts);
                return out;
        }

        /**
         * Reporte de ventas por cajero en un rango de fechas: total, # ventas, y
         * desglose de efectivo
         * y tarjeta. Lo usa el admin para ver la venta de cada cajero por separado.
         *
         * <p>
         * Incluye el split NORMAL/ESPECIAL por cajero
         * ({@code normalSales*}/{@code specialSales*})
         * calculado sobre el importe pagado por método (los 4 del corte), coherente con
         * el corte de
         * caja. El campo {@code total} sigue siendo {@code SUM(sale.total)} (importe
         * facturado).
         */
        public java.util.List<java.util.Map<String, Object>> salesByCashier(
                        java.time.LocalDate from, java.time.LocalDate to) {
                return jdbc.sql("""
                                WITH pay AS (
                                    SELECT sp.sale_id,
                                           SUM(CASE WHEN sp.method = 'CASH' THEN sp.amount ELSE 0 END) AS cash,
                                           SUM(CASE WHEN sp.method = 'CARD' THEN sp.amount ELSE 0 END) AS card,
                                           SUM(CASE WHEN sp.method = 'TRANSFER' THEN sp.amount ELSE 0 END) AS transfer,
                                           SUM(CASE WHEN sp.method IN ('CASH','CARD','TRANSFER','VOUCHER')
                                                    THEN sp.amount ELSE 0 END) AS paid
                                    FROM sale_payment sp GROUP BY sp.sale_id
                                )
                                SELECT s.cashier,
                                       COUNT(*) AS sales_count,
                                       COALESCE(SUM(s.total), 0) AS total,
                                       COALESCE(SUM(p.cash), 0) AS cash,
                                       COALESCE(SUM(p.card), 0) AS card,
                                       COALESCE(SUM(p.transfer), 0) AS transfer,
                                       COUNT(*) FILTER (WHERE s.sale_type = 'NORMAL')  AS normal_count,
                                       COALESCE(SUM(p.paid) FILTER (WHERE s.sale_type = 'NORMAL'), 0)  AS normal_total,
                                       COUNT(*) FILTER (WHERE s.sale_type = 'SPECIAL') AS special_count,
                                       COALESCE(SUM(p.paid) FILTER (WHERE s.sale_type = 'SPECIAL'), 0) AS special_total
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
                                        m.put("normalSalesCount", rs.getInt("normal_count"));
                                        m.put("normalSalesTotal", rs.getBigDecimal("normal_total"));
                                        m.put("specialSalesCount", rs.getInt("special_count"));
                                        m.put("specialSalesTotal", rs.getBigDecimal("special_total"));
                                        return m;
                                })
                                .list();
        }

        /**
         * Registra una entrada o salida de efectivo en el turno (retiro, gasto,
         * ingreso).
         */
        @Transactional
        public void recordCashMovement(long shiftId, String direction, BigDecimal amount,
                        String reason, String actor) {
                // 1) El turno debe EXISTIR y estar ABIERTO. NO se valida propiedad (opened_by):
                // operar un turno abierto ajeno es el relevo legítimo; el actor queda trazado
                // en
                // la propia fila de cash_movement (simétrico con closeShift y registerSale).
                java.util.Optional<String> status = jdbc.sql("SELECT status FROM shift WHERE id = :id")
                                .param("id", shiftId).query(String.class).optional();
                if (status.isEmpty()) {
                        throw new IllegalStateException("El turno indicado no existe.");
                }
                if (!"OPEN".equals(status.get())) {
                        throw new IllegalStateException(
                                        "La caja ya está cerrada; no admite movimientos de efectivo.");
                }
                // 2) Dirección y monto (red de seguridad además del Bean Validation del DTO).
                String dir = direction == null ? "" : direction.trim().toUpperCase(java.util.Locale.ROOT);
                if (!"IN".equals(dir) && !"OUT".equals(dir)) {
                        throw new IllegalArgumentException("La dirección debe ser IN u OUT.");
                }
                if (amount == null || amount.signum() <= 0) {
                        throw new IllegalArgumentException("El monto debe ser mayor a 0.");
                }
                // 3) Motivo obligatorio (red de seguridad además del @NotBlank del DTO): un
                // movimiento sin motivo rompe la trazabilidad/auditoría del dueño. Se persiste
                // el motivo recortado (trim) para no guardar espacios sueltos.
                if (reason == null || reason.trim().isEmpty()) {
                        throw new IllegalArgumentException("El motivo del movimiento es obligatorio.");
                }
                String trimmedReason = reason.trim();
                // 4) Insert trazable (actor, turno, razón, monto, fecha por DEFAULT now()).
                jdbc.sql("""
                                INSERT INTO cash_movement (shift_id, direction, amount, reason, actor)
                                VALUES (:shift, :dir, :amount, :reason, :actor)
                                """)
                                .param("shift", shiftId)
                                .param("dir", dir)
                                .param("amount", amount)
                                .param("reason", trimmedReason)
                                .param("actor", actor)
                                .update();
        }

        /**
         * Lista DETALLADA de los movimientos de efectivo del turno (entradas y
         * salidas),
         * para la trazabilidad del corte: quién, cuándo, cuánto, por qué y de qué tipo.
         * Scope BOTH (sin filtro de modo prueba), coherente con cómo el corte ya suma
         * el
         * efectivo del turno ({@code sumCashMovements}): los movimientos pertenecen al
         * turno y el documento de corte hereda el modo al persistirse.
         *
         * @return filas con claves
         *         {@code id, direction, amount, reason, actor, createdAt},
         *         ordenadas por {@code created_at} ascendente (orden cronológico).
         */
        public java.util.List<java.util.Map<String, Object>> cashMovementsOf(long shiftId) {
                return jdbc.sql("""
                                SELECT id, direction, amount, reason, actor, created_at
                                FROM cash_movement
                                WHERE shift_id = :s
                                ORDER BY created_at ASC
                                """)
                                .param("s", shiftId)
                                .query((rs, n) -> {
                                        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                                        m.put("id", rs.getLong("id"));
                                        m.put("direction", rs.getString("direction"));
                                        m.put("amount", rs.getBigDecimal("amount"));
                                        m.put("reason", rs.getString("reason"));
                                        m.put("actor", rs.getString("actor"));
                                        m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                                        return m;
                                })
                                .list();
        }

        /**
         * Modo de cierre de turno.
         *
         * <ul>
         * <li>{@code TURNO}: relevo intermedio. Hace el corte y cierra el turno, pero
         * <strong>NO</strong> convierte el pan sobrante a merma: queda vendible para la
         * siguiente cajera (hereda el stock normal de la sucursal).</li>
         * <li>{@code FIN_DIA}: cierre de fin de jornada. Hace el corte Y además dispara
         * el candado de merma, pasando el pan sobrante del stock normal a merma (origen
         * {@code CIERRE}) en la misma transacción.</li>
         * </ul>
         */
        public enum CloseMode {
                TURNO,
                FIN_DIA
        }

        /**
         * Compatibilidad: cierre de turno simple (modo {@link CloseMode#TURNO}, sin
         * conversión de sobrante a merma). Delega en la sobrecarga con modo.
         */
        @Transactional
        public ShiftClosure closeShift(long shiftId, String closedBy, BigDecimal countedCash) {
                return closeShift(shiftId, closedBy, countedCash, CloseMode.TURNO);
        }

        /**
         * Cierra el turno con el efectivo contado (arqueo) y devuelve el corte con la
         * diferencia. El candado de merma (sobrante de pan a merma) SOLO se dispara en
         * modo {@link CloseMode#FIN_DIA}; en modo {@link CloseMode#TURNO} (relevo) el
         * pan sobrante queda vendible para la siguiente cajera.
         */
        @Transactional
        public ShiftClosure closeShift(long shiftId, String closedBy, BigDecimal countedCash, CloseMode mode) {
                CloseMode effectiveMode = (mode == null) ? CloseMode.TURNO : mode;

                // (0) Leer el estado real del turno: distingue inexistente / cerrado / abierto,
                // lo que el UPDATE ... WHERE status='OPEN' por sí solo NO distingue (claimed==0
                // se produce tanto por "ya cerrado" como por "no existe").
                java.util.Optional<String> st = jdbc.sql("SELECT status FROM shift WHERE id = :id")
                                .param("id", shiftId).query(String.class).optional();
                if (st.isEmpty()) {
                        throw new IllegalStateException("El turno indicado no existe.");
                }
                if ("CLOSED".equals(st.get())) {
                        // Idempotente: devuelve el corte Z ya persistido, sin recalcular.
                        return existingClosureOf(shiftId);
                }

                // (1) Candado atómico: solo un llamador concurrente gana el claim OPEN ->
                // CLOSED.
                int claimed = jdbc.sql("""
                                UPDATE shift SET status = 'CLOSED', closed_by = :by, counted_cash = :counted,
                                    closed_at = now()
                                WHERE id = :id AND status = 'OPEN'
                                """)
                                .param("id", shiftId)
                                .param("by", closedBy)
                                .param("counted", countedCash)
                                .update();
                if (claimed == 0) {
                        // Perdió la carrera contra otro cierre concurrente que acaba de cerrar este
                        // turno: idempotente, devuelve el Z que ganó el claim.
                        return existingClosureOf(shiftId);
                }

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

                TypeSplit split = salesTypeSplit(shiftId);

                // Persiste el corte Z (documento de cierre).
                persistCut(shiftId, "Z", openingFloat, cashSales, cardSales, transferSales, voucherSales,
                                cashIn, cashOut, expectedCash, countedCash, difference, split, closedBy);

                // Candado de merma (giro panadería): SOLO en cierre de fin de jornada. En el
                // relevo intermedio (CloseMode.TURNO) el sobrante NO se convierte: queda
                // vendible
                // para la siguiente cajera, que hereda el stock normal de la sucursal.
                if (effectiveMode == CloseMode.FIN_DIA) {
                        transferLeftoverToWasteOnClose(shiftId, closedBy);
                }

                return new ShiftClosure(shiftId, openingFloat, cashSales, cardSales, transferSales,
                                voucherSales, cashIn, cashOut, expectedCash, countedCash, difference,
                                split.normalCount(), split.normalTotal(), split.specialCount(), split.specialTotal());
        }

        /**
         * Reconstruye el {@link ShiftClosure} de un turno YA CERRADO a partir de su
         * corte Z persistido (idempotencia del cierre: un segundo POST sobre un turno
         * cerrado devuelve el mismo corte Z sin recalcular ni re-persistir). Usa
         * {@code ORDER BY created_at ASC LIMIT 1} para ser determinista ante datos
         * legados con dos Z (el bug de doble cierre previo). Si el turno está CLOSED
         * pero NO tiene corte Z (dato corrupto), lanza error explícito en vez de
         * recalcular un corte que podría diferir del firmado.
         */
        private ShiftClosure existingClosureOf(long shiftId) {
                return jdbc.sql("""
                                SELECT opening_float, cash_sales, card_sales, transfer_sales, voucher_sales,
                                       cash_in, cash_out, expected_cash, counted_cash, difference,
                                       normal_sales_count, normal_sales_total,
                                       special_sales_count, special_sales_total
                                FROM cash_cut
                                WHERE shift_id = :id AND cut_type = 'Z'
                                ORDER BY created_at ASC LIMIT 1
                                """)
                                .param("id", shiftId)
                                .query((rs, n) -> new ShiftClosure(shiftId,
                                                rs.getBigDecimal("opening_float"), rs.getBigDecimal("cash_sales"),
                                                rs.getBigDecimal("card_sales"), rs.getBigDecimal("transfer_sales"),
                                                rs.getBigDecimal("voucher_sales"), rs.getBigDecimal("cash_in"),
                                                rs.getBigDecimal("cash_out"), rs.getBigDecimal("expected_cash"),
                                                rs.getBigDecimal("counted_cash"), rs.getBigDecimal("difference"),
                                                rs.getInt("normal_sales_count"), rs.getBigDecimal("normal_sales_total"),
                                                rs.getInt("special_sales_count"),
                                                rs.getBigDecimal("special_sales_total")))
                                .optional()
                                .orElseThrow(() -> new IllegalStateException(
                                                "El turno está cerrado pero no tiene corte Z; revísalo manualmente."));
        }

        /**
         * Candado de merma al cierre: solo en giro panadería (módulo {@code bakery}
         * activo). Por
         * cada producto con existencia normal &gt; 0 en la sucursal del turno,
         * transfiere ese
         * sobrante a merma con origen {@code CIERRE}. Es idempotente respecto a lo que
         * ya se pasó
         * manualmente antes del cierre: solo mueve lo que siga en existencia normal
         * positiva.
         *
         * <p>
         * Si el turno no tiene sucursal asociada (dato opcional en {@code shift}), no
         * hay a qué
         * sucursal aplicar el candado y se omite sin error.
         */
        private void transferLeftoverToWasteOnClose(long shiftId, String closedBy) {
                if (!moduleAccess.canUse("bakery")) {
                        return;
                }
                Long branchId = jdbc.sql("SELECT branch_id FROM shift WHERE id = :id")
                                .param("id", shiftId)
                                .query(Long.class)
                                .optional()
                                .orElse(null);
                if (branchId == null) {
                        return;
                }
                List<Map<String, Object>> leftovers = bakeryUseCase.productsWithNormalStock(branchId);
                for (Map<String, Object> row : leftovers) {
                        long productId = ((Number) row.get("productId")).longValue();
                        BigDecimal quantity = (BigDecimal) row.get("quantity");
                        if (quantity == null || quantity.signum() <= 0) {
                                continue;
                        }
                        bakeryUseCase.transferToWaste(productId, branchId, quantity,
                                        WasteOrigin.CIERRE, closedBy, null, shiftId);
                }
        }

        /**
         * Corte X: lectura parcial del turno SIN cerrarlo. Calcula el desglose actual y
         * lo persiste
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

                TypeSplit split = salesTypeSplit(shiftId);

                persistCut(shiftId, "X", openingFloat, cashSales, cardSales, transferSales, voucherSales,
                                cashIn, cashOut, expectedCash, null, null, split, actor);

                return new ShiftClosure(shiftId, openingFloat, cashSales, cardSales, transferSales,
                                voucherSales, cashIn, cashOut, expectedCash, null, null,
                                split.normalCount(), split.normalTotal(), split.specialCount(), split.specialTotal());
        }

        /**
         * Inserta un corte (X o Z) como documento, con el conteo de ventas del turno.
         */
        private void persistCut(long shiftId, String type, BigDecimal openingFloat,
                        BigDecimal cashSales, BigDecimal cardSales, BigDecimal transferSales,
                        BigDecimal voucherSales, BigDecimal cashIn, BigDecimal cashOut,
                        BigDecimal expectedCash, BigDecimal countedCash, BigDecimal difference,
                        TypeSplit split, String actor) {
                BigDecimal totalSales = cashSales.add(cardSales).add(transferSales).add(voucherSales);
                Integer salesCount = jdbc.sql("""
                                SELECT COUNT(*) FROM sale
                                WHERE shift_id = :shift AND status = 'COMPLETED'
                                """)
                                .param("shift", shiftId).query(Integer.class).single();

                // MODO PRUEBA: el corte (X o Z) hereda el modo vigente al momento de cortar,
                // para que los cortes de prueba no se mezclen con los reales en reportes.
                boolean testMode = testModeQueryPort.isTestModeActive();

                long cutId = jdbc.sql("""
                                INSERT INTO cash_cut
                                    (shift_id, cut_type, opening_float, cash_sales, card_sales, transfer_sales,
                                     voucher_sales, cash_in, cash_out, expected_cash, counted_cash, difference,
                                     sales_count, total_sales, normal_sales_count, normal_sales_total,
                                     special_sales_count, special_sales_total, created_by, is_test)
                                VALUES (:shift, :type, :float, :cash, :card, :transfer, :voucher, :in, :out,
                                        :expected, :counted, :diff, :count, :total, :nCount, :nTotal,
                                        :sCount, :sTotal, :actor, :isTest)
                                RETURNING id
                                """)
                                .param("shift", shiftId).param("type", type).param("float", openingFloat)
                                .param("cash", cashSales).param("card", cardSales).param("transfer", transferSales)
                                .param("voucher", voucherSales).param("in", cashIn).param("out", cashOut)
                                .param("expected", expectedCash).param("counted", countedCash)
                                .param("diff", difference).param("count", salesCount).param("total", totalSales)
                                .param("nCount", split.normalCount()).param("nTotal", split.normalTotal())
                                .param("sCount", split.specialCount()).param("sTotal", split.specialTotal())
                                .param("actor", actor).param("isTest", testMode)
                                .query(Long.class).single();

                jdbc.sql("UPDATE cash_cut SET folio = :folio WHERE id = :id")
                                .param("folio", type + "-" + cutId).param("id", cutId).update();
        }

        /** Compatibilidad: cortes recientes sin filtro de scope (default por modo). */
        public java.util.List<java.util.Map<String, Object>> listCuts(int limit) {
                return listCuts(limit, null);
        }

        /**
         * Lista los cortes recientes (documentos X/Z) para consulta/reimpresión,
         * filtrando por el {@link ReportScope} pedido sobre {@code cash_cut.is_test}
         * (real/test/both). Default por modo si {@code scope} es {@code null}.
         */
        public java.util.List<java.util.Map<String, Object>> listCuts(int limit, String scope) {
                int max = limit <= 0 ? 50 : Math.min(limit, 200);
                ReportScope resolved = resolveScope(scope);
                return jdbc.sql("""
                                SELECT id, folio, shift_id, cut_type, opening_float, cash_sales, card_sales,
                                       transfer_sales, voucher_sales, cash_in, cash_out, expected_cash,
                                       counted_cash, difference, sales_count, total_sales,
                                       normal_sales_count, normal_sales_total, special_sales_count,
                                       special_sales_total, created_by, created_at
                                FROM cash_cut
                                WHERE 1 = 1 """ + resolved.sqlPredicate("is_test") + """

                                ORDER BY created_at DESC LIMIT :max
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
                                        m.put("normalSalesCount", rs.getInt("normal_sales_count"));
                                        m.put("normalSalesTotal", rs.getBigDecimal("normal_sales_total"));
                                        m.put("specialSalesCount", rs.getInt("special_sales_count"));
                                        m.put("specialSalesTotal", rs.getBigDecimal("special_sales_total"));
                                        m.put("createdBy", rs.getString("created_by"));
                                        m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                                        return m;
                                })
                                .list();
        }

        /**
         * Suma por método SIN filtro de scope (BOTH): el corte X/Z refleja TODO lo
         * ocurrido en el turno, independientemente del modo prueba. Lo usan
         * {@code closeShift}/{@code partialCut}.
         */
        private BigDecimal sumSalesByMethod(long shiftId, String method) {
                return sumSalesByMethod(shiftId, method, ReportScope.BOTH);
        }

        private BigDecimal sumSalesByMethod(long shiftId, String method, ReportScope scope) {
                return jdbc.sql("""
                                SELECT COALESCE(SUM(sp.amount), 0) FROM sale_payment sp
                                JOIN sale s ON s.id = sp.sale_id
                                WHERE s.shift_id = :shift AND s.status = 'COMPLETED' AND sp.method = :method"""
                                + scope.sqlPredicate("s.is_test") + """
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
         * Split NORMAL/ESPECIAL del turno sobre el importe pagado por método (los 4
         * métodos que
         * componen {@code total_sales}: CASH/CARD/TRANSFER/VOUCHER), filtrando ventas
         * COMPLETED.
         * Por construcción {@code normalTotal + specialTotal == total_sales} del corte,
         * y el conteo
         * usa {@code COUNT(DISTINCT s.id)} para no inflar por ventas con varios pagos.
         * El LEFT JOIN
         * incluye ventas COMPLETED sin pagos (cuentan con total 0).
         */
        private TypeSplit salesTypeSplit(long shiftId) {
                return salesTypeSplit(shiftId, ReportScope.BOTH);
        }

        private TypeSplit salesTypeSplit(long shiftId, ReportScope scope) {
                return jdbc.sql("""
                                SELECT
                                  COUNT(DISTINCT s.id) FILTER (WHERE s.sale_type = 'NORMAL')  AS n_count,
                                  COALESCE(SUM(sp.amount) FILTER (WHERE s.sale_type = 'NORMAL'), 0) AS n_total,
                                  COUNT(DISTINCT s.id) FILTER (WHERE s.sale_type = 'SPECIAL') AS s_count,
                                  COALESCE(SUM(sp.amount) FILTER (WHERE s.sale_type = 'SPECIAL'), 0) AS s_total
                                FROM sale s
                                LEFT JOIN sale_payment sp
                                       ON sp.sale_id = s.id
                                      AND sp.method IN ('CASH','CARD','TRANSFER','VOUCHER')
                                WHERE s.shift_id = :s AND s.status = 'COMPLETED'""" + scope.sqlPredicate("s.is_test")
                                + """
                                                """)
                                .param("s", shiftId)
                                .query((rs, n) -> new TypeSplit(
                                                rs.getInt("n_count"), rs.getBigDecimal("n_total"),
                                                rs.getInt("s_count"), rs.getBigDecimal("s_total")))
                                .single();
        }

        /**
         * Split de ventas por tipo (NORMAL/ESPECIAL) sobre importe pagado, para el
         * corte.
         */
        private record TypeSplit(int normalCount, BigDecimal normalTotal,
                        int specialCount, BigDecimal specialTotal) {
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
                        BigDecimal difference,
                        int normalSalesCount,
                        BigDecimal normalSalesTotal,
                        int specialSalesCount,
                        BigDecimal specialSalesTotal) {
        }
}
