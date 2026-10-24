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
     * Configuración del proveedor Resend.
     *
     * @param apiKey API key (secreto; {@code Authorization: Bearer <key>})
     * @param from   remitente verificado en Resend (PII)
     */
    public record Resend(String apiKey, String from) {
    }

    /**
     * Destino del buzón de soporte.
     *
     * @param inbox correo(s) destino, coma-separado si son varios (PII)
     */
    public record Support(String inbox) {
    }
}
