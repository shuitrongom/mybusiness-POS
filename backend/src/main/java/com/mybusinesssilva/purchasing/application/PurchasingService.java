package com.mybusinesssilva.purchasing.application;

import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.inventory.domain.port.in.InventoryPort;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de compras y proveedores.
 *
 * <p>Al recibir una compra: se registra, se aumenta el inventario de cada renglón (vía
 * {@link InventoryPort}) y, si es a crédito, se genera la cuenta por pagar correspondiente,
 * todo en la misma transacción.
 */
@Service
public class PurchasingService {

    private final JdbcClient jdbc;
    private final InventoryPort inventoryPort;

    public PurchasingService(JdbcClient jdbc, InventoryPort inventoryPort) {
        this.jdbc = jdbc;
        this.inventoryPort = inventoryPort;
    }

    /** Da de alta un proveedor y devuelve su id. */
    @Transactional
    public long createSupplier(String name, String rfc, String phone, String email) {
        return jdbc.sql("""
                INSERT INTO supplier (name, rfc, phone, email) VALUES (:n, :r, :p, :e) RETURNING id
                """)
                .param("n", name).param("r", rfc).param("p", phone).param("e", email)
                .query(Long.class).single();
    }

    /**
     * Registra una compra recibida: aumenta inventario y (si es a crédito) genera cuenta por pagar.
     *
     * @return id de la compra
     */
    @Transactional
    public long receivePurchase(long supplierId, long branchId, String invoiceRef,
                                boolean onCredit, List<PurchaseLineInput> lines, String actor) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("La compra debe tener al menos un renglón");
        }

        BigDecimal total = lines.stream()
                .map(l -> l.unitCost().multiply(l.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long purchaseId = jdbc.sql("""
                INSERT INTO purchase (supplier_id, branch_id, invoice_ref, total, on_credit, status)
                VALUES (:sup, :branch, :ref, :total, :credit, 'RECEIVED')
                RETURNING id
                """)
                .param("sup", supplierId).param("branch", branchId).param("ref", invoiceRef)
                .param("total", total).param("credit", onCredit)
                .query(Long.class).single();

        for (PurchaseLineInput line : lines) {
            BigDecimal lineTotal = line.unitCost().multiply(line.quantity());
            jdbc.sql("""
                    INSERT INTO purchase_line (purchase_id, product_id, quantity, unit_cost, line_total)
                    VALUES (:pur, :prod, :qty, :cost, :total)
                    """)
                    .param("pur", purchaseId).param("prod", line.productId())
                    .param("qty", line.quantity()).param("cost", line.unitCost())
                    .param("total", lineTotal)
                    .update();

            // Entrada al inventario.
            inventoryPort.applyMovement(line.productId(), branchId, MovementType.PURCHASE,
                    line.quantity(), "PURCHASE:" + purchaseId, actor);
        }

        // Cuenta por pagar si es a crédito.
        if (onCredit) {
            jdbc.sql("""
                    INSERT INTO account_payable (supplier_id, purchase_id, amount, status)
                    VALUES (:sup, :pur, :amount, 'OPEN')
                    """)
                    .param("sup", supplierId).param("pur", purchaseId).param("amount", total)
                    .update();
        }

        return purchaseId;
    }

    /** Abona a una cuenta por pagar; la marca PAID si se salda. */
    @Transactional
    public void payPayable(long payableId, BigDecimal amount) {
        jdbc.sql("""
                UPDATE account_payable
                SET paid = paid + :amt,
                    status = CASE WHEN paid + :amt >= amount THEN 'PAID' ELSE 'OPEN' END
                WHERE id = :id
                """)
                .param("id", payableId).param("amt", amount)
                .update();
    }

    /**
     * Renglón de compra.
     *
     * @param productId producto
     * @param quantity  cantidad recibida
     * @param unitCost  costo unitario
     */
    public record PurchaseLineInput(long productId, BigDecimal quantity, BigDecimal unitCost) {
    }
}
