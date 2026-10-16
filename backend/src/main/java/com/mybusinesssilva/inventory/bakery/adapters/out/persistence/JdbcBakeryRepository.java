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
                WHERE (:branch IS NULL OR p.branch_id = :branch)
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
    public void upsertWasteSale(long saleId, long productId, BigDecimal quantity,
            BigDecimal entryPrice, BigDecimal wastePrice, BigDecimal difference,
            String cashier) {
        // Consolida por (sale_id, product_id): si el mismo producto ya tiene merma en
        // la venta,
        // suma la cantidad y conserva los precios/diferencia capturados en este
        // registro.
        jdbc.sql("""
                INSERT INTO bakery_waste_sale
                    (sale_id, product_id, quantity, entry_price, waste_price, difference, cashier)
                VALUES (:sale, :product, :qty, :entry, :waste, :diff, :cashier)
                ON CONFLICT (sale_id, product_id) DO UPDATE SET
                    quantity    = bakery_waste_sale.quantity + EXCLUDED.quantity,
                    entry_price = EXCLUDED.entry_price,
                    waste_price = EXCLUDED.waste_price,
                    difference  = EXCLUDED.difference,
                    cashier     = EXCLUDED.cashier
                """)
                .param("sale", saleId)
                .param("product", productId)
                .param("qty", quantity)
                .param("entry", entryPrice)
                .param("waste", wastePrice)
                .param("diff", difference)
                .param("cashier", cashier)
                .update();
    }

    @Override
    public List<Map<String, Object>> listWasteSales(LocalDate from, LocalDate to, Long productId) {
        return jdbc.sql("""
                SELECT w.id, w.sale_id, s.folio AS sale_folio, w.product_id, pr.name AS product_name,
                       w.quantity, w.entry_price, w.waste_price, w.difference, w.cashier, w.created_at
                FROM bakery_waste_sale w
                JOIN product pr ON pr.id = w.product_id
                LEFT JOIN sale s ON s.id = w.sale_id
                WHERE (CAST(:from AS date) IS NULL
                       OR w.created_at >= (CAST(:from AS date)) AT TIME ZONE '%s')
                  AND (CAST(:to AS date) IS NULL
                       OR w.created_at < (CAST(:to AS date) + 1) AT TIME ZONE '%s')
                  AND (:product IS NULL OR w.product_id = :product)
                ORDER BY w.created_at DESC, w.id DESC
                """.formatted(TZ, TZ))
                .param("from", from)
                .param("to", to)
                .param("product", productId)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("saleId", rs.getLong("sale_id"));
                    m.put("saleFolio", rs.getString("sale_folio"));
                    m.put("productId", rs.getLong("product_id"));
                    m.put("productName", rs.getString("product_name"));
                    m.put("quantity", rs.getBigDecimal("quantity"));
                    m.put("entryPrice", rs.getBigDecimal("entry_price"));
                    m.put("wastePrice", rs.getBigDecimal("waste_price"));
                    m.put("difference", rs.getBigDecimal("difference"));
                    m.put("cashier", rs.getString("cashier"));
                    m.put("createdAt", rs.getTimestamp("created_at").toInstant());
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
    public Optional<BigDecimal> latestEntryPrice(long productId, long branchId) {
        return jdbc.sql("""
                SELECT l.entry_price
                FROM bakery_production_line l
                JOIN bakery_production p ON l.production_id = p.id
                WHERE l.product_id = :pid AND p.branch_id = :bid
                ORDER BY p.production_date DESC, p.id DESC
                LIMIT 1
                """)
                .param("pid", productId)
                .param("bid", branchId)
                .query(BigDecimal.class)
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
        return jdbc.sql("""
                SELECT p.id, p.sku, p.name, p.unit, p.price, p.cost,
                       COALESCE(st.quantity, 0) AS quantity
                FROM product p
                LEFT JOIN inventory_stock st
                       ON st.product_id = p.id AND (:branch IS NULL OR st.branch_id = :branch)
                WHERE p.is_bakery = TRUE AND p.active = TRUE
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
        return jdbc.sql("""
                SELECT p.id, p.sku, p.name, p.unit, p.price, p.cost,
                       COALESCE(st.quantity, 0) AS quantity,
                       (p.price * COALESCE(st.quantity, 0)) AS line_total
                FROM product p
                LEFT JOIN inventory_stock st
                       ON st.product_id = p.id AND (:branch IS NULL OR st.branch_id = :branch)
                WHERE p.is_bakery = TRUE AND p.active = TRUE
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
