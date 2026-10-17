package com.mybusinesssilva.inventory.bakery.adapters.out.persistence;

import com.mybusinesssilva.inventory.bakery.domain.model.BakeryEntryStatus;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProduction;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProductionLine;
import com.mybusinesssilva.inventory.bakery.domain.port.out.BakeryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC del repositorio de panadería, sobre el schema del tenant
 * en curso.
 *
 * <p>
 * Reglas de fecha (Decisión 3 del diseño): el día natural es
 * America/Mexico_City en ambas
 * caras. La cara "producido" usa {@code production_date} (ya en la zona del
 * negocio por su
 * DEFAULT). La cara "vendido" filtra por un rango sobre {@code created_at}
 * crudo
 * ({@code >= :fromTs AND < :toTsNextDay}) para que el índice
 * {@code (product_id, created_at)}
 * sea sargable, calculando los límites como el inicio del día natural
 * {@code from} y el inicio
 * del día natural siguiente a {@code to}.
 */
@Repository
public class JdbcBakeryRepository implements BakeryRepository {

    private static final String TZ = "America/Mexico_City";

    private final JdbcClient jdbc;

    public JdbcBakeryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long insertProduction(BakeryProduction p) {
        long id = jdbc.sql("""
                INSERT INTO bakery_production
                    (branch_id, status_code, production_date, total_qty, total_value,
                     notes, created_by, created_role)
                VALUES (:branch, COALESCE(:status, 'PRODUCED'),
                        COALESCE(:prodDate, (now() AT TIME ZONE '%s')::date),
                        :totalQty, :totalValue, :notes, :createdBy, :createdRole)
                RETURNING id
                """.formatted(TZ))
                .param("branch", p.branchId())
                .param("status", p.statusCode())
                .param("prodDate", p.productionDate())
                .param("totalQty", p.totalQty())
                .param("totalValue", p.totalValue())
                .param("notes", p.notes())
                .param("createdBy", p.createdBy())
                .param("createdRole", p.createdRole())
                .query(Long.class)
                .single();

        jdbc.sql("UPDATE bakery_production SET folio = :folio WHERE id = :id")
                .param("folio", "PROD-" + id)
                .param("id", id)
                .update();

        for (BakeryProductionLine line : p.lines()) {
            jdbc.sql("""
                    INSERT INTO bakery_production_line
                        (production_id, product_id, description, quantity, unit,
                         entry_price, unit_cost, line_total)
                    VALUES (:prod, :product, :desc, :qty, :unit, :entry, :cost, :total)
                    """)
                    .param("prod", id)
                    .param("product", line.productId())
                    .param("desc", line.description())
                    .param("qty", line.quantity())
                    .param("unit", line.unit())
                    .param("entry", line.entryPrice())
                    .param("cost", line.unitCost())
                    .param("total", line.lineTotal())
                    .update();
        }
        return id;
    }

    @Override
    public List<Map<String, Object>> listRecentProductions(Long branchId, LocalDate from, LocalDate to, int limit) {
        return jdbc.sql("""
                SELECT p.id, p.folio, p.branch_id, p.status_code, p.production_date,
                       p.total_qty, p.total_value, p.notes, p.created_by, p.created_role, p.created_at,
                       l.product_id, pr.name AS product_name, l.description, l.quantity, l.unit,
                       l.entry_price, l.unit_cost, l.line_total
                FROM bakery_production p
                JOIN bakery_production_line l ON l.production_id = p.id
                JOIN product pr ON pr.id = l.product_id
                WHERE (CAST(:branch AS bigint) IS NULL OR p.branch_id = CAST(:branch AS bigint))
                  AND (CAST(:from AS date) IS NULL OR p.production_date >= CAST(:from AS date))
                  AND (CAST(:to   AS date) IS NULL OR p.production_date <= CAST(:to   AS date))
                ORDER BY p.production_date DESC, p.id DESC, l.id ASC
                LIMIT :limit
                """)
                .param("branch", branchId)
                .param("from", from)
                .param("to", to)
                .param("limit", limit <= 0 ? 50 : limit)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("productionId", rs.getLong("id"));
                    m.put("folio", rs.getString("folio"));
                    m.put("branchId", rs.getLong("branch_id"));
                    m.put("statusCode", rs.getString("status_code"));
                    m.put("productionDate", rs.getObject("production_date", LocalDate.class));
                    m.put("totalQty", rs.getBigDecimal("total_qty"));
                    m.put("totalValue", rs.getBigDecimal("total_value"));
                    m.put("notes", rs.getString("notes"));
                    m.put("createdBy", rs.getString("created_by"));
                    m.put("createdRole", rs.getString("created_role"));
                    m.put("createdAt", rs.getTimestamp("created_at").toInstant());
                    m.put("productId", rs.getLong("product_id"));
                    m.put("productName", rs.getString("product_name"));
                    m.put("description", rs.getString("description"));
                    m.put("quantity", rs.getBigDecimal("quantity"));
                    m.put("unit", rs.getString("unit"));
                    m.put("entryPrice", rs.getBigDecimal("entry_price"));
                    m.put("unitCost", rs.getBigDecimal("unit_cost"));
                    m.put("lineTotal", rs.getBigDecimal("line_total"));
                    return m;
                })
                .list();
    }

    @Override
    public List<BakeryEntryStatus> listEntryStatuses() {
        return jdbc.sql("""
                SELECT id, code, label, sort_order, active
                FROM bakery_entry_status
                WHERE active = TRUE
                ORDER BY sort_order, id
                """)
                .query((rs, n) -> new BakeryEntryStatus(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("label"),
                        rs.getInt("sort_order"),
                        rs.getBoolean("active")))
                .list();
    }

    @Override
    public long upsertEntryStatus(String code, String label) {
        jdbc.sql("""
                INSERT INTO bakery_entry_status (code, label)
                VALUES (:code, :label)
                ON CONFLICT (code) DO NOTHING
                """)
                .param("code", code)
                .param("label", label)
                .update();
        return jdbc.sql("SELECT id FROM bakery_entry_status WHERE code = :code")
                .param("code", code)
                .query(Long.class)
                .single();
    }

    @Override
    public void addWasteStock(long productId, long branchId, BigDecimal quantity) {
        // Upsert atómico: crea el renglón de merma o le suma la cantidad.
        jdbc.sql("""
                INSERT INTO bakery_waste_stock (product_id, branch_id, quantity)
                VALUES (:pid, :bid, :qty)
                ON CONFLICT (product_id, branch_id) DO UPDATE SET
                    quantity   = bakery_waste_stock.quantity + EXCLUDED.quantity,
                    updated_at = now()
                """)
                .param("pid", productId)
                .param("bid", branchId)
                .param("qty", quantity)
                .update();
    }

    @Override
    public boolean deductWasteStock(long productId, long branchId, BigDecimal quantity) {
        // Descuento atómico que no deja negativo: el WHERE exige saldo suficiente, de
        // modo que
        // dos salidas de merma concurrentes nunca sobrevendan. 0 filas => existencia
        // insuficiente.
        int updated = jdbc.sql("""
                UPDATE bakery_waste_stock
                SET quantity = quantity - :qty, updated_at = now()
                WHERE product_id = :pid AND branch_id = :bid AND quantity >= :qty
                """)
                .param("pid", productId)
                .param("bid", branchId)
                .param("qty", quantity)
                .update();
        return updated > 0;
    }

    @Override
    public BigDecimal wasteStock(long productId, long branchId) {
        return jdbc.sql("""
                SELECT COALESCE(quantity, 0)
                FROM bakery_waste_stock
                WHERE product_id = :pid AND branch_id = :bid
                """)
                .param("pid", productId)
                .param("bid", branchId)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public void insertWasteMovement(long productId, long branchId, String origin, BigDecimal quantity,
            BigDecimal normalPrice, BigDecimal wastePrice, BigDecimal difference,
            String cashier, Long saleId, Long shiftId) {
        jdbc.sql("""
                INSERT INTO bakery_waste_movement
                    (product_id, branch_id, origin, quantity, normal_price, waste_price,
                     difference, cashier, sale_id, shift_id)
                VALUES (:pid, :bid, :origin, :qty, :normal, :waste, :diff, :cashier, :sale, :shift)
                """)
                .param("pid", productId)
                .param("bid", branchId)
                .param("origin", origin)
                .param("qty", quantity)
                .param("normal", normalPrice)
                .param("waste", wastePrice)
                .param("diff", difference)
                .param("cashier", cashier)
                .param("sale", saleId)
                .param("shift", shiftId)
                .update();
    }

    @Override
    public List<Map<String, Object>> listWasteMovements(LocalDate from, LocalDate to, Long productId) {
        return jdbc.sql("""
                SELECT w.id, w.product_id, pr.name AS product_name, w.origin, w.quantity,
                       w.normal_price, w.waste_price, w.difference, w.cashier,
                       w.sale_id, s.folio AS sale_folio, w.shift_id, w.created_at
                FROM bakery_waste_movement w
                JOIN product pr ON pr.id = w.product_id
                LEFT JOIN sale s ON s.id = w.sale_id
                WHERE (CAST(:from AS date) IS NULL
                       OR w.created_at >= (CAST(:from AS date)) AT TIME ZONE '%s')
                  AND (CAST(:to AS date) IS NULL
                       OR w.created_at < (CAST(:to AS date) + 1) AT TIME ZONE '%s')
                  AND (CAST(:product AS bigint) IS NULL OR w.product_id = CAST(:product AS bigint))
                ORDER BY w.created_at DESC, w.id DESC
                """.formatted(TZ, TZ))
                .param("from", from)
                .param("to", to)
                .param("product", productId)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("productId", rs.getLong("product_id"));
                    m.put("productName", rs.getString("product_name"));
                    m.put("origin", rs.getString("origin"));
                    m.put("quantity", rs.getBigDecimal("quantity"));
                    m.put("normalPrice", rs.getBigDecimal("normal_price"));
                    m.put("wastePrice", rs.getBigDecimal("waste_price"));
                    m.put("difference", rs.getBigDecimal("difference"));
                    m.put("cashier", rs.getString("cashier"));
                    long saleId = rs.getLong("sale_id");
                    m.put("saleId", rs.wasNull() ? null : saleId);
                    m.put("saleFolio", rs.getString("sale_folio"));
                    long shiftId = rs.getLong("shift_id");
                    m.put("shiftId", rs.wasNull() ? null : shiftId);
                    m.put("createdAt", rs.getTimestamp("created_at").toInstant());
                    return m;
                })
                .list();
    }

    @Override
    public List<Map<String, Object>> listUnifiedExits(Long branchId, int limit) {
        // Historial unificado de salidas de panadería (Opción A): salida normal
        // (inventory_document OUT) + merma MANUAL/REPARTO (bakery_waste_movement), en
        // un
        // único UNION ALL para que ORDER BY/LIMIT globales se apliquen sobre el
        // conjunto
        // combinado (mismo patrón de CTE/UNION que dailyStockAll). Para la merma,
        // totalValue
        // usa `difference` (la pérdida (normal-merma)*cantidad), la cifra de valor con
        // significado en bakery_waste_movement para un historial de salidas.
        return jdbc.sql("""
                SELECT ('DOC-' || d.id)                     AS row_key,
                       'NORMAL'                             AS kind,
                       d.folio                              AS folio,
                       COALESCE(d.concept_name, 'Salida')   AS concept,
                       b.name                               AS branch_name,
                       d.total_qty                          AS total_qty,
                       d.total_value                        AS total_value,
                       d.created_by                         AS cashier,
                       d.created_at                         AS created_at
                FROM inventory_document d
                LEFT JOIN branch b ON b.id = d.branch_id
                WHERE d.doc_type = 'OUT'
                  AND (CAST(:branch AS bigint) IS NULL OR d.branch_id = CAST(:branch AS bigint))

                UNION ALL

                SELECT ('WASTE-' || w.id)                   AS row_key,
                       w.origin                             AS kind,
                       s.folio                              AS folio,
                       CASE w.origin WHEN 'MANUAL'  THEN 'Paso a merma'
                                     WHEN 'REPARTO' THEN 'Reparto de merma'
                                     ELSE w.origin END       AS concept,
                       b.name                               AS branch_name,
                       w.quantity                           AS total_qty,
                       w.difference                         AS total_value,
                       w.cashier                            AS cashier,
                       w.created_at                         AS created_at
                FROM bakery_waste_movement w
                JOIN branch b ON b.id = w.branch_id
                LEFT JOIN sale s ON s.id = w.sale_id
                WHERE w.origin IN ('MANUAL', 'REPARTO')
                  AND (CAST(:branch AS bigint) IS NULL OR w.branch_id = CAST(:branch AS bigint))

                ORDER BY created_at DESC
                LIMIT :limit
                """)
                .param("branch", branchId)
                .param("limit", limit <= 0 ? 50 : limit)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getString("row_key"));
                    m.put("kind", rs.getString("kind"));
                    m.put("folio", rs.getString("folio"));
                    m.put("concept", rs.getString("concept"));
                    m.put("branchName", rs.getString("branch_name"));
                    m.put("totalQty", rs.getBigDecimal("total_qty"));
                    m.put("totalValue", rs.getBigDecimal("total_value"));
                    m.put("cashier", rs.getString("cashier"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();
    }

    @Override
    public List<Map<String, Object>> productsWithNormalStock(long branchId) {
        return jdbc.sql("""
                SELECT st.product_id, st.quantity
                FROM inventory_stock st
                JOIN product p ON p.id = st.product_id
                WHERE st.branch_id = :bid AND st.quantity > 0 AND p.active = TRUE
                ORDER BY st.product_id
                """)
                .param("bid", branchId)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("productId", rs.getLong("product_id"));
                    m.put("quantity", rs.getBigDecimal("quantity"));
                    return m;
                })
                .list();
    }

    @Override
    public List<Map<String, Object>> productsWithStock(long branchId, String kind) {
        // kind = 'waste' -> existencia EN MERMA (bakery_waste_stock); cualquier otro ->
        // stock normal.
        // Devuelve solo productos de pan ACTIVOS con existencia > 0 del tipo pedido,
        // para que el
        // selector de Salidas muestre únicamente lo que SÍ tiene inventario (evita
        // confundir al cajero).
        String table = "waste".equalsIgnoreCase(kind) ? "bakery_waste_stock" : "inventory_stock";
        return jdbc.sql(("""
                SELECT s.product_id, p.name AS product_name, p.unit, s.quantity
                FROM %s s
                JOIN product p ON p.id = s.product_id
                WHERE s.branch_id = :bid AND s.quantity > 0 AND p.active = TRUE
                ORDER BY p.name
                """).formatted(table))
                .param("bid", branchId)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("productId", rs.getLong("product_id"));
                    m.put("productName", rs.getString("product_name"));
                    m.put("unit", rs.getString("unit"));
                    m.put("quantity", rs.getBigDecimal("quantity"));
                    return m;
                })
                .list();
    }

    @Override
    public Optional<Long> saleBranch(long saleId) {
        return jdbc.sql("SELECT branch_id FROM sale WHERE id = :id")
                .param("id", saleId)
                .query(Long.class)
                .optional();
    }

    @Override
    public Optional<BigDecimal> productPrice(long productId) {
        return jdbc.sql("SELECT price FROM product WHERE id = :id")
                .param("id", productId)
                .query(BigDecimal.class)
                .optional();
    }

    @Override
    public BigDecimal productWastePrice(long productId) {
        return jdbc.sql("SELECT COALESCE(waste_price, 0) FROM product WHERE id = :id")
                .param("id", productId)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public BigDecimal producedBetween(long productId, long branchId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(l.quantity), 0)
                FROM bakery_production_line l
                JOIN bakery_production p ON l.production_id = p.id
                WHERE l.product_id = :pid AND p.branch_id = :bid
                  AND p.production_date >= CAST(:from AS date)
                  AND p.production_date <= CAST(:to AS date)
                """)
                .param("pid", productId)
                .param("bid", branchId)
                .param("from", from)
                .param("to", to)
                .query(BigDecimal.class)
                .single();
    }

    @Override
    public BigDecimal soldBetween(long productId, long branchId, LocalDate from, LocalDate to) {
        // Filtro por rango sobre created_at crudo (sargable con idx_movement_prod).
        // La cantidad de SALE es negativa; se niega para obtener unidades vendidas.
        return jdbc.sql("""
                SELECT COALESCE(-SUM(m.quantity), 0)
                FROM inventory_movement m
                WHERE m.product_id = :pid AND m.branch_id = :bid
                  AND m.movement_type = 'SALE'
                  AND m.reference LIKE 'SALE:%%'
                  AND m.created_at >= (CAST(:from AS date)) AT TIME ZONE '%s'
                  AND m.created_at <  (CAST(:to AS date) + 1) AT TIME ZONE '%s'
                """.formatted(TZ, TZ))
                .param("pid", productId)
                .param("bid", branchId)
                .param("from", from)
                .param("to", to)
                .query(BigDecimal.class)
                .single();
    }

    @Override
    public List<Map<String, Object>> dailySeries(long productId, long branchId, LocalDate from, LocalDate to) {
        // Serie diaria: produce y vende agregados por fecha natural
        // (America/Mexico_City).
        // El filtro de "vendido" usa el rango crudo (sargable); la clave de agrupación
        // usa la
        // expresión de día natural solo para clasificar las filas ya acotadas.
        return jdbc.sql("""
                WITH prod AS (
                    SELECT p.production_date AS day, SUM(l.quantity) AS produced
                    FROM bakery_production_line l
                    JOIN bakery_production p ON l.production_id = p.id
                    WHERE l.product_id = :pid AND p.branch_id = :bid
                      AND p.production_date >= CAST(:from AS date)
                      AND p.production_date <= CAST(:to AS date)
                    GROUP BY p.production_date
                ),
                sold AS (
                    SELECT (m.created_at AT TIME ZONE '%s')::date AS day, -SUM(m.quantity) AS sold
                    FROM inventory_movement m
                    WHERE m.product_id = :pid AND m.branch_id = :bid
                      AND m.movement_type = 'SALE'
                      AND m.reference LIKE 'SALE:%%'
                      AND m.created_at >= (CAST(:from AS date)) AT TIME ZONE '%s'
                      AND m.created_at <  (CAST(:to AS date) + 1) AT TIME ZONE '%s'
                    GROUP BY (m.created_at AT TIME ZONE '%s')::date
                )
                SELECT d.day,
                       COALESCE(prod.produced, 0) AS produced,
                       COALESCE(sold.sold, 0)     AS sold,
                       COALESCE(prod.produced, 0) - COALESCE(sold.sold, 0) AS remaining
                FROM (SELECT day FROM prod UNION SELECT day FROM sold) d
                LEFT JOIN prod ON prod.day = d.day
                LEFT JOIN sold ON sold.day = d.day
                ORDER BY d.day
                """.formatted(TZ, TZ, TZ, TZ))
                .param("pid", productId)
                .param("bid", branchId)
                .param("from", from)
                .param("to", to)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("day", rs.getObject("day", LocalDate.class));
                    m.put("produced", rs.getBigDecimal("produced"));
                    m.put("sold", rs.getBigDecimal("sold"));
                    m.put("remaining", rs.getBigDecimal("remaining"));
                    return m;
                })
                .list();
    }

    @Override
    public List<Map<String, Object>> dailyStockAll(long branchId, LocalDate date) {
        // Una sola consulta: por cada producto de pan (activo), producido y vendido del
        // día natural,
        // existencia normal actual y existencia de merma actual. El filtro de "vendido"
        // usa el rango
        // crudo sobre created_at (sargable). Se incluyen productos con producción/venta
        // ese día o con
        // existencia (normal o merma) distinta de cero, para no listar ruido con todo
        // en cero.
        return jdbc.sql("""
                WITH prod AS (
                    SELECT l.product_id, SUM(l.quantity) AS produced
                    FROM bakery_production_line l
                    JOIN bakery_production p ON l.production_id = p.id
                    WHERE p.branch_id = :bid AND p.production_date = CAST(:date AS date)
                    GROUP BY l.product_id
                ),
                sold AS (
                    SELECT m.product_id, -SUM(m.quantity) AS sold
                    FROM inventory_movement m
                    WHERE m.branch_id = :bid AND m.movement_type = 'SALE' AND m.reference LIKE 'SALE:%%'
                      AND m.created_at >= (CAST(:date AS date)) AT TIME ZONE '%s'
                      AND m.created_at <  (CAST(:date AS date) + 1) AT TIME ZONE '%s'
                    GROUP BY m.product_id
                ),
                norm AS (
                    SELECT product_id, quantity FROM inventory_stock WHERE branch_id = :bid
                ),
                merma AS (
                    SELECT product_id, quantity FROM bakery_waste_stock WHERE branch_id = :bid
                )
                SELECT p.id, p.name, p.unit, p.price,
                       COALESCE(prod.produced, 0)   AS produced,
                       COALESCE(sold.sold, 0)       AS sold,
                       COALESCE(norm.quantity, 0)   AS current_stock,
                       COALESCE(merma.quantity, 0)  AS waste
                FROM product p
                LEFT JOIN prod  ON prod.product_id  = p.id
                LEFT JOIN sold  ON sold.product_id  = p.id
                LEFT JOIN norm  ON norm.product_id  = p.id
                LEFT JOIN merma ON merma.product_id = p.id
                WHERE p.active = TRUE
                  AND (prod.produced IS NOT NULL OR sold.sold IS NOT NULL
                       OR COALESCE(norm.quantity, 0) <> 0 OR COALESCE(merma.quantity, 0) <> 0)
                ORDER BY p.name
                """.formatted(TZ, TZ))
                .param("bid", branchId)
                .param("date", date)
                .query((rs, n) -> {
                    BigDecimal produced = rs.getBigDecimal("produced");
                    BigDecimal sold = rs.getBigDecimal("sold");
                    BigDecimal waste = rs.getBigDecimal("waste");
                    BigDecimal current = rs.getBigDecimal("current_stock");
                    BigDecimal price = rs.getBigDecimal("price");
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("productId", rs.getLong("id"));
                    m.put("productName", rs.getString("name"));
                    m.put("unit", rs.getString("unit"));
                    m.put("price", price);
                    m.put("produced", produced);
                    m.put("sold", sold);
                    m.put("waste", waste);
                    m.put("currentStock", current);
                    // Restante del día = producido - vendido (lo que quedó de la producción del
                    // día).
                    m.put("remaining", produced.subtract(sold));
                    // Valor = existencia normal actual * precio.
                    m.put("value", current.multiply(price == null ? BigDecimal.ZERO : price));
                    return m;
                })
                .list();
    }

    @Override
    public BigDecimal currentStock(long productId, long branchId) {
        return jdbc.sql("""
                SELECT COALESCE(quantity, 0)
                FROM inventory_stock
                WHERE product_id = :pid AND branch_id = :bid
                """)
                .param("pid", productId)
                .param("bid", branchId)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public List<Map<String, Object>> searchBakeryItems(String query, Long branchId) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        return jdbc
                .sql("""
                        SELECT p.id, p.sku, p.name, p.unit, p.price, p.cost,
                               COALESCE(st.quantity, 0) AS quantity
                        FROM product p
                        LEFT JOIN inventory_stock st
                               ON st.product_id = p.id AND (CAST(:branch AS bigint) IS NULL OR st.branch_id = CAST(:branch AS bigint))
                        WHERE p.active = TRUE
                          AND (lower(p.name) LIKE lower(:q) OR p.sku LIKE :q)
                        ORDER BY p.name
                        LIMIT 50
                        """)
                .param("q", "%" + q + "%")
                .param("branch", branchId)
                .query((rs, n) -> itemRow(rs, false))
                .list();
    }

    @Override
    public List<Map<String, Object>> listBakeryItems(Long branchId) {
        return jdbc
                .sql("""
                        SELECT p.id, p.sku, p.name, p.unit, p.price, p.cost,
                               COALESCE(st.quantity, 0) AS quantity,
                               (p.price * COALESCE(st.quantity, 0)) AS line_total
                        FROM product p
                        LEFT JOIN inventory_stock st
                               ON st.product_id = p.id AND (CAST(:branch AS bigint) IS NULL OR st.branch_id = CAST(:branch AS bigint))
                        WHERE p.active = TRUE
                        ORDER BY p.name
                        """)
                .param("branch", branchId)
                .query((rs, n) -> itemRow(rs, true))
                .list();
    }

    private static Map<String, Object> itemRow(java.sql.ResultSet rs, boolean withLineTotal)
            throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("sku", rs.getString("sku"));
        m.put("name", rs.getString("name"));
        m.put("unit", rs.getString("unit"));
        m.put("price", rs.getBigDecimal("price"));
        m.put("cost", rs.getBigDecimal("cost"));
        m.put("quantity", rs.getBigDecimal("quantity"));
        if (withLineTotal) {
            m.put("lineTotal", rs.getBigDecimal("line_total"));
        }
        return m;
    }
}
