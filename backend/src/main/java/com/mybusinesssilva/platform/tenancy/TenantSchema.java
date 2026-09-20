package com.mybusinesssilva.platform.tenancy;

import java.util.regex.Pattern;

/**
 * Utilidades para los nombres de schema de PostgreSQL usados en el modelo multi-tenant.
 *
 * <p>Convenciones:
 * <ul>
 *   <li>{@code admin}: schema global (negocios, planes, licencias, catálogos globales).</li>
 *   <li>{@code tenant_<id>}: schema de datos de un negocio concreto.</li>
 * </ul>
 */
public final class TenantSchema {

    /** Schema global de administración del SaaS. */
    public static final String ADMIN_SCHEMA = "admin";

    /** Prefijo de los schemas de cada negocio. */
    public static final String TENANT_PREFIX = "tenant_";

    /**
     * Patrón de validación de un nombre de schema. Restringido a minúsculas, dígitos y guion
     * bajo, empezando por letra, con longitud máxima de 63 (límite de identificadores en
     * PostgreSQL). Evita inyección al construir sentencias {@code SET search_path}.
     */
    private static final Pattern VALID_SCHEMA = Pattern.compile("^[a-z][a-z0-9_]{0,62}$");

    private TenantSchema() {
        // Clase de utilidad: no instanciable.
    }

    /**
     * Construye el nombre de schema de un negocio a partir de su identificador numérico.
     *
     * @param businessId identificador del negocio en el schema {@code admin}
     * @return nombre de schema, por ejemplo {@code tenant_12}
     */
    public static String forBusinessId(long businessId) {
        return TENANT_PREFIX + businessId;
    }

    /**
     * Valida que un nombre de schema sea seguro para usarse en sentencias SQL dinámicas.
     *
     * @param schema nombre de schema a validar
     * @return el mismo nombre si es válido
     * @throws IllegalArgumentException si el nombre no cumple el patrón permitido
     */
    public static String validate(String schema) {
        if (schema == null || !VALID_SCHEMA.matcher(schema).matches()) {
            throw new IllegalArgumentException("Nombre de schema inválido: " + schema);
        }
        return schema;
    }
}
