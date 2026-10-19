package com.mybusinesssilva.sales.adapters.out.persistence;

import com.mybusinesssilva.sales.domain.model.Payment;
import com.mybusinesssilva.sales.domain.model.Sale;
import com.mybusinesssilva.sales.domain.model.SaleLine;
import com.mybusinesssilva.sales.domain.port.out.SaleRepository;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementacion JDBC del repositorio de ventas, sobre el schema del tenant en
 * curso.
 */
@Repository
public class JdbcSaleRepository implements SaleRepository {

        private final JdbcClient jdbc;

        public JdbcSaleRepository(JdbcClient jdbc) {
                this.jdbc = jdbc;
        }

        @Override
        public Sale insert(Sale sale) {
                Long saleId = jdbc.sql("""
                                INSERT INTO sale
                                    (folio, branch_id, cash_register_id, shift_id, cashier, salesperson,
                                     customer_id, price_list_id, subtotal, discount, global_discount, tax,
                                     total, amount_paid, on_credit, note, status, sale_type, idempotency_key)
                                VALUES (:folio, :branch, :reg, :shift, :cashier, :salesperson,
                                        :customer, :priceList, :subtotal, :discount, :globalDiscount, 0,
                                        :total, :amountPaid, :onCredit, :note, :status, :saleType, :idem)
                                RETURNING id
                                """)
                                .param("folio", sale.getFolio())
                                .param("branch", sale.getBranchId())
                                .param("reg", sale.getCashRegisterId())
                                .param("shift", sale.getShiftId())
                                .param("cashier", sale.getCashier())
                                .param("salesperson", sale.getSalesperson())
                                .param("customer", sale.getCustomerId())
                                .param("priceList", (int) sale.getPriceListId())
                                .param("subtotal", sale.subtotal())
                                .param("discount", sale.discount())
                                .param("globalDiscount", sale.getGlobalDiscount())
                                .param("total", sale.total())
                                .param("amountPaid", sale.totalPaid())
                                .param("onCredit", sale.isOnCredit())
                                .param("note", sale.getNote())
                                .param("status", sale.getStatus().name())
                                .param("saleType", sale.getSaleType().name())
                                .param("idem", sale.getIdempotencyKey())
                                .query(Long.class)
                                .single();

                for (SaleLine line : sale.getLines()) {
                        jdbc.sql("""
                                        INSERT INTO sale_line
                                            (sale_id, product_id, description, quantity, unit_price, discount, line_total, unit, waste)
                                        VALUES (:sale, :product, :desc, :qty, :price, :discount, :total, :unit, :waste)
                                        """)
                                        .param("sale", saleId)
                                        .param("product", line.productId())
                                        .param("desc", line.description())
                                        .param("qty", line.quantity())
                                        .param("price", line.unitPrice())
                                        .param("discount", line.discount())
                                        .param("total", line.lineTotal())
                                        .param("unit", line.unit())
                                        .param("waste", line.waste())
                                        .update();
                }

                for (Payment payment : sale.getPayments()) {
                        jdbc.sql("""
                                        INSERT INTO sale_payment (sale_id, method, amount)
                                        VALUES (:sale, :method, :amount)
                                        """)
                                        .param("sale", saleId)
                                        .param("method", payment.method().name())
                                        .param("amount", payment.amount())
                                        .update();
                }

                sale.setId(saleId);
                return sale;
        }

        @Override
        public boolean existsById(long id) {
                return jdbc.sql("SELECT count(*) FROM sale WHERE id = :id")
                                .param("id", id)
                                .query(Integer.class)
                                .single() > 0;
        }

        @Override
        public Optional<Long> findIdByIdempotencyKey(String idempotencyKey) {
                if (idempotencyKey == null) {
                        return Optional.empty();
                }
                return jdbc.sql("SELECT id FROM sale WHERE idempotency_key = :k")
                                .param("k", idempotencyKey)
                                .query(Long.class)
                                .optional();
        }

        @Override
        public void markVoided(long saleId) {
                jdbc.sql("UPDATE sale SET status = 'VOIDED' WHERE id = :id")
                                .param("id", saleId)
                                .update();
        }

        @Override
        public java.util.Optional<String> statusOf(long saleId) {
                return jdbc.sql("SELECT status FROM sale WHERE id = :id")
                                .param("id", saleId)
                                .query(String.class)
                                .optional();
        }

        @Override
        public java.util.Optional<Long> branchOf(long saleId) {
                return jdbc.sql("SELECT branch_id FROM sale WHERE id = :id")
                                .param("id", saleId)
                                .query(Long.class)
                                .optional();
        }

        @Override
        public java.util.Optional<String> shiftStatus(long shiftId) {
                return jdbc.sql("SELECT status FROM shift WHERE id = :id")
                                .param("id", shiftId)
                                .query(String.class)
                                .optional();
        }

        @Override
        public java.util.List<SaleLineRow> linesOf(long saleId) {
                return jdbc.sql("SELECT product_id, quantity, waste FROM sale_line WHERE sale_id = :id")
                                .param("id", saleId)
                                .query((rs, n) -> new SaleLineRow(
                                                (Long) rs.getObject("product_id"), rs.getBigDecimal("quantity"),
                                                rs.getBoolean("waste")))
                                .list();
        }

        @Override
        public java.util.Optional<SaleDetail> findDetail(long saleId) {
                var header = jdbc.sql("""
                                SELECT s.id, s.folio, s.branch_id, s.customer_id, s.status, s.total,
                                       s.created_at, c.name AS customer_name
                                FROM sale s
                                LEFT JOIN customer c ON c.id = s.customer_id
                                WHERE s.id = :id
                                """)
                                .param("id", saleId)
                                .query((rs, n) -> new Object[] {
                                                rs.getLong("id"), rs.getString("folio"), rs.getLong("branch_id"),
                                                (Long) rs.getObject("customer_id"), rs.getString("status"),
                                                rs.getBigDecimal("total"),
                                                rs.getObject("created_at", java.time.OffsetDateTime.class),
                                                rs.getString("customer_name") })
                                .optional();
                if (header.isEmpty()) {
                        return java.util.Optional.empty();
                }
                Object[] h = header.get();
                var lines = jdbc.sql("""
                                SELECT sl.product_id, sl.description, sl.quantity, sl.unit_price, sl.unit,
                                       p.name AS product_name, COALESCE(p.active, TRUE) AS active,
                                       COALESCE((SELECT SUM(rl.quantity)
                                                 FROM sales_return_line rl
                                                 JOIN sales_return r ON r.id = rl.return_id
                                                 WHERE r.sale_id = :id AND rl.product_id = sl.product_id), 0)
                                           AS already_returned
                                FROM sale_line sl
                                LEFT JOIN product p ON p.id = sl.product_id
                                WHERE sl.sale_id = :id ORDER BY sl.id
                                """)
                                .param("id", saleId)
                                .query((rs, n) -> new SaleDetailLine(
                                                (Long) rs.getObject("product_id"), rs.getString("description"),
                                                rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
                                                rs.getString("unit"), rs.getString("product_name"),
                                                rs.getBoolean("active"), rs.getBigDecimal("already_returned")))
                                .list();
                return java.util.Optional.of(new SaleDetail(
                                (Long) h[0], (String) h[1], (Long) h[2], (Long) h[3], (String) h[4],
                                (java.math.BigDecimal) h[5], lines,
                                (java.time.OffsetDateTime) h[6], (String) h[7]));
        }

        @Override
        public java.util.List<RecentSale> recentSales(int limit, String q) {
                // Normaliza el tope (defensa: <=0 -> 20, máximo 50) para no devolver listados
                // enormes al selector de devoluciones.
                int lim = (limit <= 0) ? 20 : Math.min(limit, 50);
                // Aislamiento por tenant: lo aplica RLS sobre `sale` (igual que el historial de
                // devoluciones). No se filtra por sucursal manualmente; punto de extensión si
                // en
                // multi-sucursal se quiere acotar a la sucursal del cajero.
                return jdbc.sql("""
                                SELECT s.id, s.folio, s.created_at, s.total, c.name AS customer_name,
                                       (SELECT COUNT(*) FROM sale_line sl WHERE sl.sale_id = s.id)
                                           AS item_count,
                                       (SELECT COUNT(DISTINCT rl.product_id)
                                          FROM sales_return_line rl
                                          JOIN sales_return r ON r.id = rl.return_id
                                         WHERE r.sale_id = s.id) AS returned_count,
                                       NOT EXISTS (
                                         SELECT 1 FROM sale_line sl
                                         WHERE sl.sale_id = s.id AND sl.product_id IS NOT NULL
                                           AND sl.quantity > COALESCE((
                                             SELECT SUM(rl.quantity) FROM sales_return_line rl
                                             JOIN sales_return r ON r.id = rl.return_id
                                             WHERE r.sale_id = s.id AND rl.product_id = sl.product_id), 0)
                                       ) AS fully_returned
                                FROM sale s
                                LEFT JOIN customer c ON c.id = s.customer_id
                                WHERE s.status = 'COMPLETED'
                                  AND (CAST(:q AS text) IS NULL
                                       OR s.folio ILIKE '%' || CAST(:q AS text) || '%'
                                       OR c.name ILIKE '%' || CAST(:q AS text) || '%')
                                ORDER BY s.created_at DESC
                                LIMIT :limit
                                """)
                                .param("q", q)
                                .param("limit", lim)
                                .query((rs, n) -> new RecentSale(
                                                rs.getLong("id"), rs.getString("folio"),
                                                rs.getObject("created_at", java.time.OffsetDateTime.class),
                                                rs.getBigDecimal("total"), rs.getString("customer_name"),
                                                rs.getInt("item_count"), rs.getInt("returned_count"),
                                                rs.getBoolean("fully_returned")))
                                .list();
        }

        @Override
        public java.math.BigDecimal returnedQuantity(long saleId, long productId) {
                return jdbc.sql("""
                                SELECT COALESCE(SUM(rl.quantity), 0)
                                FROM sales_return_line rl
                                JOIN sales_return r ON r.id = rl.return_id
                                WHERE r.sale_id = :sale AND rl.product_id = :product
                                """)
                                .param("sale", saleId)
                                .param("product", productId)
                                .query(java.math.BigDecimal.class)
                                .single();
        }
}
