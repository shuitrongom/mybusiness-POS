package com.mybusinesssilva.notifications.application;

import com.mybusinesssilva.notifications.domain.model.Notification;
import com.mybusinesssilva.notifications.domain.port.in.NotificationScope;
import com.mybusinesssilva.notifications.domain.port.out.NotificationRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Servicio del centro de notificaciones. Trunca título/cuerpo por code points, valida el tamaño del
 * payload serializado (≤ 4096 bytes UTF-8) y delega la persistencia/consulta en el repositorio.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final int TITLE_MAX_CP = 200;
    private static final int BODY_MAX_CP = 600;
    private static final int PAYLOAD_MAX_BYTES = 4096;
    private static final int INBOX_LIMIT = 50;

    private final NotificationRepository repo;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    /** Resultado de la bandeja: items + conteo de no leídas (un solo round-trip de lectura). */
    public record InboxResult(List<Notification> items, long unreadCount) {
    }

    /**
     * Crea una notificación: trunca title/body por code points (H-A9) y descarta el payload si
     * excede 4096 bytes UTF-8 (NFR-4/N3), persistiendo igual sin él.
     */
    public Notification create(Notification draft) {
        String title = limitCodePoints(draft.title(), TITLE_MAX_CP);
        String body = limitCodePoints(draft.body(), BODY_MAX_CP);
        var payload = draft.payload();
        if (payload != null && !payload.isEmpty() && !payloadWithinLimit(payload)) {
            log.warn("[NOTIF] payload excede {} bytes UTF-8; se persiste sin payload (type={}).",
                    PAYLOAD_MAX_BYTES, draft.type());
            payload = null;
        }
        Notification normalized = new Notification(
                draft.id(), draft.publicId(), draft.businessId(), draft.tenantSchema(),
                draft.recipientUserId(), draft.recipientScope(), draft.type(), title, body,
                draft.refType(), draft.refId(), payload, draft.readAt(), draft.createdAt());
        return repo.insert(normalized);
    }

    public InboxResult inbox(NotificationScope scope) {
        List<Notification> items = repo.findInbox(scope, INBOX_LIMIT);
        long unread = repo.countUnread(scope);
        return new InboxResult(items, unread);
    }

    public long unreadCount(NotificationScope scope) {
        return repo.countUnread(scope);
    }

    public int markRead(UUID publicId, NotificationScope scope) {
        return repo.markRead(publicId, scope);
    }

    public int markAllRead(NotificationScope scope) {
        return repo.markAllRead(scope);
    }

    /** Depura (best-effort) el badge del super-admin al resolver un ticket (M-R4-2/D17). */
    public int markSupportTicketResolvedForSuperAdmin(String ticketPublicId) {
        return repo.markReadBySupportTicketForSuperAdmin(ticketPublicId);
    }

    private boolean payloadWithinLimit(java.util.Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            return json.getBytes(StandardCharsets.UTF_8).length <= PAYLOAD_MAX_BYTES;
        } catch (RuntimeException ex) {
            log.warn("[NOTIF] No se pudo serializar el payload; se persiste sin él. Causa: {}",
                    ex.getClass().getSimpleName());
            return false;
        }
    }

    /** Trunca {@code s} a {@code maxCp} code points (no por chars/substring crudo). */
    private static String limitCodePoints(String s, int maxCp) {
        if (s == null) {
            return null;
        }
        int count = s.codePointCount(0, s.length());
        if (count <= maxCp) {
            return s;
        }
        int end = s.offsetByCodePoints(0, maxCp);
        return s.substring(0, end);
    }
}
