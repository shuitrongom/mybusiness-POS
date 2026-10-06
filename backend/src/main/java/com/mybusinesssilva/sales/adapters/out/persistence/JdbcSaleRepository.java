package com.mybusinesssilva.sales.adapters.out.persistence;

import com.mybusinesssilva.sales.domain.model.Payment;
import com.mybusinesssilva.sales.domain.model.Sale;
import com.mybusinesssilva.sales.domain.model.SaleLine;
import com.mybusinesssilva.sales.domain.port.out.SaleRepository;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC del repositorio de ventas, sobre el schema del tenant en curso.
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
                     total, amount_paid, on_credit, note, status, idempotency_key)
                VALUES (:folio, :branch, :reg, :shift, :cashier, :salesperson,
                        :customer, :priceList, :subtotal, :discount, :globalDiscount, 0,
                        :total, :amountPaid, :onCredit, :note, :status, :idem)
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
                .param("idem", sale.getIdempotencyKey())
                .query(Long.class)
                .single();

        for (SaleLine line : sale.getLines()) {
            jdbc.sql("""
                    INSERT INTO sale_line
                        (sale_id, product_id, description, quantity, unit_price, discount, line_total)
                    VALUES (:sale, :product, :desc, :qty, :price, :discount, :total)
                    """)
                    .param("sale", saleId)
                    .param("product", line.productId())
                    .param("desc", line.description())
                    .param("qty", line.quantity())
                    .param("price", line.unitPrice())
                    .param("discount", line.discount())
                    .param("total", line.lineTotal())
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
        return jdbc.sql("SELECT product_id, quantity FROM sale_line WHERE sale_id = :id")
                .param("id", saleId)
                .query((rs, n) -> new SaleLineRow(
                        rs.getLong("product_id"), rs.getBigDecimal("quantity")))
                .list();
    }

    @Override
    public java.util.Optional<SaleDetail> findDetail(long saleId) {
        var header = jdbc.sql("""
                SELECT id, folio, branch_id, customer_id, status, total
                FROM sale WHERE id = :id
                """)
                .param("id", saleId)
                .query((rs, n) -> new Object[] {
                        rs.getLong("id"), rs.getString("folio"), rs.getLong("branch_id"),
                        (Long) rs.getObject("customer_id"), rs.getString("status"),
                        rs.getBigDecimal("total") })
                .optional();
        if (header.isEmpty()) {
            return java.util.Optional.empty();
        }
        Object[] h = header.get();
        var lines = jdbc.sql("""
                SELECT product_id, description, quantity, unit_price
                FROM sale_line WHERE sale_id = :id ORDER BY id
                """)
                .param("id", saleId)
                .query((rs, n) -> new SaleDetailLine(
                        rs.getLong("product_id"), rs.getString("description"),
                        rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price")))
                .list();
        return java.util.Optional.of(new SaleDetail(
                (Long) h[0], (String) h[1], (Long) h[2], (Long) h[3], (String) h[4],
                (java.math.BigDecimal) h[5], lines));
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
