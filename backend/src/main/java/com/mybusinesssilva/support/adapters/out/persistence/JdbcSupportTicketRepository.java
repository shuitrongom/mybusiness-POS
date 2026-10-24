package com.mybusinesssilva.support.adapters.out.persistence;

import com.mybusinesssilva.support.domain.model.SupportTicket;
import com.mybusinesssilva.support.domain.model.SupportTicketImage;
import com.mybusinesssilva.support.domain.model.TicketStatus;
import com.mybusinesssilva.support.domain.model.TicketType;
import com.mybusinesssilva.support.domain.port.in.RequesterScope;
import com.mybusinesssilva.support.domain.port.in.TicketQuery;
import com.mybusinesssilva.support.domain.port.out.SupportTicketRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC del repositorio del buzón de soporte sobre el schema {@code admin}.
 *
 * <p>Usa {@link JdbcClient} con parámetros nombrados (anti-inyección). El aislamiento por negocio se
 * aplica traduciendo el {@link RequesterScope} a cláusulas {@code WHERE} (el schema admin no usa RLS).
 */
@Repository
public class JdbcSupportTicketRepository implements SupportTicketRepository {

    private final JdbcClient jdbc;

    public JdbcSupportTicketRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public SupportTicket insert(SupportTicket ticket) {
        TicketRow row = jdbc.sql("""
                INSERT INTO admin.support_ticket (
                    business_id, tenant_schema, reporter_user_id, reporter_email, reporter_role,
                    type, title, description, screen_path, app_version, user_agent, device, status)
                VALUES (
                    :businessId, :tenantSchema, :reporterUserId, :reporterEmail, :reporterRole,
                    :type, :title, :description, :screenPath, :appVersion, :userAgent, :device, :status)
                RETURNING id, public_id, business_id, tenant_schema, reporter_user_id, reporter_email,
                          reporter_role, type, title, description, screen_path, app_version, user_agent,
                          device, status, resolution_note, created_at, updated_at, resolved_at, resolved_by
                """)
                .param("businessId", ticket.businessId())
                .param("tenantSchema", ticket.tenantSchema())
                .param("reporterUserId", ticket.reporterUserId())
                .param("reporterEmail", ticket.reporterEmail())
                .param("reporterRole", ticket.reporterRole())
                .param("type", ticket.type() == null ? null : ticket.type().name())
                .param("title", ticket.title())
                .param("description", ticket.description())
                .param("screenPath", ticket.screenPath())
                .param("appVersion", ticket.appVersion())
                .param("userAgent", ticket.userAgent())
                .param("device", ticket.device() == null ? "unknown" : ticket.device())
                .param("status", (ticket.status() == null ? TicketStatus.NUEVO : ticket.status()).name())
                .query(TicketRow.class)
                .single();
        return toTicket(row, List.of());
    }

    @Override
    public void insertImages(long ticketId, List<SupportTicketImage> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        for (SupportTicketImage img : images) {
            jdbc.sql("""
                    INSERT INTO admin.support_ticket_image
                        (ticket_id, cloudinary_url, public_id, width, height, bytes)
                    VALUES (:ticketId, :url, :publicId, :width, :height, :bytes)
                    """)
                    .param("ticketId", ticketId)
                    .param("url", img.url())
                    .param("publicId", img.publicId())
                    .param("width", img.width())
                    .param("height", img.height())
                    .param("bytes", img.bytes())
                    .update();
        }
    }

    @Override
    public Optional<SupportTicket> findByPublicId(UUID publicId) {
        Optional<TicketRow> row = jdbc.sql("""
                SELECT id, public_id, business_id, tenant_schema, reporter_user_id, reporter_email,
                       reporter_role, type, title, description, screen_path, app_version, user_agent,
                       device, status, resolution_note, created_at, updated_at, resolved_at, resolved_by
                FROM admin.support_ticket
                WHERE public_id = :publicId
                """)
                .param("publicId", publicId)
                .query(TicketRow.class)
                .optional();
        return row.map(r -> toTicket(r, findImages(r.id())));
    }

    @Override
    public List<SupportTicket> find(TicketQuery query, RequesterScope scope) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, public_id, business_id, tenant_schema, reporter_user_id, reporter_email,
                       reporter_role, type, title, description, screen_path, app_version, user_agent,
                       device, status, resolution_note, created_at, updated_at, resolved_at, resolved_by
                FROM admin.support_ticket
                WHERE 1 = 1
                """);
        Map<String, Object> params = new HashMap<>();

        // Barrera de aislamiento por alcance.
        switch (scope) {
            case RequesterScope.SuperAdmin ignored -> {
                // sin filtro de negocio; el businessId del query es solo filtro de UI
            }
            case RequesterScope.BusinessOwner owner -> {
                sql.append(" AND business_id = :scopeBusinessId");
                params.put("scopeBusinessId", owner.businessId());
            }
            case RequesterScope.Reporter reporter -> {
                sql.append(" AND business_id = :scopeBusinessId AND reporter_user_id = :scopeReporter");
                params.put("scopeBusinessId", reporter.businessId());
                params.put("scopeReporter", reporter.reporterUserId());
            }
        }

        if (query != null) {
            if (scope instanceof RequesterScope.SuperAdmin && query.businessId() != null) {
                sql.append(" AND business_id = :filterBusinessId");
                params.put("filterBusinessId", query.businessId());
            }
            if (query.type() != null) {
                sql.append(" AND type = :type");
                params.put("type", query.type().name());
            }
            if (query.status() != null) {
                sql.append(" AND status = :status");
                params.put("status", query.status().name());
            }
            if (query.from() != null) {
                sql.append(" AND created_at >= :from");
                params.put("from", Timestamp.from(query.from()));
            }
            if (query.to() != null) {
                sql.append(" AND created_at <= :to");
                params.put("to", Timestamp.from(query.to()));
            }
            if (query.q() != null && !query.q().isBlank()) {
                sql.append(" AND (title ILIKE :q OR description ILIKE :q)");
                params.put("q", "%" + query.q().trim() + "%");
            }
        }

        sql.append(" ORDER BY created_at DESC");

        JdbcClient.StatementSpec spec = jdbc.sql(sql.toString());
        for (Map.Entry<String, Object> e : params.entrySet()) {
            spec = spec.param(e.getKey(), e.getValue());
        }
        List<TicketRow> rows = spec.query(TicketRow.class).list();
        return rows.stream().map(r -> toTicket(r, List.of())).toList();
    }

    @Override
    public SupportTicket updateStatusByPublicId(UUID publicId, TicketStatus status, String note,
                                                 String resolvedBy, Instant resolvedAt) {
        jdbc.sql("""
                UPDATE admin.support_ticket
                SET status = :status,
                    resolution_note = COALESCE(:note, resolution_note),
                    resolved_at = :resolvedAt,
                    resolved_by = :resolvedBy,
                    updated_at = now()
                WHERE public_id = :publicId
                """)
                .param("publicId", publicId)
                .param("status", status == null ? null : status.name())
                .param("note", note)
                .param("resolvedAt", resolvedAt == null ? null : Timestamp.from(resolvedAt))
                .param("resolvedBy", resolvedBy)
                .update();
        return findByPublicId(publicId).orElseThrow();
    }

    @Override
    public Optional<Long> resolveBusinessIdBySchema(String tenantSchema) {
        return jdbc.sql("SELECT id FROM admin.business WHERE schema_name = :schema")
                .param("schema", tenantSchema)
                .query(Long.class)
                .optional();
    }

    private List<SupportTicketImage> findImages(long ticketId) {
        List<ImageRow> rows = jdbc.sql("""
                SELECT cloudinary_url, public_id, width, height, bytes
                FROM admin.support_ticket_image
                WHERE ticket_id = :ticketId
                ORDER BY id
                """)
                .param("ticketId", ticketId)
                .query(ImageRow.class)
                .list();
        List<SupportTicketImage> images = new ArrayList<>(rows.size());
        for (ImageRow r : rows) {
            images.add(new SupportTicketImage(
                    r.cloudinaryUrl(), r.publicId(), r.width(), r.height(), r.bytes()));
        }
        return images;
    }

    private SupportTicket toTicket(TicketRow r, List<SupportTicketImage> images) {
        return new SupportTicket(
                r.id(),
                r.publicId(),
                r.businessId(),
                r.tenantSchema(),
                r.reporterUserId(),
                r.reporterEmail(),
                r.reporterRole(),
                r.type() == null ? null : TicketType.valueOf(r.type()),
                r.title(),
                r.description(),
                r.screenPath(),
                r.appVersion(),
                r.userAgent(),
                r.device(),
                r.status() == null ? null : TicketStatus.valueOf(r.status()),
                r.resolutionNote(),
                toInstant(r.createdAt()),
                toInstant(r.updatedAt()),
                toInstant(r.resolvedAt()),
                r.resolvedBy(),
                images);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    /** Proyección de fila de {@code admin.support_ticket}. */
    public record TicketRow(
            Long id,
            UUID publicId,
            Long businessId,
            String tenantSchema,
            String reporterUserId,
            String reporterEmail,
            String reporterRole,
            String type,
            String title,
            String description,
            String screenPath,
            String appVersion,
            String userAgent,
            String device,
            String status,
            String resolutionNote,
            Timestamp createdAt,
            Timestamp updatedAt,
            Timestamp resolvedAt,
            String resolvedBy) {
    }

    /** Proyección de fila de {@code admin.support_ticket_image}. */
    public record ImageRow(
            String cloudinaryUrl,
            String publicId,
            Integer width,
            Integer height,
            Long bytes) {
    }
}
