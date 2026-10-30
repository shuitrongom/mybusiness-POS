package com.mybusinesssilva.inventory.rawmaterial.adapters.out.persistence;

import com.mybusinesssilva.inventory.rawmaterial.domain.model.RawMaterial;
import com.mybusinesssilva.inventory.rawmaterial.domain.model.RawMaterialMovementType;
import com.mybusinesssilva.inventory.rawmaterial.domain.port.out.RawMaterialRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC del repositorio de materia prima, sobre el schema del tenant
 * en curso. SQL atómicos para el cuadre (espejo de {@code JdbcInventoryRepository}).
 */
@Repository
public class JdbcRawMaterialRepository implements RawMaterialRepository {

    private final JdbcClient jdbc;

    public JdbcRawMaterialRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long insert(RawMaterial m) {
        return jdbc.sql("""
                INSERT INTO raw_material (sku, name, unit, unit_cost, min_quantity, active)
                VALUES (:sku, :name, :unit, :unitCost, :minQty, :active)
                RETURNING id
                """)
                .param("sku", m.sku())
                .param("name", m.name())
                .param("unit", m.unit())
                .param("unitCost", m.unitCost())
                .param("minQty", m.minQuantity())
                .param("active", m.active())
                .query(Long.class)
                .single();
    }

    @Override
    public void update(long id, RawMaterial m) {
        jdbc.sql("""
                UPDATE raw_material
                   SET sku = :sku, name = :name, unit = :unit,
                       unit_cost = :unitCost, min_quantity = :minQty
                 WHERE id = :id
                """)
                .param("id", id)
                .param("sku", m.sku())
                .param("name", m.name())
                .param("unit", m.unit())
                .param("unitCost", m.unitCost())
                .param("minQty", m.minQuantity())
                .update();
    }

    @Override
    public void setActive(long id, boolean active) {
        jdbc.sql("UPDATE raw_material SET active = :active WHERE id = :id")
                .param("active", active)
                .param("id", id)
                .update();
    }

    @Override
    public Optional<RawMaterial> find(long id) {
        return jdbc.sql("""
                SELECT id, sku, name, unit, unit_cost, min_quantity, active
                FROM raw_material WHERE id = :id
                """)
                .param("id", id)
                .query((rs, n) -> new RawMaterial(
                        rs.getLong("id"),
                        rs.getString("sku"),
                        rs.getString("name"),
                        rs.getString("unit"),
                        rs.getBigDecimal("unit_cost"),
                        rs.getBigDecimal("min_quantity"),
                        rs.getBoolean("active")))
                .optional();
    }

    @Override
    public boolean existsActiveName(String name, Long excludeId) {
        return jdbc.sql("""
                SELECT count(*) FROM raw_material
                 WHERE active = TRUE
                   AND lower(name) = lower(:name)
                   AND (CAST(:exclude AS bigint) IS NULL OR id <> CAST(:exclude AS bigint))
                """)
                .param("name", name)
                .param("exclude", excludeId)
                .query(Long.class)
                .single() > 0;
    }

    @Override
    public boolean existsSku(String sku, Long excludeId) {
        return jdbc.sql("""
                SELECT count(*) FROM raw_material
                 WHERE sku = :sku
                   AND (CAST(:exclude AS bigint) IS NULL OR id <> CAST(:exclude AS bigint))
                """)
                .param("sku", sku)
                .param("exclude", excludeId)
                .query(Long.class)
                .single() > 0;
    }

    @Override
    public BigDecimal adjustQuantity(long rmId, long branchId, BigDecimal signedQty) {
        // Upsert atómico: materializa la fila si no existe; si existe, suma el delta.
        // ON CONFLICT DO UPDATE toma el lock de fila: dos entradas concurrentes se serializan.
        return jdbc.sql("""
                INSERT INTO raw_material_stock (tenant_id, raw_material_id, branch_id, quantity, updated_at)
                VALUES (current_setting('app.current_tenant', true), :rm, :branch, :signedQty, now())
                ON CONFLICT (raw_material_id, branch_id) DO UPDATE
                   SET quantity   = raw_material_stock.quantity + EXCLUDED.quantity,
                       updated_at = now()
                RETURNING quantity
                """)
                .param("rm", rmId)
                .param("branch", branchId)
                .param("signedQty", signedQty)
                .query(BigDecimal.class)
                .single();
    }

    @Override
    public boolean deductIfEnough(long rmId, long branchId, BigDecimal qty) {
        // Descuento atómico condicionado: el WHERE quantity >= :qty impide dejar negativo.
        // No crea fila (si no existe stock, 0 filas afectadas => false).
        int rows = jdbc.sql("""
                UPDATE raw_material_stock
                   SET quantity = quantity - :qty, updated_at = now()
                 WHERE raw_material_id = :rm AND branch_id = :branch AND quantity >= :qty
                """)
                .param("qty", qty)
                .param("rm", rmId)
                .param("branch", branchId)
                .update();
        return rows == 1;
    }

    @Override
    public void recordMovement(long rmId, long branchId, RawMaterialMovementType type,
                               BigDecimal signedQty, BigDecimal balanceAfter, BigDecimal unitCost,
                               String reason, String reference, String actor) {
        jdbc.sql("""
                INSERT INTO raw_material_movement
                    (raw_material_id, branch_id, movement_type, quantity, balance_after,
                     unit_cost, reason, reference, actor)
                VALUES (:rm, :branch, :type, :qty, :balance, :cost, :reason, :ref, :actor)
                """)
                .param("rm", rmId)
                .param("branch", branchId)
                .param("type", type.name())
                .param("qty", signedQty)
                .param("balance", balanceAfter)
                .param("cost", unitCost)
                .param("reason", reason)
                .param("ref", reference)
                .param("actor", actor)
                .update();
    }

    @Override
    public List<Map<String, Object>> listWithStock(Long branchId) {
        return jdbc.sql("""
                SELECT m.id, m.sku, m.name, m.unit, m.unit_cost, m.min_quantity, m.active,
                       COALESCE(s.quantity, 0) AS quantity, s.branch_id
                FROM raw_material m
                LEFT JOIN raw_material_stock s
                       ON s.raw_material_id = m.id
                      AND (CAST(:branch AS bigint) IS NULL OR s.branch_id = CAST(:branch AS bigint))
                ORDER BY m.active DESC, m.name ASC
                """)
                .param("branch", branchId)
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("rawMaterialId", rs.getLong("id"));
                    row.put("sku", rs.getString("sku"));
                    row.put("name", rs.getString("name"));
                    row.put("unit", rs.getString("unit"));
                    row.put("unitCost", rs.getBigDecimal("unit_cost"));
                    row.put("minQuantity", rs.getBigDecimal("min_quantity"));
                    row.put("active", rs.getBoolean("active"));
                    row.put("quantity", rs.getBigDecimal("quantity"));
                    long branch = rs.getLong("branch_id");
                    row.put("branchId", rs.wasNull() ? null : branch);
                    return row;
                })
                .list();
    }

    @Override
    public List<Map<String, Object>> listAvailableForBatch(long branchId) {
        return jdbc.sql("""
                SELECT m.id, m.name, m.unit, m.unit_cost, s.quantity
                FROM raw_material m
                JOIN raw_material_stock s ON s.raw_material_id = m.id AND s.branch_id = :branch
                WHERE m.active = TRUE AND s.quantity > 0
                ORDER BY m.name ASC
                """)
                .param("branch", branchId)
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("rawMaterialId", rs.getLong("id"));
                    row.put("name", rs.getString("name"));
                    row.put("unit", rs.getString("unit"));
                    row.put("unitCost", rs.getBigDecimal("unit_cost"));
                    row.put("quantity", rs.getBigDecimal("quantity"));
                    return row;
                })
                .list();
    }

    @Override
    public BigDecimal stock(long rmId, long branchId) {
        return jdbc.sql("""
                SELECT COALESCE(quantity, 0) FROM raw_material_stock
                 WHERE raw_material_id = :rm AND branch_id = :branch
                """)
                .param("rm", rmId)
                .param("branch", branchId)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    @Override
    public boolean hasMovements(long rmId) {
        return jdbc.sql("SELECT EXISTS(SELECT 1 FROM raw_material_movement WHERE raw_material_id = :rm)")
                .param("rm", rmId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public boolean hasAnyStock(long rmId) {
        return jdbc.sql("""
                SELECT EXISTS(
                    SELECT 1 FROM raw_material_stock
                     WHERE raw_material_id = :rm AND quantity <> 0)
                """)
                .param("rm", rmId)
                .query(Boolean.class)
                .single();
    }

    @Override
    public void hardDelete(long rmId) {
        // Orden por FK: primero las filas de existencia (todas en 0 por invariante del service),
        // luego la fila del catálogo. No se toca raw_material_movement (no hay, por invariante).
        jdbc.sql("DELETE FROM raw_material_stock WHERE raw_material_id = :rm")
                .param("rm", rmId)
                .update();
        jdbc.sql("DELETE FROM raw_material WHERE id = :id")
                .param("id", rmId)
                .update();
    }

    @Override
    public List<Map<String, Object>> movements(long rmId, LocalDate from, LocalDate to) {
        return jdbc.sql("""
                SELECT id, movement_type, quantity, balance_after, unit_cost,
                       reason, reference, actor, created_at
                FROM raw_material_movement
                WHERE raw_material_id = :rm
                  AND (CAST(:from AS date) IS NULL OR created_at::date >= CAST(:from AS date))
                  AND (CAST(:to   AS date) IS NULL OR created_at::date <= CAST(:to   AS date))
                ORDER BY id DESC
                """)
                .param("rm", rmId)
                .param("from", from)
                .param("to", to)
                .query((rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("movementId", rs.getLong("id"));
                    row.put("movementType", rs.getString("movement_type"));
                    row.put("quantity", rs.getBigDecimal("quantity"));
                    row.put("balanceAfter", rs.getBigDecimal("balance_after"));
                    row.put("unitCost", rs.getBigDecimal("unit_cost"));
                    row.put("reason", rs.getString("reason"));
                    row.put("reference", rs.getString("reference"));
                    row.put("actor", rs.getString("actor"));
                    row.put("createdAt", rs.getObject("created_at", OffsetDateTime.class));
                    return row;
                })
                .list();
    }
}
