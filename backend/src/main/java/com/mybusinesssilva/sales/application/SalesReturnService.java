package com.mybusinesssilva.sales.application;

import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.inventory.domain.port.in.InventoryPort;
import com.mybusinesssilva.sales.domain.port.out.SaleRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de DEVOLUCIONES.
 *
 * <p>Una devolución se persiste como documento (cabecera + renglones), ligada opcionalmente a la
 * venta original. Cuando hay venta origen, se valida que no se devuelva más de lo vendido menos
 * lo ya devuelto. En todos los casos reingresa el inventario y registra el reembolso.
 */
@Service
public class SalesReturnService {

    private final JdbcClient jdbc;
    private final SaleRepository saleRepository;
    private final InventoryPort inventoryPort;

    public SalesReturnService(JdbcClient jdbc, SaleRepository saleRepository,
                              InventoryPort inventoryPort) {
        this.jdbc = jdbc;
        this.saleRepository = saleRepository;
        this.inventoryPort = inventoryPort;
    }

    /**
     * Registra una devolución. Valida contra la venta origen (si se indica), persiste el
     * documento, reingresa inventario y devuelve el resultado.
     *
     * @param command datos de la devolución
     * @param actor   quién procesa
     * @return resultado con id, folio e importe reembolsado
     */
    @Transactional
    public ReturnResult register(ReturnCommand command, String actor) {
        if (command.items() == null || command.items().isEmpty()) {
            throw new IllegalArgumentException("La devolución debe tener al menos un producto");
        }

        long branchId = command.branchId();
        Long customerId = command.customerId();

        // Si hay venta origen, valida existencia, sucursal y que no se exceda lo vendido.
        if (command.saleId() != null) {
            var detail = saleRepository.findDetail(command.saleId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No existe la venta " + command.saleId()));
            if ("VOIDED".equals(detail.status())) {
                throw new IllegalArgumentException("La venta ya fue cancelada; no admite devolución");
            }
            branchId = detail.branchId();
            customerId = detail.customerId();
            for (ReturnItem item : command.items()) {
                var soldLine = detail.lines().stream()
                        .filter(l -> l.productId() == item.productId())
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "El producto " + item.productId() + " no pertenece a la venta"));
                BigDecimal alreadyReturned = saleRepository.returnedQuantity(
                        command.saleId(), item.productId());
                BigDecimal remaining = soldLine.quantity().subtract(alreadyReturned);
                if (item.quantity().compareTo(remaining) > 0) {
                    throw new IllegalArgumentException(
                            "No se puede devolver " + item.quantity() + " de '" + soldLine.description()
                            + "': solo quedan " + remaining + " por devolver");
                }
            }
        }

        BigDecimal total = command.items().stream()
                .map(i -> i.unitPrice().multiply(i.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long returnId = jdbc.sql("""
                INSERT INTO sales_return
                    (sale_id, branch_id, customer_id, reason, total, refund_method, processed_by)
                VALUES (:sale, :branch, :customer, :reason, :total, :refund, :actor)
                RETURNING id
                """)
                .param("sale", command.saleId())
                .param("branch", branchId)
                .param("customer", customerId)
                .param("reason", command.reason())
                .param("total", total)
                .param("refund", command.refundMethod() == null ? "CASH" : command.refundMethod())
                .param("actor", actor)
                .query(Long.class).single();

        jdbc.sql("UPDATE sales_return SET folio = :folio WHERE id = :id")
                .param("folio", "D-" + returnId).param("id", returnId).update();

        for (ReturnItem item : command.items()) {
            jdbc.sql("""
                    INSERT INTO sales_return_line
                        (return_id, product_id, description, quantity, unit_price, line_total)
                    VALUES (:ret, :product, :desc, :qty, :price, :total)
                    """)
                    .param("ret", returnId)
                    .param("product", item.productId())
                    .param("desc", item.description() == null ? "" : item.description())
                    .param("qty", item.quantity())
                    .param("price", item.unitPrice())
                    .param("total", item.unitPrice().multiply(item.quantity()))
                    .update();

            // Reingresa el inventario del producto devuelto.
            inventoryPort.applyMovement(item.productId(), branchId, MovementType.RETURN,
                    item.quantity(), "RETURN:" + returnId, actor);
        }

        return new ReturnResult(returnId, "D-" + returnId, total);
    }

    /** Lista las devoluciones recientes (para el historial de la pantalla de devoluciones). */
    public List<java.util.Map<String, Object>> listRecent(int limit) {
        int max = limit <= 0 ? 50 : Math.min(limit, 200);
        return jdbc.sql("""
                SELECT r.id, r.folio, r.sale_id, r.total, r.refund_method, r.reason,
                       r.processed_by, r.created_at
                FROM sales_return r
                ORDER BY r.created_at DESC
                LIMIT :max
                """)
                .param("max", max)
                .query((rs, n) -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("folio", rs.getString("folio"));
                    m.put("saleId", rs.getObject("sale_id"));
                    m.put("total", rs.getBigDecimal("total"));
                    m.put("refundMethod", rs.getString("refund_method"));
                    m.put("reason", rs.getString("reason"));
                    m.put("processedBy", rs.getString("processed_by"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();
    }

    /** Datos de una devolución a registrar. */
    public record ReturnCommand(Long saleId, long branchId, Long customerId, String reason,
                                String refundMethod, List<ReturnItem> items) {
    }

    /** Renglón de la devolución. */
    public record ReturnItem(long productId, String description, BigDecimal quantity,
                             BigDecimal unitPrice) {
    }

    /** Resultado de registrar una devolución. */
    public record ReturnResult(long returnId, String folio, BigDecimal total) {
    }
}
