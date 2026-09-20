package com.mybusinesssilva.platform.tenancy;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resuelve el tenant de una petición HTTP.
 *
 * <p>Orden de resolución:
 * <ol>
 *   <li>Encabezado {@code X-Tenant-Id} (útil para clientes y pruebas).</li>
 *   <li>Subdominio del host (por ejemplo {@code negocio.mybusinesssilva.com} → {@code negocio}).</li>
 * </ol>
 *
 * <p>En una fase posterior, cuando la autenticación esté activa, el tenant se tomará
 * preferentemente del claim del JWT. Este resolvedor deja preparada esa extensión.
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

        String host = request.getServerName();
        return subdomainOf(host).map(this::normalizeToSchema);
    }

    /**
     * Extrae el subdominio de un host, si existe y no es un host base o local.
     */
    private Optional<String> subdomainOf(String host) {
        if (!StringUtils.hasText(host) || "localhost".equalsIgnoreCase(host)) {
            return Optional.empty();
        }
        String[] parts = host.split("\\.");
        // Requiere al menos sub.dominio.tld para considerar subdominio de negocio.
        if (parts.length < 3) {
            return Optional.empty();
        }
        String sub = parts[0];
        if ("www".equalsIgnoreCase(sub) || "app".equalsIgnoreCase(sub)) {
            return Optional.empty();
        }
        return Optional.of(sub);
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
