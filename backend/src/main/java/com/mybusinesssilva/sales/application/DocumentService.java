package com.mybusinesssilva.sales.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Bitácora de comprobantes impresos (evidencia). Registra cada comprobante que respalda un
 * movimiento de dinero o mercancía (corte de caja, movimiento de efectivo, devolución, traspaso,
 * venta) para que el dueño pueda consultarlos clasificados y auditar el negocio. Cero pérdidas:
 * todo movimiento sensible deja evidencia consultable e imprimible.
 */
@Service
public class DocumentService {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public DocumentService(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** Registra un comprobante impreso/generado. Devuelve su id. */
    @Transactional
    public long record(String docType, String folio, String title, Long shiftId, Long branchId,
                       BigDecimal amount, String actor, boolean reprint, Map<String, ?> payload) {
        String json = payload == null ? null : toJson(payload);
        return jdbc.sql("""
                INSERT INTO document_print
                    (doc_type, folio, title, shift_id, branch_id, amount, actor, reprint, payload)
                VALUES (:type, :folio, :title, :shift, :branch, :amount, :actor, :reprint, CAST(:payload AS jsonb))
                RETURNING id
                """)
                .param("type", docType)
                .param("folio", folio)
                .param("title", title)
                .param("shift", shiftId)
                .param("branch", branchId)
                .param("amount", amount)
                .param("actor", actor)
                .param("reprint", reprint)
                .param("payload", json)
                .query(Long.class)
                .single();
    }

    /** Lista comprobantes con filtros de tipo, cajero y rango de fechas (para el dueño/admin). */
    public List<Map<String, Object>> list(String docType, String actor, LocalDate from, LocalDate to, int limit) {
        int max = limit <= 0 ? 200 : Math.min(limit, 1000);
        return jdbc.sql("""
                SELECT id, doc_type, folio, title, shift_id, branch_id, amount, actor, reprint, created_at
                FROM document_print
                WHERE (CAST(:type AS varchar) IS NULL OR doc_type = :type)
                  AND (CAST(:actor AS varchar) IS NULL OR actor ILIKE :actorLike)
                  AND (CAST(:from AS date) IS NULL OR created_at::date >= :from)
                  AND (CAST(:to AS date) IS NULL OR created_at::date <= :to)
                ORDER BY created_at DESC LIMIT :max
                """)
                .param("type", docType == null || docType.isBlank() ? null : docType)
                .param("actor", actor == null || actor.isBlank() ? null : actor)
                .param("actorLike", "%" + (actor == null ? "" : actor) + "%")
                .param("from", from)
                .param("to", to)
                .param("max", max)
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("docType", rs.getString("doc_type"));
                    m.put("folio", rs.getString("folio"));
                    m.put("title", rs.getString("title"));
                    m.put("shiftId", (Object) rs.getObject("shift_id"));
                    m.put("branchId", (Object) rs.getObject("branch_id"));
                    m.put("amount", rs.getBigDecimal("amount"));
                    m.put("actor", rs.getString("actor"));
                    m.put("reprint", rs.getBoolean("reprint"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();
    }

    /** Detalle (payload) de un comprobante, para reimprimirlo tal cual. */
    public Map<String, Object> detail(long id) {
        return jdbc.sql("SELECT id, doc_type, folio, title, amount, actor, payload, created_at FROM document_print WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("docType", rs.getString("doc_type"));
                    m.put("folio", rs.getString("folio"));
                    m.put("title", rs.getString("title"));
                    m.put("amount", rs.getBigDecimal("amount"));
                    m.put("actor", rs.getString("actor"));
                    m.put("payload", parseJson(rs.getString("payload")));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .optional()
                .orElseGet(java.util.LinkedHashMap::new);
    }

    /** Resumen por tipo de documento en un rango (para el panel del dueño). */
    public List<Map<String, Object>> summaryByType(LocalDate from, LocalDate to) {
        return jdbc.sql("""
                SELECT doc_type, COUNT(*) AS count, COALESCE(SUM(amount),0) AS total
                FROM document_print
                WHERE (CAST(:from AS date) IS NULL OR created_at::date >= :from)
                  AND (CAST(:to AS date) IS NULL OR created_at::date <= :to)
                GROUP BY doc_type ORDER BY doc_type
                """)
                .param("from", from).param("to", to)
                .query((rs, n) -> Map.<String, Object>of(
                        "docType", rs.getString("doc_type"),
                        "count", rs.getLong("count"),
                        "total", rs.getBigDecimal("total")))
                .list();
    }

    private String toJson(Map<String, ?> m) {
        try {
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            return null;
        }
    }

    private Object parseJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
