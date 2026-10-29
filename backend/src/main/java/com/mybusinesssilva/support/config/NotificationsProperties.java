package com.mybusinesssilva.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de notificaciones del buzón, bajo el prefijo {@code app.notifications}. Usa
 * sub-records anidados (igual que {@code SecurityProperties(Jwt jwt)}) porque un
 * {@code @ConfigurationProperties} tiene un único prefijo.
 *
 * @param resend  configuración del proveedor Resend
 * @param support destino del buzón de soporte
 */
@ConfigurationProperties(prefix = "app.notifications")
public record NotificationsProperties(Resend resend, Support support) {

    /**
     * Configuración del proveedor Resend. Fase 1 usa {@code apiKey}/{@code from} (envío saliente).
     * Fase 2 (email INBOUND → hilo) añade los campos de seguridad/correlación del webhook inbound.
     *
     * @param apiKey                      API key (SECRETO; {@code Authorization: Bearer <key>})
     * @param from                        remitente verificado en Resend (PII)
     * @param inboundWebhookSecret        SECRETO de firma Svix del webhook inbound (formato
     *                                    {@code whsec_…}); vacío ⇒ el webhook rechaza TODO (fail-closed)
     * @param inboundAllowlist            remitentes del equipo autorizados, coma-separado (PII); vacío ⇒
     *                                    rechaza TODO (fail-closed)
     * @param replyDomain                 dominio para el {@code Reply-To} {@code reply+<publicId>@…};
     *                                    vacío ⇒ se omite el {@code Reply-To} (el token de asunto sigue)
     * @param inboundSignatureHeaderPrefix prefijo de los headers de firma ({@code svix} o alias
     *                                    {@code webhook}); default {@code svix}
     * @param inboundMaxBytes             tope duro del cuerpo entrante en bytes (barrera 0)
     * @param inboundMaxTextChars         tope del {@code text} antes de las regex de limpieza
     */
    public record Resend(String apiKey, String from, String inboundWebhookSecret,
                         String inboundAllowlist, String replyDomain,
                         String inboundSignatureHeaderPrefix, int inboundMaxBytes,
                         int inboundMaxTextChars) {
    }

    /**
     * Destino del buzón de soporte.
     *
     * @param inbox correo(s) destino, coma-separado si son varios (PII)
     */
    public record Support(String inbox) {
    }
}
