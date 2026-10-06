package com.mybusinesssilva.promotions.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de PROMOCIONES.
 *
 * <p>Gestiona el alta/edición/baja de promociones y expone las promociones vigentes al punto de
 * venta, que las aplica automáticamente al carrito. Persiste con {@link JdbcClient} sobre el
 * schema del tenant en curso.
 */
@Service
public class PromotionService {

    private final JdbcClient jdbc;

    public PromotionService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Lista todas las promociones (para el gestor), con los ids de producto asociados. */
    public List<Map<String, Object>> listAll() {
        var promos = jdbc.sql("""
                SELECT id, name, type, scope, category_id, value, buy_qty, pay_qty, min_amount,
                       starts_on, ends_on, max_uses, used_count, priority, active
                FROM promotion ORDER BY active DESC, priority DESC, name
                """)
                .query((rs, n) -> mapPromotion(rs))
                .list();
        attachProducts(promos);
        return promos;
    }

    /** Lista solo las promociones VIGENTES hoy y activas (para el punto de venta). */
    public List<Map<String, Object>> listActive() {
        LocalDate today = LocalDate.now();
        var promos = jdbc.sql("""
                SELECT id, name, type, scope, category_id, value, buy_qty, pay_qty, min_amount,
                       starts_on, ends_on, max_uses, used_count, priority, active
                FROM promotion
                WHERE active = TRUE
                  AND (starts_on IS NULL OR starts_on <= :today)
                  AND (ends_on   IS NULL OR ends_on   >= :today)
                  AND (max_uses IS NULL OR used_count < max_uses)
                ORDER BY priority DESC, id
                """)
                .param("today", today)
                .query((rs, n) -> mapPromotion(rs))
                .list();
        attachProducts(promos);
        return promos;
    }

    private void attachProducts(List<Map<String, Object>> promos) {
        for (var p : promos) {
            long id = ((Number) p.get("id")).longValue();
            var productIds = jdbc.sql(
                            "SELECT product_id FROM promotion_product WHERE promotion_id = :id")
                    .param("id", id)
                    .query(Long.class)
                    .list();
            p.put("productIds", productIds);
        }
    }

    /** Crea una promoción con sus productos asociados. */
    @Transactional
    public long create(PromotionInput in) {
        long id = jdbc.sql("""
                INSERT INTO promotion
                    (name, type, scope, category_id, value, buy_qty, pay_qty, min_amount,
                     starts_on, ends_on, max_uses, priority, active)
                VALUES (:name, :type, :scope, :category, :value, :buyQty, :payQty, :minAmount,
                        :startsOn, :endsOn, :maxUses, :priority, :active)
                RETURNING id
                """)
                .param("name", in.name())
                .param("type", in.type())
                .param("scope", in.scope())
                .param("category", in.categoryId())
                .param("value", in.value())
                .param("buyQty", in.buyQty())
                .param("payQty", in.payQty())
                .param("minAmount", in.minAmount())
                .param("startsOn", in.startsOn())
                .param("endsOn", in.endsOn())
                .param("maxUses", in.maxUses())
                .param("priority", in.priority())
                .param("active", in.active())
                .query(Long.class).single();
        replaceProducts(id, in.productIds());
        return id;
    }

    /** Actualiza una promoción y sus productos. */
    @Transactional
    public void update(long id, PromotionInput in) {
        int updated = jdbc.sql("""
                UPDATE promotion SET
                    name = :name, type = :type, scope = :scope, category_id = :category,
                    value = :value, buy_qty = :buyQty, pay_qty = :payQty, min_amount = :minAmount,
                    starts_on = :startsOn, ends_on = :endsOn, max_uses = :maxUses,
                    priority = :priority, active = :active
                WHERE id = :id
                """)
                .param("id", id)
                .param("name", in.name())
                .param("type", in.type())
                .param("scope", in.scope())
                .param("category", in.categoryId())
                .param("value", in.value())
                .param("buyQty", in.buyQty())
                .param("payQty", in.payQty())
                .param("minAmount", in.minAmount())
                .param("startsOn", in.startsOn())
                .param("endsOn", in.endsOn())
                .param("maxUses", in.maxUses())
                .param("priority", in.priority())
                .param("active", in.active())
                .update();
        if (updated == 0) {
            throw new IllegalArgumentException("No existe la promoción con id " + id);
        }
        replaceProducts(id, in.productIds());
    }

    /** Elimina una promoción. */
    @Transactional
    public void delete(long id) {
        jdbc.sql("DELETE FROM promotion WHERE id = :id").param("id", id).update();
    }

    private void replaceProducts(long promotionId, List<Long> productIds) {
        jdbc.sql("DELETE FROM promotion_product WHERE promotion_id = :id")
                .param("id", promotionId).update();
        if (productIds == null) {
            return;
        }
        for (Long productId : productIds) {
            jdbc.sql("""
                    INSERT INTO promotion_product (promotion_id, product_id)
                    VALUES (:promo, :product)
                    ON CONFLICT (promotion_id, product_id) DO NOTHING
                    """)
                    .param("promo", promotionId).param("product", productId)
                    .update();
        }
    }

    private Map<String, Object> mapPromotion(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("name", rs.getString("name"));
        m.put("type", rs.getString("type"));
        m.put("scope", rs.getString("scope"));
        m.put("categoryId", rs.getObject("category_id"));
        m.put("value", rs.getBigDecimal("value"));
        m.put("buyQty", rs.getBigDecimal("buy_qty"));
        m.put("payQty", rs.getBigDecimal("pay_qty"));
        m.put("minAmount", rs.getBigDecimal("min_amount"));
        m.put("startsOn", rs.getObject("starts_on", LocalDate.class));
        m.put("endsOn", rs.getObject("ends_on", LocalDate.class));
        m.put("maxUses", rs.getObject("max_uses"));
        m.put("usedCount", rs.getInt("used_count"));
        m.put("priority", rs.getInt("priority"));
        m.put("active", rs.getBoolean("active"));
        return m;
    }

    /** Datos de alta/edición de una promoción. */
    public record PromotionInput(
            String name, String type, String scope, Long categoryId,
            BigDecimal value, BigDecimal buyQty, BigDecimal payQty, BigDecimal minAmount,
            LocalDate startsOn, LocalDate endsOn, Integer maxUses, Integer priority,
            boolean active, List<Long> productIds) {
    }
}
