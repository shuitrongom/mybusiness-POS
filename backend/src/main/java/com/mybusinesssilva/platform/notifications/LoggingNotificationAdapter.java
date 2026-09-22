package com.mybusinesssilva.platform.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador de notificaciones que registra el envío en el log (sin proveedor real).
 *
 * <p>Permite que el sistema funcione de extremo a extremo sin un servicio de correo contratado.
 * Al integrar un proveedor real (SES, SendGrid, SMTP) se implementa otro {@link NotificationPort}
 * y se activa por configuración ({@code app.notifications.provider=real}). El dominio no cambia.
 *
 * <p>Activo por defecto o cuando {@code app.notifications.provider=logging}.
 */
@Component
@ConditionalOnProperty(name = "app.notifications.provider", havingValue = "logging",
        matchIfMissing = true)
public class LoggingNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationAdapter.class);

    @Override
    public void sendEmail(String to, String subject, String body,
                          String attachmentName, byte[] attachment) {
        log.info("[NOTIFICACIÓN CORREO] Para: {} | Asunto: {} | Adjunto: {} ({} bytes)",
                to, subject, attachmentName,
                attachment == null ? 0 : attachment.length);
    }

    @Override
    public void sendWhatsApp(String toPhone, String message) {
        log.info("[NOTIFICACIÓN WHATSAPP] Para: {} | Mensaje: {}", toPhone, message);
    }
}
