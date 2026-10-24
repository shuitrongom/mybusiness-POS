package com.mybusinesssilva.support.adapters.in.rest;

import com.mybusinesssilva.platform.security.AuthenticatedUser;
import com.mybusinesssilva.support.adapters.in.rest.dto.ChangeStatusRequest;
import com.mybusinesssilva.support.adapters.in.rest.dto.TicketListItem;
import com.mybusinesssilva.support.adapters.in.rest.dto.TicketView;
import com.mybusinesssilva.support.domain.model.TicketStatus;
import com.mybusinesssilva.support.domain.model.TicketType;
import com.mybusinesssilva.support.domain.port.in.Actor;
import com.mybusinesssilva.support.domain.port.in.ChangeTicketStatusUseCase;
import com.mybusinesssilva.support.domain.port.in.GetSupportTicketUseCase;
import com.mybusinesssilva.support.domain.port.in.ListSupportTicketsUseCase;
import com.mybusinesssilva.support.domain.port.in.RequesterScope;
import com.mybusinesssilva.support.domain.port.in.TicketQuery;
import com.mybusinesssilva.platform.util.DeviceDetector;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bandeja global del super-admin: ve y gestiona tickets de TODOS los negocios. Todos los endpoints
 * exigen {@code hasRole('SUPER_ADMIN')}.
 *
 * <ul>
 * <li>{@code GET /} — lista global con filtros (incluye {@code businessId}).</li>
 * <li>{@code GET /{publicId}} — detalle.</li>
 * <li>{@code GET /{publicId}/history} — timeline leído de {@code admin.audit_log_global}.</li>
 * <li>{@code PATCH /{publicId}/status} — cambiar estado.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/admin/support-tickets")
public class SuperAdminSupportController {

    private final ListSupportTicketsUseCase listUseCase;
    private final GetSupportTicketUseCase getUseCase;
    private final ChangeTicketStatusUseCase changeStatusUseCase;
    private final JdbcClient jdbc;

    public SuperAdminSupportController(ListSupportTicketsUseCase listUseCase,
                                       GetSupportTicketUseCase getUseCase,
                                       ChangeTicketStatusUseCase changeStatusUseCase,
                                       JdbcClient jdbc) {
        this.listUseCase = listUseCase;
        this.getUseCase = getUseCase;
        this.changeStatusUseCase = changeStatusUseCase;
        this.jdbc = jdbc;
    }

    /** Lista todos los tickets de todos los negocios con filtros opcionales. */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<TicketListItem> list(
            @RequestParam(required = false) Long businessId,
            @RequestParam(required = false) TicketType type,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String q) {

        TicketQuery query = new TicketQuery(businessId, type, status, from, to, q);
        return listUseCase.list(query, new RequesterScope.SuperAdmin()).stream()
                .map(TicketListItem::from).toList();
    }

    /** Detalle de un ticket por {@code publicId}. */
    @GetMapping("/{publicId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public TicketView detail(@PathVariable UUID publicId) {
        return TicketView.from(getUseCase.get(publicId, new RequesterScope.SuperAdmin()));
    }

    /**
     * Historial de cambios de estado del ticket, leído de {@code admin.audit_log_global} con las
     * claves EXACTAS del §7 ({@code from}, {@code to}, {@code note}). El alta ({@code
     * SUPPORT_TICKET_CREATED}) no tiene {@code from}/{@code to}.
     */
    @GetMapping("/{publicId}/history")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<HistoryEntry> history(@PathVariable UUID publicId) {
        return jdbc.sql("""
                SELECT created_at                          AS at,
                       actor                               AS actor,
                       action                              AS action,
                       details ->> 'from'                  AS from_status,
                       details ->> 'to'                    AS to_status,
                       details ->> 'note'                  AS note
                FROM admin.audit_log_global
                WHERE target_type = 'support_ticket'
                  AND target_id = :publicId
                  AND action IN ('SUPPORT_TICKET_CREATED', 'SUPPORT_TICKET_STATUS_CHANGED')
                ORDER BY created_at
                """)
                .param("publicId", publicId.toString())
                .query(HistoryEntry.class)
                .list();
    }

    /** Cambia el estado de cualquier ticket. */
    @PatchMapping("/{publicId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public TicketView changeStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @PathVariable UUID publicId,
            @Valid @RequestBody ChangeStatusRequest request) {

        Actor actor = new Actor(
                user == null ? null : user.subject(),
                DeviceDetector.classify(userAgent),
                userAgent);
        return TicketView.from(changeStatusUseCase.changeStatus(
                publicId, request.status(), request.note(), actor, new RequesterScope.SuperAdmin()));
    }

    /**
     * Entrada del timeline de estado. {@code fromStatus}/{@code toStatus}/{@code note} son nulos para
     * el alta.
     */
    public record HistoryEntry(
            Instant at,
            String actor,
            String action,
            String fromStatus,
            String toStatus,
            String note) {
    }
}
