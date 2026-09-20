package com.mybusinesssilva.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de configuración de seguridad, inyectadas desde {@code app.security.*}.
 *
 * @param jwt configuración de los tokens JWT
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(Jwt jwt) {

    /**
     * Configuración de JWT.
     *
     * @param secret            clave secreta para firmar tokens (HMAC). En producción debe
     *                          venir de un gestor de secretos y tener al menos 32 bytes.
     * @param accessTtlMinutes  vigencia del token de acceso en minutos.
     * @param refreshTtlDays    vigencia del refresh token en días.
     * @param issuer            emisor de los tokens.
     */
    public record Jwt(
            String secret,
            long accessTtlMinutes,
            long refreshTtlDays,
            String issuer) {
    }
}
