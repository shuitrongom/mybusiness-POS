package com.mybusinesssilva.platform.security;

import java.util.List;
import java.util.Set;

/**
 * Representa al usuario autenticado en la petición en curso.
 *
 * @param subject identificador del usuario (correo o id)
 * @param tenant  schema del tenant, o vacío para el Super Admin (contexto global)
 * @param roles   roles del usuario
 * @param modules módulos habilitados comercialmente para el negocio del usuario
 */
public record AuthenticatedUser(
        String subject,
        String tenant,
        Set<String> roles,
        Set<String> modules) {

    public AuthenticatedUser(String subject, String tenant,
                             List<String> roles, List<String> modules) {
        this(subject, tenant, Set.copyOf(roles), Set.copyOf(modules));
    }

    /** @return true si el usuario tiene el rol dado. */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /** @return true si el negocio del usuario tiene habilitado el módulo dado. */
    public boolean hasModule(String moduleKey) {
        return modules.contains(moduleKey);
    }

    /** @return true si es un usuario global (Super Admin), sin tenant asociado. */
    public boolean isSuperAdmin() {
        return (tenant == null || tenant.isBlank()) && roles.contains(Roles.SUPER_ADMIN);
    }
}
