package com.mybusinesssilva.invoicing.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Consultas de comprobantes: lista de facturas y lista de recibos de pago (complementos), con
 * filtros de fecha y de cliente. Corresponde a las pantallas "Lista de facturas" y "Consultar
 * recibos de pago".
 */
@Service
public class InvoiceQueryService {

    private final JdbcClient jdbc;

    public InvoiceQueryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Lista CFDI con filtros. {@code kind} filtra el tipo (INVOICE/GLOBAL/PAYMENT) o null = todos.
     */
    public List<Map<String, Object>> listInvoices(String kind, LocalDate from, LocalDate to,
                                                  String clientQuery, int limit) {
        int max = limit <= 0 ? 200 : Math.min(limit, 1000);
        String cq = clientQuery == null ? "" : clientQuery.trim();
        return jdbc.sql("""
                SELECT id, doc_type, kind, series, folio, receiver_rfc, receiver_name,
                       subtotal, tax, total, currency, payment_form, payment_method,
                       status, uuid, has_carta_porte, created_at, stamped_at, canceled_at, stamp_error
                FROM cfdi
                WHERE (CAST(:kind AS varchar) IS NULL OR kind = :kind)
                  AND (CAST(:from AS date) IS NULL OR created_at::date >= :from)
                  AND (CAST(:to AS date) IS NULL OR created_at::date <= :to)
                  AND (:cq = '' OR receiver_rfc ILIKE :like OR receiver_name ILIKE :like)
                ORDER BY created_at DESC LIMIT :max
                """)
                .param("kind", kind)
                .param("from", from)
                .param("to", to)
                .param("cq", cq)
                .param("like", "%" + cq + "%")
                .param("max", max)
                .query(this::mapCfdi)
                .list();
    }

    private Map<String, Object> mapCfdi(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", rs.getLong("id"));
        m.put("docType", rs.getString("doc_type"));
        m.put("kind", rs.getString("kind"));
        m.put("series", rs.getString("series"));
        m.put("folio", (Object) rs.getObject("folio"));
        m.put("receiverRfc", rs.getString("receiver_rfc"));
        m.put("receiverName", rs.getString("receiver_name"));
        m.put("subtotal", rs.getBigDecimal("subtotal"));
        m.put("tax", rs.getBigDecimal("tax"));
        m.put("total", rs.getBigDecimal("total"));
        m.put("currency", rs.getString("currency"));
        m.put("paymentForm", rs.getString("payment_form"));
        m.put("paymentMethod", rs.getString("payment_method"));
        m.put("status", rs.getString("status"));
        m.put("uuid", rs.getString("uuid"));
        m.put("hasCartaPorte", rs.getBoolean("has_carta_porte"));
        m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
        m.put("stampedAt", rs.getObject("stamped_at", java.time.OffsetDateTime.class));
        m.put("canceledAt", rs.getObject("canceled_at", java.time.OffsetDateTime.class));
        m.put("error", rs.getString("stamp_error"));
        return m;
    }

    /** Conceptos de un CFDI (para el detalle / "Ver documentos relacionados"). */
    public List<Map<String, Object>> conceptsOf(long cfdiId) {
        return jdbc.sql("""
                SELECT sat_prod_serv, sat_unit, description, quantity, unit_price, amount, discount, tax_object
                FROM cfdi_concept WHERE cfdi_id = :id ORDER BY id
                """)
                .param("id", cfdiId)
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("satProdServ", rs.getString("sat_prod_serv"));
                    m.put("satUnit", rs.getString("sat_unit"));
                    m.put("description", rs.getString("description"));
                    m.put("quantity", rs.getBigDecimal("quantity"));
                    m.put("unitPrice", rs.getBigDecimal("unit_price"));
                    m.put("amount", rs.getBigDecimal("amount"));
                    m.put("discount", rs.getBigDecimal("discount"));
                    m.put("taxObject", rs.getString("tax_object"));
                    return m;
                })
                .list();
    }

    /** Lista recibos de pago (complementos Pagos 2.0) con filtros. */
    public List<Map<String, Object>> listPayments(LocalDate from, LocalDate to, String clientQuery, int limit) {
        int max = limit <= 0 ? 200 : Math.min(limit, 1000);
        String cq = clientQuery == null ? "" : clientQuery.trim();
        return jdbc.sql("""
                SELECT pc.id, pc.series, pc.folio, pc.paid_amount, pc.payment_form, pc.payment_date,
                       pc.currency, pc.bank, pc.operation_no, pc.uuid, pc.status, pc.created_at,
                       c.receiver_name, c.receiver_rfc, c.uuid AS invoice_uuid
                FROM cfdi_payment_complement pc
                LEFT JOIN cfdi c ON c.id = pc.cfdi_id
                WHERE (CAST(:from AS date) IS NULL OR pc.payment_date >= :from)
                  AND (CAST(:to AS date) IS NULL OR pc.payment_date <= :to)
                  AND (:cq = '' OR c.receiver_rfc ILIKE :like OR c.receiver_name ILIKE :like)
                ORDER BY pc.created_at DESC LIMIT :max
                """)
                .param("from", from)
                .param("to", to)
                .param("cq", cq)
                .param("like", "%" + cq + "%")
                .param("max", max)
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("series", rs.getString("series"));
                    m.put("folio", (Object) rs.getObject("folio"));
                    m.put("paidAmount", rs.getBigDecimal("paid_amount"));
                    m.put("paymentForm", rs.getString("payment_form"));
                    m.put("paymentDate", rs.getObject("payment_date", java.time.LocalDate.class));
                    m.put("currency", rs.getString("currency"));
                    m.put("bank", rs.getString("bank"));
                    m.put("operationNo", rs.getString("operation_no"));
                    m.put("uuid", rs.getString("uuid"));
                    m.put("status", rs.getString("status"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    m.put("receiverName", rs.getString("receiver_name"));
                    m.put("receiverRfc", rs.getString("receiver_rfc"));
                    m.put("invoiceUuid", rs.getString("invoice_uuid"));
                    return m;
                })
                .list();
    }

    /**
     * Facturas a crédito con saldo pendiente (para "Documentos pendientes de pago" al generar un
     * recibo). Se consideran CFDI timbrados con método PPD.
     */
    public List<Map<String, Object>> pendingForPayment(String clientQuery) {
        String cq = clientQuery == null ? "" : clientQuery.trim();
        return jdbc.sql("""
                SELECT c.id, c.series, c.folio, c.uuid, c.total, c.currency, c.created_at,
                       c.receiver_name, c.receiver_rfc,
                       COALESCE((SELECT SUM(pc.paid_amount) FROM cfdi_payment_complement pc WHERE pc.cfdi_id = c.id), 0) AS paid
                FROM cfdi c
                WHERE c.status = 'STAMPED' AND c.payment_method = 'PPD'
                  AND (:cq = '' OR c.receiver_rfc ILIKE :like OR c.receiver_name ILIKE :like)
                ORDER BY c.created_at DESC LIMIT 200
                """)
                .param("cq", cq)
                .param("like", "%" + cq + "%")
                .query((rs, n) -> {
                    java.math.BigDecimal total = rs.getBigDecimal("total");
                    java.math.BigDecimal paid = rs.getBigDecimal("paid");
                    java.math.BigDecimal balance = total.subtract(paid);
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("series", rs.getString("series"));
                    m.put("folio", (Object) rs.getObject("folio"));
                    m.put("uuid", rs.getString("uuid"));
                    m.put("total", total);
                    m.put("paid", paid);
                    m.put("balance", balance);
                    m.put("currency", rs.getString("currency"));
                    m.put("receiverName", rs.getString("receiver_name"));
                    m.put("receiverRfc", rs.getString("receiver_rfc"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();
    }
}
