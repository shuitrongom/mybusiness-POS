package com.mybusinesssilva.platform.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Evalúa el acceso a un módulo aplicando la regla de las tres capas del diseño:
 * un módulo es accesible para un usuario solo si está habilitado comercialmente para su
 * negocio (capa del Super Admin) Y su rol tiene permiso (capa de permisos).
 *
 * <p>Se expone como bean {@code moduleAccess} para usarse en expresiones de seguridad, por
 * ejemplo: {@code @PreAuthorize("@moduleAccess.canUse('sales')")}.
 *
 * <p>El Super Admin no está sujeto a la habilitación por módulo de un negocio: opera a nivel
 * plataforma.
 */
@Component("moduleAccess")
public class ModuleAccessEvaluator {

    /**
     * @param moduleKey clave del módulo (por ejemplo {@code sales}, {@code invoicing})
     * @return true si el usuario autenticado puede usar el módulo
     */
    public boolean canUse(String moduleKey) {
        AuthenticatedUser user = currentUser();
        if (user == null) {
            return false;
        }
        if (user.isSuperAdmin()) {
            return true;
        }
        // Capa comercial: el negocio debe tener el módulo habilitado.
        return user.hasModule(moduleKey);
    }

    /**
     * @return el usuario autenticado en la petición, o {@code null} si no hay autenticación.
     */
    private AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }
}
