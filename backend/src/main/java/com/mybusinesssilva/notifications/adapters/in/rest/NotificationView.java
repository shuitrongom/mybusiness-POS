package com.mybusinesssilva.notifications.adapters.in.rest;

import com.mybusinesssilva.notifications.domain.model.Notification;
import java.util.UUID;

/** DTO de respuesta REST de una notificación. No expone scope ni payload interno. */
public record NotificationView(
        UUID publicId,
        String type,
        String title,
        String body,
        String refType,
        String refId,
        boolean read,
        String createdAt) {

    public static NotificationView from(Notification n) {
        return new NotificationView(
                n.publicId(), n.type(), n.title(), n.body(),
                n.refType(), n.refId(), n.readAt() != null,
                n.createdAt() == null ? null : n.createdAt().toString());
    }
}
