package com.mybusinesssilva.inventory.adapters.out.persistence;

import com.mybusinesssilva.inventory.domain.model.InventoryMovement;
import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.inventory.domain.model.StockLevel;
import com.mybusinesssilva.inventory.domain.port.out.InventoryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC del repositorio de inventario, sobre el schema del tenant en curso.
 */
@Repository
public class JdbcInventoryRepository implements InventoryRepository {

    private final JdbcClient jdbc;

    public JdbcInventoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StockLevel> findStock(long productId, long branchId) {
        return jdbc.sql("""
                SELECT product_id, branch_id, quantity, min_quantity
                FROM inventory_stock WHERE product_id = :p AND branch_id = :b
                """)
                .param("p", productId)
                .param("b", branchId)
                .query(this::mapStock)
                .optional();
    }

    @Override
    public BigDecimal adjustQuantity(long productId, long branchId, BigDecimal delta) {
        // Upsert atómico: si no existe el registro se crea; si existe, se suma el delta.
        return jdbc.sql("""
                INSERT INTO inventory_stock (product_id, branch_id, quantity)
                VALUES (:p, :b, :delta)
                ON CONFLICT (product_id, branch_id)
                DO UPDATE SET quantity = inventory_stock.quantity + :delta, updated_at = now()
                RETURNING quantity
                """)
                .param("p", productId)
                .param("b", branchId)
                .param("delta", delta)
                .query(BigDecimal.class)
                .single();
    }

    @Override
    public void setMinQuantity(long productId, long branchId, BigDecimal minQuantity) {
        jdbc.sql("""
                INSERT INTO inventory_stock (product_id, branch_id, quantity, min_quantity)
                VALUES (:p, :b, 0, :min)
                ON CONFLICT (product_id, branch_id)
                DO UPDATE SET min_quantity = :min, updated_at = now()
                """)
                .param("p", productId)
                .param("b", branchId)
                .param("min", minQuantity)
                .update();
    }

    @Override
    public void recordMovement(InventoryMovement m) {
        jdbc.sql("""
                INSERT INTO inventory_movement
                    (product_id, branch_id, movement_type, quantity, balance_after,
                     reason, reference, actor)
                VALUES (:p, :b, :type, :qty, :balance, :reason, :ref, :actor)
                """)
                .param("p", m.productId())
                .param("b", m.branchId())
                .param("type", m.type().name())
                .param("qty", m.quantity())
                .param("balance", m.balanceAfter())
                .param("reason", m.reason())
                .param("ref", m.reference())
                .param("actor", m.actor())
                .update();
    }

    @Override
    public List<InventoryMovement> kardex(long productId) {
        return jdbc.sql("""
                SELECT * FROM inventory_movement WHERE product_id = :p ORDER BY created_at DESC, id DESC
                """)
                .param("p", productId)
                .query((rs, n) -> new InventoryMovement(
                        rs.getLong("id"),
                        rs.getLong("product_id"),
                        rs.getLong("branch_id"),
                        MovementType.valueOf(rs.getString("movement_type")),
                        rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("balance_after"),
                        rs.getString("reason"),
                        rs.getString("reference"),
                        rs.getString("actor"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }

    @Override
    public List<StockLevel> findBelowMinimum() {
        return jdbc.sql("""
                SELECT product_id, branch_id, quantity, min_quantity
                FROM inventory_stock
                WHERE min_quantity > 0 AND quantity <= min_quantity
                ORDER BY product_id
                """)
                .query(this::mapStock)
                .list();
    }

    private StockLevel mapStock(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new StockLevel(
                rs.getLong("product_id"),
                rs.getLong("branch_id"),
                rs.getBigDecimal("quantity"),
                rs.getBigDecimal("min_quantity"));
    }
}
