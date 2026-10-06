package com.mybusinesssilva.invoicing.application;

import com.mybusinesssilva.invoicing.domain.model.CfdiConcept;
import com.mybusinesssilva.invoicing.domain.model.ReceiverInfo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Conversión de documentos previos en facturas:
 *   - Remisiones a factura (notas de venta no fiscales).
 *   - Tickets a factura (ventas del punto de venta).
 *   - Factura de cierre / global: agrupa tickets + remisiones - devoluciones de un periodo en un
 *     solo CFDI al público en general.
 *
 * Reutiliza {@link InvoicingService} para emitir/timbrar, de modo que el flujo CFDI es único.
 */
@Service
public class RemissionService {

    private final JdbcClient jdbc;
    private final InvoicingService invoicingService;

    public RemissionService(JdbcClient jdbc, InvoicingService invoicingService) {
        this.jdbc = jdbc;
        this.invoicingService = invoicingService;
    }

    /** Lista remisiones abiertas (aún no facturadas). */
    public List<Map<String, Object>> openRemissions() {
        return jdbc.sql("""
                SELECT id, folio, customer_id, customer_name, subtotal, tax, total, created_at
                FROM remission WHERE status = 'OPEN' ORDER BY created_at DESC LIMIT 300
                """)
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("folio", rs.getString("folio"));
                    m.put("customerId", (Object) rs.getObject("customer_id"));
                    m.put("customerName", rs.getString("customer_name"));
                    m.put("subtotal", rs.getBigDecimal("subtotal"));
                    m.put("tax", rs.getBigDecimal("tax"));
                    m.put("total", rs.getBigDecimal("total"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();
    }

    /** Convierte una remisión en factura, con los datos fiscales del cliente. */
    @Transactional
    public InvoicingService.InvoiceResult remissionToInvoice(long remissionId, ReceiverInfo receiver) {
        List<CfdiConcept> concepts = jdbc.sql("""
                SELECT description, quantity, unit_price, amount FROM remission_line
                WHERE remission_id = :id ORDER BY id
                """)
                .param("id", remissionId)
                .query((rs, n) -> new CfdiConcept(
                        "01010101", "H87", rs.getString("description"),
                        rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("amount")))
                .list();
        if (concepts.isEmpty()) {
            throw new IllegalArgumentException("La remisión no tiene renglones o no existe");
        }
        InvoicingService.InvoiceResult result = invoicingService.issueInvoice(
                null, receiver, concepts, "remission-" + remissionId);
        jdbc.sql("UPDATE remission SET status = 'INVOICED', invoiced_cfdi_id = :cfdi WHERE id = :id")
                .param("cfdi", result.cfdiId()).param("id", remissionId).update();
        return result;
    }

    /** Tickets (ventas) sin factura, para convertir a factura. */
    public List<Map<String, Object>> uninvoicedSales(LocalDate from, LocalDate to) {
        return jdbc.sql("""
                SELECT s.id, s.folio, s.customer_id, s.subtotal, s.tax, s.total, s.created_at
                FROM sale s
                WHERE s.status = 'COMPLETED'
                  AND NOT EXISTS (SELECT 1 FROM cfdi c WHERE c.sale_id = s.id AND c.status IN ('STAMPED','PENDING'))
                  AND (CAST(:from AS date) IS NULL OR s.created_at::date >= :from)
                  AND (CAST(:to AS date) IS NULL OR s.created_at::date <= :to)
                ORDER BY s.created_at DESC LIMIT 300
                """)
                .param("from", from)
                .param("to", to)
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("folio", rs.getString("folio"));
                    m.put("customerId", (Object) rs.getObject("customer_id"));
                    m.put("subtotal", rs.getBigDecimal("subtotal"));
                    m.put("tax", rs.getBigDecimal("tax"));
                    m.put("total", rs.getBigDecimal("total"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();
    }

    /** Convierte un ticket (venta) en factura, con los datos fiscales del cliente. */
    @Transactional
    public InvoicingService.InvoiceResult ticketToInvoice(long saleId, ReceiverInfo receiver) {
        List<CfdiConcept> concepts = jdbc.sql("""
                SELECT COALESCE(p.name, 'Venta') AS description, sl.quantity, sl.unit_price,
                       (sl.quantity * sl.unit_price) AS amount,
                       COALESCE(p.sat_prod_serv, '01010101') AS ps, COALESCE(p.sat_unit, 'H87') AS u
                FROM sale_line sl
                LEFT JOIN product p ON p.id = sl.product_id
                WHERE sl.sale_id = :id
                """)
                .param("id", saleId)
                .query((rs, n) -> new CfdiConcept(
                        rs.getString("ps"), rs.getString("u"), rs.getString("description"),
                        rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("amount")))
                .list();
        if (concepts.isEmpty()) {
            throw new IllegalArgumentException("La venta no tiene renglones o no existe");
        }
        return invoicingService.issueInvoice(saleId, receiver, concepts, "sale-" + saleId);
    }

    /**
     * Factura de cierre (global): agrupa el total de tickets del periodo (menos devoluciones) y
     * emite un CFDI global al público en general. Devuelve el resultado del timbrado.
     */
    @Transactional
    public ClosingResult closingInvoice(LocalDate from, LocalDate to) {
        BigDecimal tickets = jdbc.sql("""
                SELECT COALESCE(SUM(total), 0) FROM sale
                WHERE status = 'COMPLETED' AND created_at::date BETWEEN :from AND :to
                  AND NOT EXISTS (SELECT 1 FROM cfdi c WHERE c.sale_id = sale.id AND c.status = 'STAMPED')
                """)
                .param("from", from).param("to", to).query(BigDecimal.class).single();

        BigDecimal remissions = jdbc.sql("""
                SELECT COALESCE(SUM(total), 0) FROM remission
                WHERE status = 'OPEN' AND created_at::date BETWEEN :from AND :to
                """)
                .param("from", from).param("to", to).query(BigDecimal.class).single();

        // Devoluciones del periodo (si existe la tabla de devoluciones de venta).
        BigDecimal returns = BigDecimal.ZERO;
        try {
            returns = jdbc.sql("""
                    SELECT COALESCE(SUM(total), 0) FROM sales_return
                    WHERE created_at::date BETWEEN :from AND :to
                    """)
                    .param("from", from).param("to", to).query(BigDecimal.class).single();
        } catch (RuntimeException ignored) {
            // La tabla puede no existir en algunos tenants; se ignora.
        }

        BigDecimal net = tickets.add(remissions).subtract(returns).max(BigDecimal.ZERO);
        String desc = "Venta global del periodo " + from + " al " + to;
        InvoicingService.InvoiceResult result = invoicingService.issueGlobalInvoice(net, desc);
        return new ClosingResult(tickets, remissions, returns, net, result);
    }

    /** Resultado del cálculo + timbrado de la factura de cierre. */
    public record ClosingResult(BigDecimal tickets, BigDecimal remissions, BigDecimal returns,
                                BigDecimal net, InvoicingService.InvoiceResult invoice) {
    }
}
