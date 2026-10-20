package com.mybusinesssilva.sales.application;

import com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase;
import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.inventory.domain.port.in.InventoryPort;
import com.mybusinesssilva.sales.domain.port.out.CustomerCreditPort;
import com.mybusinesssilva.sales.domain.port.out.SaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de DEVOLUCIONES.
 *
 * <p>
 * Una devolución se persiste como documento (cabecera + renglones), SIEMPRE
 * ligada a su venta
 * origen (no hay devolución libre sin ticket). Se valida que no se devuelva más
 * de lo vendido
 * menos lo ya devuelto, el importe se recomputa desde el renglón real de la
 * venta (no del precio
 * que mande el request), y la operación amarra de punta a punta: reingresa el
 * inventario (a stock
 * normal o a existencia de merma según el renglón), reembolsa (efectivo con
 * salida de caja ligada
 * al turno, o nota de crédito que reduce la cuenta por cobrar) y queda todo en
 * una sola transacción.
 */
@Service
public class SalesReturnService {

    private final JdbcClient jdbc;
    private final SaleRepository saleRepository;
    private final InventoryPort inventoryPort;
    private final BakeryUseCase bakeryUseCase;
    private final CustomerCreditPort customerCreditPort;
    private final ShiftService shiftService;

    public SalesReturnService(JdbcClient jdbc, SaleRepository saleRepository,
            InventoryPort inventoryPort, BakeryUseCase bakeryUseCase,
            CustomerCreditPort customerCreditPort, ShiftService shiftService) {
        this.jdbc = jdbc;
        this.saleRepository = saleRepository;
        this.inventoryPort = inventoryPort;
        this.bakeryUseCase = bakeryUseCase;
        this.customerCreditPort = customerCreditPort;
        this.shiftService = shiftService;
    }

    /**
     * Registra una devolución REAL contra su venta origen: valida, recomputa el
     * importe desde la
     * venta, persiste el documento, reingresa inventario y aplica el reembolso
     * (efectivo o nota de
     * crédito). Todo en una transacción.
     *
     * @param command datos de la devolución
     * @param actor   quién procesa
     * @return resultado con id, folio e importe reembolsado
     */
    @Transactional
    public ReturnResult register(ReturnCommand command, String actor) {
        // (1) items no vacíos; consolidar por productId (sumar duplicados) y validar
        // cantidad > 0.
        if (command.items() == null || command.items().isEmpty()) {
            throw new IllegalArgumentException("La devolución debe tener al menos un producto");
        }
        Map<Long, ReturnItem> consolidated = new LinkedHashMap<>();
        for (ReturnItem item : command.items()) {
            if (item.quantity() == null || item.quantity().signum() <= 0) {
                throw new IllegalArgumentException(
                        "La cantidad a devolver debe ser mayor a cero");
            }
            consolidated.merge(item.productId(), item, (a, b) -> new ReturnItem(
                    a.productId(), a.description(), a.quantity().add(b.quantity()), a.unitPrice()));
        }

        // (2) exigir venta origen y cargar su detalle; rechazar venta cancelada.
        if (command.saleId() == null) {
            throw new IllegalArgumentException(
                    "La devolución requiere la venta origen (folio del ticket)");
        }
        long saleId = command.saleId();
        var detail = saleRepository.findDetail(saleId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la venta " + saleId));
        if ("VOIDED".equals(detail.status())) {
            throw new IllegalArgumentException("La venta ya fue cancelada; no admite devolución");
        }
        long branchId = detail.branchId();
        Long customerId = detail.customerId();

        // (3) MEDIA-1: bloquear la venta (FOR UPDATE) antes de leer remanentes, para
        // serializar devoluciones concurrentes de la misma venta.
        saleRepository.lockSale(saleId);

        // (4)+(5) por cada item consolidado: localizar su renglón vendido, validar el
        // remanente y recomputar el reembolso prorrateado desde line_total (ALTA-4).
        BigDecimal total = BigDecimal.ZERO;
        Map<Long, BigDecimal> lineRefund = new LinkedHashMap<>();
        for (ReturnItem item : consolidated.values()) {
            var soldLine = detail.lines().stream()
                    // Los renglones de artículo libre (productId null) no son devolvibles por
                    // producto; se omiten para no desreferenciar null al comparar.
                    .filter(l -> l.productId() != null && l.productId().equals(item.productId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "El producto " + item.productId() + " no pertenece a la venta"));

            BigDecimal alreadyReturned = saleRepository.returnedQuantity(saleId, item.productId());
            BigDecimal remaining = soldLine.quantity().subtract(alreadyReturned);
            if (item.quantity().compareTo(remaining) > 0) {
                throw new IllegalArgumentException(
                        "No se puede devolver " + item.quantity() + " de '" + soldLine.description()
                                + "': solo quedan " + remaining + " por devolver");
            }

            // ALTA-4: reembolso = precio unitario REAL del renglón (line_total / quantity)
            // × cantidad devuelta. Ignora item.unitPrice del request. Se acota a que lo
            // devuelto acumulado por renglón no exceda su line_total real.
            BigDecimal unitRefund = soldLine.lineTotal()
                    .divide(soldLine.quantity(), 2, RoundingMode.HALF_UP);
            BigDecimal refund = unitRefund.multiply(item.quantity());
            BigDecimal alreadyRefunded = unitRefund.multiply(alreadyReturned);
            if (alreadyRefunded.add(refund).compareTo(soldLine.lineTotal()) > 0) {
                // No reembolsar por encima de lo realmente cobrado en el renglón.
                refund = soldLine.lineTotal().subtract(alreadyRefunded).max(BigDecimal.ZERO);
            }
            lineRefund.put(item.productId(), refund);
            total = total.add(refund);
        }

        // (6) venta a crédito => el reembolso NO es en efectivo: forzar nota de
        // crédito.
        String refundMethod = command.refundMethod() == null ? "CASH" : command.refundMethod();
        if (detail.onCredit()) {
            refundMethod = "CREDIT_NOTE";
        }

        // (7) turno activo del cajero (para ligar el movimiento de caja al corte) e
        // insertar la cabecera de la devolución incluyendo shift_id.
        Long shiftId = saleRepository.activeShiftId(actor).orElse(null);

        long returnId = jdbc.sql("""
                INSERT INTO sales_return
                    (sale_id, branch_id, customer_id, reason, total, refund_method, processed_by, shift_id)
                VALUES (:sale, :branch, :customer, :reason, :total, :refund, :actor, :shift)
                RETURNING id
                """)
                .param("sale", saleId)
                .param("branch", branchId)
                .param("customer", customerId)
                .param("reason", command.reason())
                .param("total", total)
                .param("refund", refundMethod)
                .param("actor", actor)
                .param("shift", shiftId)
                .query(Long.class).single();

        jdbc.sql("UPDATE sales_return SET folio = :folio WHERE id = :id")
                .param("folio", "D-" + returnId).param("id", returnId).update();

        for (ReturnItem item : consolidated.values()) {
            BigDecimal refund = lineRefund.get(item.productId());
            BigDecimal unitPrice = item.quantity().signum() == 0
                    ? BigDecimal.ZERO
                    : refund.divide(item.quantity(), 2, RoundingMode.HALF_UP);

            // (8) renglón con precio y total REcomputados desde la venta real.
            jdbc.sql("""
                    INSERT INTO sales_return_line
                        (return_id, product_id, description, quantity, unit_price, line_total)
                    VALUES (:ret, :product, :desc, :qty, :price, :total)
                    """)
                    .param("ret", returnId)
                    .param("product", item.productId())
                    .param("desc", item.description() == null ? "" : item.description())
                    .param("qty", item.quantity())
                    .param("price", unitPrice)
                    .param("total", refund)
                    .update();

            // (9) ALTA-3: si el renglón se vendió como merma, reingresa a la EXISTENCIA DE
            // MERMA (returnWasteOnVoid); si no, reingresa al stock normal (RETURN).
            // MEDIA-4: el reingreso de un producto desactivado (soft-delete) NO truena: la
            // FK por id sigue existiendo (el soft-delete sólo marca active=false), así que
            // el movimiento de inventario se aplica igual.
            var soldLine = detail.lines().stream()
                    .filter(l -> l.productId() != null && l.productId().equals(item.productId()))
                    .findFirst().orElseThrow();
            if (soldLine.waste()) {
                bakeryUseCase.returnWasteOnVoid(item.productId(), branchId, item.quantity(),
                        actor, saleId);
            } else {
                inventoryPort.applyMovement(item.productId(), branchId, MovementType.RETURN,
                        item.quantity(), "RETURN:" + returnId, actor);
            }
        }

        if (total.signum() > 0) {
            if (detail.onCredit()) {
                // (10) ALTA-2: venta a crédito => abonar la cuenta por cobrar hasta el saldo
                // vigente, sin entregar efectivo. Si el saldo pendiente es menor que el total
                // (parte ya se cobró), el remanente NO se paga en efectivo: queda como nota a
                // favor del cliente (STORE_CREDIT implícito), coherente con "no se vende/saca
                // ni se reembolsa lo que no corresponde".
                customerCreditPort.reduceReceivableForSale(saleId, total);
            } else if ("CASH".equals(refundMethod) && shiftId != null) {
                // (11) ALTA-1: reembolso en efectivo => salida de caja (OUT) ligada al turno,
                // para que expectedCash del corte la reste sin tocar sus fórmulas.
                shiftService.recordCashMovement(shiftId, "OUT", total,
                        "Reembolso devolución D-" + returnId, actor);
            }
        }

        return new ReturnResult(returnId, "D-" + returnId, total);
    }

    /**
     * Lista las devoluciones recientes (para el historial de la pantalla de
     * devoluciones).
     */
    public List<java.util.Map<String, Object>> listRecent(int limit) {
        return listRecent(limit, null);
    }

    /**
     * Igual que {@link #listRecent(int)} pero filtrando las devoluciones por el
     * {@link ReportScope} (modo prueba) de su VENTA ORIGEN ({@code s.is_test}):
     * real/test/both. El scope ya viene resuelto por el controller;
     * null/desconocido
     * se trata como {@code both}.
     */
    public List<java.util.Map<String, Object>> listRecent(int limit, String scope) {
        int max = limit <= 0 ? 50 : Math.min(limit, 200);
        String testPredicate = ReportScope.resolve(scope == null ? "both" : scope, true)
                .sqlPredicate("s.is_test");
        return jdbc.sql("""
                SELECT r.id, r.folio, r.sale_id, r.total, r.refund_method, r.reason,
                       r.processed_by, r.created_at,
                       (SELECT COUNT(*) FROM sales_return_line rl WHERE rl.return_id = r.id)
                           AS item_count
                FROM sales_return r
                JOIN sale s ON s.id = r.sale_id
                WHERE 1 = 1""" + testPredicate + """
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
                    m.put("itemCount", rs.getInt("item_count"));
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
