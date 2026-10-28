package com.mybusinesssilva.notifications.domain.port.out;

import com.mybusinesssilva.notifications.domain.model.Notification;
import com.mybusinesssilva.notifications.domain.port.in.NotificationScope;
import java.util.List;
import java.util.UUID;

/** Puerto de salida del centro de notificaciones. */
public interface NotificationRepository {

    Notification insert(Notification n);

    List<Notification> findInbox(NotificationScope scope, int limit);

    long countUnread(NotificationScope scope);

    /** @return 1 si existe en el scope (204), 0 si es ajena/no existe (404). */
    int markRead(UUID publicId, NotificationScope scope);

    int markAllRead(NotificationScope scope);

    /**
     * Marca leídas (best-effort) las notificaciones SUPER_ADMIN de
     * {@code ref_type='support_ticket'} AND {@code ref_id=ticketPublicId}.
     */
    int markReadBySupportTicketForSuperAdmin(String ticketPublicId);
}
