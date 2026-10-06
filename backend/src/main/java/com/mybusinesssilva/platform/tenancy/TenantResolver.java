package com.mybusinesssilva.platform.tenancy;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resuelve el tenant de una petición HTTP.
 *
 * <p>Fuente del tenant:
 * <ol>
 *   <li>El claim del JWT (fuente de verdad), que fija el {@link JwtAuthenticationFilter}
 *       directamente en el {@link TenantContext} antes de que corra este resolvedor.</li>
 *   <li>El encabezado {@code X-Tenant-Id} (útil para clientes y pruebas) como respaldo
 *       cuando no hay JWT.</li>
 * </ol>
 *
 * <p>NO se deduce el tenant del subdominio del host: la arquitectura es de login único
 * (todos los negocios entran por el mismo dominio, p. ej. {@code app.puntonubepos.com}, y
 * eligen su negocio al iniciar sesión). Deducir por subdominio rompía detrás de proxies/PaaS
 * cuyo host (p. ej. {@code mybusiness-pos-production.up.railway.app}) no es un tenant válido.
 *
 * <p>Devuelve el nombre de schema del tenant ya validado, o vacío si la petición es global
 * (por ejemplo, endpoints del Super Admin que operan sobre el schema {@code admin}).
 */
@Component
public class TenantResolver {

    public static final String TENANT_HEADER = "X-Tenant-Id";

    /**
     * @param request petición entrante
     * @return el nombre de schema del tenant, o vacío si no aplica
     */
    public Optional<String> resolve(HttpServletRequest request) {
        String headerValue = request.getHeader(TENANT_HEADER);
        if (StringUtils.hasText(headerValue)) {
            return Optional.of(normalizeToSchema(headerValue.trim()));
        }
        // Sin header: el tenant lo aporta el JWT (ya puesto en TenantContext por el filtro JWT).
        // No se infiere del host.
        return Optional.empty();
    }

    /**
     * Normaliza un valor a un nombre de schema válido. Acepta tanto un identificador ya con
     * prefijo ({@code tenant_12}) como un valor crudo que se prefija.
     */
    private String normalizeToSchema(String value) {
        String schema = value.startsWith(TenantSchema.TENANT_PREFIX)
                ? value
                : TenantSchema.TENANT_PREFIX + value;
        return TenantSchema.validate(schema.toLowerCase());
    }
}
