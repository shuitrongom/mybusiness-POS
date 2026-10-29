package com.mybusinesssilva.inventory.bakery.adapters.out.persistence;

import com.mybusinesssilva.inventory.bakery.domain.model.BatchInput;
import com.mybusinesssilva.inventory.bakery.domain.model.BatchOutput;
import com.mybusinesssilva.inventory.bakery.domain.port.out.BakeryBatchRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC de la persistencia de la TANDA, sobre el schema del tenant en
 * curso. Los joins a {@code product} para traer el NOMBRE del pan NO filtran
 * {@code active} (preservan el histórico de panes retirados); {@code active = TRUE}
 * solo se usa en las consultas de captura (ver {@code JdbcBatchProductQueryPort}).
 */
@Repository
public class JdbcBakeryBatchRepository implements BakeryBatchRepository {

    private final JdbcClient jdbc;

    public JdbcBakeryBatchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long insertHeader(long branchId, LocalDate batchDate, String createdBy,
                             String createdRole, String notes) {
        long id = jdbc.sql("""
                INSERT INTO bakery_batch
                    (branch_id, batch_date, production_id, input_cost, output_value,
                     value_over_inputs, notes, created_by, created_role)
                VALUES (:branch, :batchDate, NULL, 0, 0, 0, :notes, :createdBy, :createdRole)
                RETURNING id
                """)
                .param("branch", branchId)
                .param("batchDate", batchDate)
                .param("notes", notes)
                .param("createdBy", createdBy)
                .param("createdRole", createdRole)
                .query(Long.class)
                .single();
        jdbc.sql("UPDATE bakery_batch SET folio = :folio WHERE id = :id")
                .param("folio", "T-" + id)
                .param("id", id)
                .update();
        return id;
    }

    @Override
    public void closeBatch(long batchId, long productionId, BigDecimal inputCost,
                           BigDecimal outputValue, BigDecimal valueOverInputs) {
        jdbc.sql("""
                UPDATE bakery_batch
                   SET production_id = :prod, input_cost = :inputCost,
                       output_value = :outputValue, value_over_inputs = :voi
                 WHERE id = :id
                """)
                .param("prod", productionId)
                .param("inputCost", inputCost)
                .param("outputValue", outputValue)
                .param("voi", valueOverInputs)
                .param("id", batchId)
                .update();
    }

    @Override
    public void insertInput(long batchId, BatchInput in) {
        jdbc.sql("""
                INSERT INTO bakery_batch_input
                    (batch_id, raw_material_id, quantity, unit, unit_cost, line_cost)
                VALUES (:batch, :rm, :qty, :unit, :cost, :line)
                """)
                .param("batch", batchId)
                .param("rm", in.rawMaterialId())
                .param("qty", in.quantity())
                .param("unit", in.unit())
                .param("cost", in.unitCost())
                .param("line", in.lineCost())
                .update();
    }

    @Override
    public void insertOutput(long batchId, BatchOutput out) {
        jdbc.sql("""
                INSERT INTO bakery_batch_output
                    (batch_id, product_id, quantity, unit, entry_price, line_value)
                VALUES (:batch, :product, :qty, :unit, :price, :line)
                """)
                .param("batch", batchId)
                .param("product", out.productId())
                .param("qty", out.quantity())
                .param("unit", out.unit())
                .param("price", out.entryPrice())
                .param("line", out.lineValue())
                .update();
    }

    @Override
    public List<Map<String, Object>> myBatchesToday(long branchId, String actor, LocalDate todayMx) {
        return jdbc.sql("""
                SELECT b.id, b.folio, b.batch_date, b.created_at,
                       COALESCE((SELECT SUM(o.quantity) FROM bakery_batch_output o WHERE o.batch_id = b.id), 0) AS total_output
                FROM bakery_batch b
                WHERE b.branch_id = :branch
                  AND b.batch_date = :today
                  AND b.created_by = :actor
                ORDER BY b.id DESC
                """)
                .param("branch", branchId)
                .param("today", todayMx)
                .param("actor", actor)
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("batchId", rs.getLong("id"));
                    row.put("folio", rs.getString("folio"));
                    row.put("batchDate", rs.getObject("batch_date", LocalDate.class));
                    row.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
                    row.put("totalOutput", rs.getBigDecimal("total_output"));
                    row.put("outputs", outputsOf(rs.getLong("id")));
                    return row;
                })
                .list();
    }

    @Override
    public List<Map<String, Object>> listBatches(Long branchId, LocalDate from, LocalDate to, int limit) {
        return jdbc.sql("""
                SELECT b.id, b.folio, b.branch_id, br.name AS branch_name, b.batch_date,
                       b.production_id, b.created_by, b.created_role,
                       b.input_cost, b.output_value, b.value_over_inputs, b.created_at,
                       COALESCE((SELECT SUM(i.quantity) FROM bakery_batch_input i WHERE i.batch_id = b.id), 0) AS total_input,
                       COALESCE((SELECT SUM(o.quantity) FROM bakery_batch_output o WHERE o.batch_id = b.id), 0) AS total_output,
                       EXISTS (SELECT 1 FROM bakery_batch_input i WHERE i.batch_id = b.id AND i.unit_cost = 0) AS has_zero_cost
                FROM bakery_batch b
                LEFT JOIN branch br ON br.id = b.branch_id
                WHERE (CAST(:branch AS bigint) IS NULL OR b.branch_id = CAST(:branch AS bigint))
                  AND (CAST(:from AS date) IS NULL OR b.batch_date >= CAST(:from AS date))
                  AND (CAST(:to   AS date) IS NULL OR b.batch_date <= CAST(:to   AS date))
                ORDER BY b.batch_date DESC, b.id DESC
                LIMIT :limit
                """)
                .param("branch", branchId)
                .param("from", from)
                .param("to", to)
                .param("limit", limit <= 0 ? 50 : limit)
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("batchId", rs.getLong("id"));
                    row.put("folio", rs.getString("folio"));
                    row.put("branchId", rs.getLong("branch_id"));
                    row.put("branchName", rs.getString("branch_name"));
                    row.put("batchDate", rs.getObject("batch_date", LocalDate.class));
                    long prod = rs.getLong("production_id");
                    row.put("productionId", rs.wasNull() ? null : prod);
                    row.put("createdBy", rs.getString("created_by"));
                    row.put("createdRole", rs.getString("created_role"));
                    row.put("inputCost", rs.getBigDecimal("input_cost"));
                    row.put("outputValue", rs.getBigDecimal("output_value"));
                    row.put("valueOverInputs", rs.getBigDecimal("value_over_inputs"));
                    row.put("totalInput", rs.getBigDecimal("total_input"));
                    row.put("totalOutput", rs.getBigDecimal("total_output"));
                    row.put("hasZeroCostInput", rs.getBoolean("has_zero_cost"));
                    row.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
                    return row;
                })
                .list();
    }

    @Override
    public Map<String, Object> batchDetail(long batchId) {
        Map<String, Object> head = jdbc.sql("""
                SELECT b.id, b.folio, b.branch_id, br.name AS branch_name, b.batch_date,
                       b.production_id, b.created_by, b.created_role,
                       b.input_cost, b.output_value, b.value_over_inputs, b.notes, b.created_at
                FROM bakery_batch b
                LEFT JOIN branch br ON br.id = b.branch_id
                WHERE b.id = :id
                """)
                .param("id", batchId)
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("batchId", rs.getLong("id"));
                    row.put("folio", rs.getString("folio"));
                    row.put("branchId", rs.getLong("branch_id"));
                    row.put("branchName", rs.getString("branch_name"));
                    row.put("batchDate", rs.getObject("batch_date", LocalDate.class));
                    long prod = rs.getLong("production_id");
                    row.put("productionId", rs.wasNull() ? null : prod);
                    row.put("createdBy", rs.getString("created_by"));
                    row.put("createdRole", rs.getString("created_role"));
                    row.put("inputCost", rs.getBigDecimal("input_cost"));
                    row.put("outputValue", rs.getBigDecimal("output_value"));
                    row.put("valueOverInputs", rs.getBigDecimal("value_over_inputs"));
                    row.put("notes", rs.getString("notes"));
                    row.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
                    return row;
                })
                .optional()
                .orElse(null);
        if (head == null) {
            return null;
        }
        head.put("inputs", inputsOf(batchId));
        head.put("outputs", outputsOf(batchId));
        return head;
    }

    private List<Map<String, Object>> inputsOf(long batchId) {
        return jdbc.sql("""
                SELECT i.raw_material_id, m.name, i.quantity, i.unit, i.unit_cost, i.line_cost
                FROM bakery_batch_input i
                JOIN raw_material m ON m.id = i.raw_material_id
                WHERE i.batch_id = :id
                ORDER BY i.id ASC
                """)
                .param("id", batchId)
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("rawMaterialId", rs.getLong("raw_material_id"));
                    row.put("name", rs.getString("name"));
                    row.put("quantity", rs.getBigDecimal("quantity"));
                    row.put("unit", rs.getString("unit"));
                    row.put("unitCost", rs.getBigDecimal("unit_cost"));
                    row.put("lineCost", rs.getBigDecimal("line_cost"));
                    return row;
                })
                .list();
    }

    private List<Map<String, Object>> outputsOf(long batchId) {
        // Join a product SIN filtrar active: preserva el nombre de panes retirados.
        return jdbc.sql("""
                SELECT o.product_id, pr.name AS product_name, o.quantity, o.unit, o.entry_price, o.line_value
                FROM bakery_batch_output o
                JOIN product pr ON pr.id = o.product_id
                WHERE o.batch_id = :id
                ORDER BY o.id ASC
                """)
                .param("id", batchId)
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productId", rs.getLong("product_id"));
                    row.put("productName", rs.getString("product_name"));
                    row.put("quantity", rs.getBigDecimal("quantity"));
                    row.put("unit", rs.getString("unit"));
                    row.put("entryPrice", rs.getBigDecimal("entry_price"));
                    row.put("lineValue", rs.getBigDecimal("line_value"));
                    return row;
                })
                .list();
    }

    @Override
    public List<Map<String, Object>> yieldReport(Long branchId, LocalDate from, LocalDate to) {
        // Rendimiento por (producto, insumo): Σ panes / Σ insumo del par en el rango.
        return jdbc.sql("""
                SELECT o.product_id, pr.name AS product_name,
                       i.raw_material_id, m.name AS raw_material_name, m.unit AS raw_unit,
                       SUM(o.quantity) AS output_qty, SUM(i.quantity) AS input_qty
                FROM bakery_batch b
                JOIN bakery_batch_output o ON o.batch_id = b.id
                JOIN bakery_batch_input  i ON i.batch_id = b.id
                JOIN product pr ON pr.id = o.product_id
                JOIN raw_material m ON m.id = i.raw_material_id
                WHERE (CAST(:branch AS bigint) IS NULL OR b.branch_id = CAST(:branch AS bigint))
                  AND (CAST(:from AS date) IS NULL OR b.batch_date >= CAST(:from AS date))
                  AND (CAST(:to   AS date) IS NULL OR b.batch_date <= CAST(:to   AS date))
                GROUP BY o.product_id, pr.name, i.raw_material_id, m.name, m.unit
                ORDER BY pr.name, m.name
                """)
                .param("branch", branchId)
                .param("from", from)
                .param("to", to)
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("productId", rs.getLong("product_id"));
                    row.put("productName", rs.getString("product_name"));
                    row.put("rawMaterialId", rs.getLong("raw_material_id"));
                    row.put("rawMaterialName", rs.getString("raw_material_name"));
                    row.put("rawUnit", rs.getString("raw_unit"));
                    BigDecimal outQty = rs.getBigDecimal("output_qty");
                    BigDecimal inQty = rs.getBigDecimal("input_qty");
                    row.put("outputQty", outQty);
                    row.put("inputQty", inQty);
                    row.put("yield", inQty != null && inQty.signum() != 0
                            ? outQty.divide(inQty, 4, java.math.RoundingMode.HALF_UP) : null);
                    return row;
                })
                .list();
    }

    @Override
    public List<Map<String, Object>> backupProductions(Long branchId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                SELECT p.id AS production_id, p.folio, p.branch_id, p.production_date,
                       p.created_by, p.created_role, p.total_qty, p.total_value
                FROM bakery_production p
                LEFT JOIN bakery_batch b ON b.production_id = p.id
                WHERE b.id IS NULL
                  AND (CAST(:branch AS bigint) IS NULL OR p.branch_id = CAST(:branch AS bigint))
                  AND (CAST(:from AS date) IS NULL OR p.production_date >= CAST(:from AS date))
                  AND (CAST(:to   AS date) IS NULL OR p.production_date <= CAST(:to   AS date))
                ORDER BY p.production_date DESC, p.id DESC
                """)
                .param("branch", branchId)
                .param("from", from)
                .param("to", to)
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("category", "BACKUP_PRODUCTION");
                    row.put("productionId", rs.getLong("production_id"));
                    row.put("folio", rs.getString("folio"));
                    row.put("branchId", rs.getLong("branch_id"));
                    row.put("productionDate", rs.getObject("production_date", LocalDate.class));
                    row.put("createdBy", rs.getString("created_by"));
                    row.put("createdRole", rs.getString("created_role"));
                    row.put("totalQty", rs.getBigDecimal("total_qty"));
                    row.put("totalValue", rs.getBigDecimal("total_value"));
                    row.put("reason", "Producción sin tanda (respaldo)");
                    return row;
                })
                .list();
    }

    @Override
    public List<Map<String, Object>> manualAdjustments(Long branchId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                SELECT mv.id, mv.product_id, pr.name AS product_name, mv.branch_id, mv.quantity,
                       mv.balance_after, mv.reason, mv.actor, mv.created_at
                FROM inventory_movement mv
                JOIN product pr ON pr.id = mv.product_id AND pr.is_bakery = TRUE
                WHERE mv.movement_type = 'ADJUSTMENT'
                  AND (CAST(:branch AS bigint) IS NULL OR mv.branch_id = CAST(:branch AS bigint))
                  AND (CAST(:from AS date) IS NULL OR mv.created_at::date >= CAST(:from AS date))
                  AND (CAST(:to   AS date) IS NULL OR mv.created_at::date <= CAST(:to   AS date))
                ORDER BY mv.created_at DESC
                """)
                .param("branch", branchId)
                .param("from", from)
                .param("to", to)
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("category", "MANUAL_ADJUSTMENT");
                    row.put("movementId", rs.getLong("id"));
                    row.put("productId", rs.getLong("product_id"));
                    row.put("productName", rs.getString("product_name"));
                    row.put("branchId", rs.getLong("branch_id"));
                    row.put("quantity", rs.getBigDecimal("quantity"));
                    row.put("balanceAfter", rs.getBigDecimal("balance_after"));
                    row.put("reason", rs.getString("reason") != null
                            ? rs.getString("reason") : "Ajuste manual de pan");
                    row.put("actor", rs.getString("actor"));
                    row.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
                    return row;
                })
                .list();
    }

    @Override
    public List<BigDecimal> recentYieldsForSameType(long batchId, int n) {
        // "Mismo tipo" = misma firma de product_id de salida. Se compara la firma
        // ordenada de productos de cada tanda contra la de la tanda dada.
        return jdbc.sql("""
                WITH target AS (
                    SELECT string_agg(DISTINCT product_id::text, ',' ORDER BY product_id::text) AS sig
                    FROM bakery_batch_output WHERE batch_id = :id
                ),
                sigs AS (
                    SELECT o.batch_id,
                           string_agg(DISTINCT o.product_id::text, ',' ORDER BY o.product_id::text) AS sig
                    FROM bakery_batch_output o
                    GROUP BY o.batch_id
                )
                SELECT CASE WHEN ti.total > 0 THEN (bo.total / ti.total) ELSE NULL END AS yield_total
                FROM sigs s
                JOIN target t ON t.sig = s.sig
                JOIN bakery_batch b ON b.id = s.batch_id
                JOIN LATERAL (SELECT COALESCE(SUM(quantity),0) AS total FROM bakery_batch_output WHERE batch_id = s.batch_id) bo ON TRUE
                JOIN LATERAL (SELECT COALESCE(SUM(quantity),0) AS total FROM bakery_batch_input  WHERE batch_id = s.batch_id) ti ON TRUE
                WHERE s.batch_id <> :id AND ti.total > 0
                ORDER BY b.id DESC
                LIMIT :n
                """)
                .param("id", batchId)
                .param("n", n <= 0 ? 10 : n)
                .query(BigDecimal.class)
                .list();
    }

    @Override
    public BigDecimal yieldTotal(long batchId) {
        return jdbc.sql("""
                SELECT CASE WHEN ti.total > 0 THEN (bo.total / ti.total) ELSE NULL END
                FROM (SELECT COALESCE(SUM(quantity),0) AS total FROM bakery_batch_output WHERE batch_id = :id) bo
                CROSS JOIN (SELECT COALESCE(SUM(quantity),0) AS total FROM bakery_batch_input WHERE batch_id = :id) ti
                """)
                .param("id", batchId)
                .query(BigDecimal.class)
                .optional()
                .orElse(null);
    }
}
