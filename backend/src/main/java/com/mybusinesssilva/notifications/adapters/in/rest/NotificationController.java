package com.mybusinesssilva.notifications.adapters.in.rest;

import com.mybusinesssilva.notifications.application.NotificationService;
import com.mybusinesssilva.notifications.domain.exception.NotificationNotFoundException;
import com.mybusinesssilva.notifications.domain.port.in.NotificationScope;
import com.mybusinesssilva.platform.security.AuthenticatedUser;
import com.mybusinesssilva.platform.tenant.BusinessResolver;
import com.mybusinesssilva.support.domain.exception.SupportValidationException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Centro de notificaciones del usuario autenticado. El scope se deriva SIEMPRE server-side a partir
 * del {@code AuthenticatedUser} (nunca del cliente): super-admin ve la bandeja global del equipo;
 * el resto ve solo sus propias notificaciones de su negocio.
 */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final BusinessResolver businessResolver;

    public NotificationController(NotificationService notificationService,
                                  BusinessResolver businessResolver) {
        this.notificationService = notificationService;
        this.businessResolver = businessResolver;
    }

    /** Bandeja completa (items + unreadCount) en un solo round-trip (N1). */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public InboxResponse inbox(@AuthenticationPrincipal AuthenticatedUser user) {
        NotificationService.InboxResult result = notificationService.inbox(resolveScope(user));
        List<NotificationView> items = result.items().stream().map(NotificationView::from).toList();
        return new InboxResponse(items, result.unreadCount());
    }

    /** Conteo ligero de no leídas para el poll de la campanita. */
    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("unreadCount", notificationService.unreadCount(resolveScope(user)));
    }

    /** Marca una notificación como leída. 204 si existe en scope, 404 si no. */
    @PostMapping("/{publicId}/read")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal AuthenticatedUser user,
                         @PathVariable UUID publicId) {
        int updated = notificationService.markRead(publicId, resolveScope(user));
        if (updated == 0) {
            throw new NotificationNotFoundException("Notificación no encontrada.");
        }
    }

    /** Marca todas las notificaciones del scope como leídas. */
    @PostMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal AuthenticatedUser user) {
        return Map.of("updated", notificationService.markAllRead(resolveScope(user)));
    }

    /** Deriva el scope server-side; nunca se construye a partir del cuerpo del cliente. */
    private NotificationScope resolveScope(AuthenticatedUser user) {
        if (user == null) {
            throw new SupportValidationException("No hay negocio en contexto.");
        }
        if (user.isSuperAdmin()) {
            return new NotificationScope.SuperAdmin();
        }
        String tenant = user.tenant();
        if (tenant == null || tenant.isBlank()) {
            throw new SupportValidationException("No hay negocio en contexto.");
        }
        long bid = businessResolver.resolveBusinessIdBySchema(tenant)
                .orElseThrow(() -> new SupportValidationException("Negocio no resuelto."));
        return new NotificationScope.User(bid, user.subject());
    }

    /** Respuesta de la bandeja: lista + conteo de no leídas. */
    public record InboxResponse(List<NotificationView> items, long unreadCount) {
    }
}
