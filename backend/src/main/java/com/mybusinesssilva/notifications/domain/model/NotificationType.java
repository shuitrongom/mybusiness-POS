package com.mybusinesssilva.notifications.domain.model;

/**
 * Tipos de notificación como constantes {@code String} (no enum cerrado) para no bloquear tipos
 * futuros a nivel de compilación.
 */
public final class NotificationType {

    public static final String SUPPORT_REPLY = "SUPPORT_REPLY";
    public static final String SUPPORT_REPLY_FROM_REPORTER = "SUPPORT_REPLY_FROM_REPORTER";
    public static final String SUPPORT_NEW_TICKET = "SUPPORT_NEW_TICKET";

    private NotificationType() {
    }
}
