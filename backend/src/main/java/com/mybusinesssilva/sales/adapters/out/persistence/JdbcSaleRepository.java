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
                    (folio, branch_id, cash_register_id, shift_id, cashier, customer_id,
                     subtotal, discount, tax, total, status, idempotency_key)
                VALUES (:folio, :branch, :reg, :shift, :cashier, :customer,
                        :subtotal, :discount, 0, :total, :status, :idem)
                RETURNING id
                """)
                .param("folio", sale.getFolio())
                .param("branch", sale.getBranchId())
                .param("reg", sale.getCashRegisterId())
                .param("shift", sale.getShiftId())
                .param("cashier", sale.getCashier())
                .param("customer", sale.getCustomerId())
                .param("subtotal", sale.subtotal())
                .param("discount", sale.discount())
                .param("total", sale.total())
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
}
