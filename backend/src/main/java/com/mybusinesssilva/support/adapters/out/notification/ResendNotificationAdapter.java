package com.mybusinesssilva.support.adapters.out.notification;

import com.mybusinesssilva.platform.notifications.NotificationPort;
import com.mybusinesssilva.support.config.NotificationsProperties;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adaptador de notificaciones por correo apoyado en <b>Resend</b>
 * ({@code POST https://api.resend.com/emails}) vía {@link RestClient}, sin SDK.
 *
 * <p>Activo <b>solo</b> cuando {@code app.notifications.provider=resend} (mutuamente excluyente con
 * {@code LoggingNotificationAdapter}, que queda con {@code havingValue=logging, matchIfMissing=true}).
 * Implementa la firma EXACTA de {@link NotificationPort} (sin {@code throws}): es best-effort y
 * <b>nunca</b> propaga una excepción aguas arriba, para no tumbar la operación que lo invoca.
 *
 * <p>Degrada con elegancia: si falta {@code api-key}, {@code from} o el destino, loggea {@code WARN}
 * y retorna sin enviar (como el adaptador de logging). Nunca loggea el secreto ({@code api_key}) ni
 * el cuerpo completo; solo el asunto y la clase de la excepción.
 */
@Component
@ConditionalOnProperty(name = "app.notifications.provider", havingValue = "resend")
public class ResendNotificationAdapter implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(ResendNotificationAdapter.class);

    private static final String RESEND_EMAILS_URL = "https://api.resend.com/emails";

    private final RestClient http;
    private final NotificationsProperties props;

    public ResendNotificationAdapter(RestClient supportRestClient, NotificationsProperties props) {
        this.http = supportRestClient;
        this.props = props;
    }

    @Override
    public void sendEmail(String to, String subject, String body,
                          String attachmentName, byte[] attachment) {
        send(to, subject, body, attachmentName, attachment, null, null);
    }

    /**
     * Variante de 7 argumentos (Fase 2): mismo payload que el de 5, añadiendo {@code reply_to} y
     * {@code headers} cuando se indican (para correlacionar la respuesta inbound con el ticket). El
     * envío de 5 args queda intacto (billing/licensing sin cambios).
     */
    @Override
    public void sendEmail(String to, String subject, String body,
                          String attachmentName, byte[] attachment,
                          String replyTo, Map<String, String> headers) {
        send(to, subject, body, attachmentName, attachment, replyTo, headers);
    }

    /** Cuerpo común de ambas variantes. {@code replyTo}/{@code headers} son opcionales (nulos ⇒ se omiten). */
    private void send(String to, String subject, String body,
                      String attachmentName, byte[] attachment,
                      String replyTo, Map<String, String> headers) {
        String apiKey = props.resend() == null ? null : props.resend().apiKey();
        String from = props.resend() == null ? null : props.resend().from();
        if (isBlank(apiKey) || isBlank(from) || isBlank(to)) {
            // Degradación elegante: sin RESEND_API_KEY, sin 'from' verificado o sin destino, se
            // comporta como el LoggingNotificationAdapter y NO rompe. 'from'/destino son PII del
            // dueño: nunca hardcodeados en el repo, solo env var en Railway.
            log.warn("[RESEND] Config incompleta (api-key/from/destino en blanco); correo NO enviado "
                    + "(degradado a log). Asunto: {}", subject);
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("from", from);
            payload.put("to", List.of(to.split("\\s*,\\s*"))); // soporta varios destinos coma-separados
            payload.put("subject", subject);
            // El contrato NotificationPort.sendEmail(body) no indica si 'body' es HTML o texto, y este
            // adaptador es TRANSVERSAL (lo usan billing/licensing además del buzón). Detección
            // best-effort: si el body contiene markup (<...>) se envía como 'html', si no, como 'text'.
            boolean looksHtml = body != null && body.contains("<") && body.contains(">");
            payload.put(looksHtml ? "html" : "text", body == null ? "" : body);
            if (attachment != null && attachmentName != null) {
                payload.put("attachments", List.of(Map.of(
                        "filename", attachmentName,
                        "content", Base64.getEncoder().encodeToString(attachment))));
            }
            // Fase 2: Reply-To con token de correlación y/o headers extra (solo si se indicaron).
            if (!isBlank(replyTo)) {
                payload.put("reply_to", replyTo);
            }
            if (headers != null && !headers.isEmpty()) {
                payload.put("headers", headers);
            }
            http.post()
                    .uri(RESEND_EMAILS_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            // NUNCA propaga: el fallo de correo no debe tumbar nada aguas arriba. No se loggea el
            // secreto ni el cuerpo; solo el asunto y la clase de excepción.
            log.warn("[RESEND] Fallo al enviar correo (se ignora, best-effort). Asunto: {} | causa: {}",
                    subject, ex.getClass().getSimpleName());
        }
    }

    @Override
    public void sendWhatsApp(String toPhone, String message) {
        // Resend no envía WhatsApp; se registra como el logging para no romper consumidores existentes.
        log.info("[RESEND] WhatsApp no soportado por este adaptador; mensaje registrado para {}.", toPhone);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
