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
     * Módulos operativos que un cajero (rol CASHIER) puede usar: la venta y el manejo de su caja.
     * Todo lo demás (inventario, compras, facturación, clientes, recargas, BI, usuarios/roles)
     * queda reservado para Dueño/Administrador/Supervisor.
     */
    private static final java.util.Set<String> CASHIER_MODULES =
            java.util.Set.of("sales", "cash");

    /** Módulos de administración del negocio, reservados a Dueño/Administrador. */
    private static final java.util.Set<String> ADMIN_ONLY_MODULES =
            java.util.Set.of("roles");

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
        if (!user.hasModule(moduleKey)) {
            return false;
        }
        // Capa de rol: acota qué módulos habilitados puede usar cada rol del negocio.
        if (isOwnerOrAdmin(user)) {
            return true; // Dueño y Administrador: acceso total a lo habilitado.
        }
        if (user.hasRole(Roles.CASHIER)) {
            return CASHIER_MODULES.contains(moduleKey);
        }
        if (user.hasRole(Roles.SUPERVISOR)) {
            // Supervisor: operación completa, salvo administración del negocio (usuarios/roles).
            return !ADMIN_ONLY_MODULES.contains(moduleKey);
        }
        // Roles personalizados: por ahora permiten los módulos operativos habilitados,
        // salvo la administración de usuarios/roles.
        return !ADMIN_ONLY_MODULES.contains(moduleKey);
    }

    /**
     * @return true si el usuario puede administrar usuarios del negocio (alta de cajeros, etc.).
     *         Solo Dueño y Administrador, y el negocio debe tener el módulo {@code roles}.
     */
    public boolean canManageUsers() {
        AuthenticatedUser user = currentUser();
        if (user == null) {
            return false;
        }
        if (user.isSuperAdmin()) {
            return true;
        }
        return user.hasModule("roles") && isOwnerOrAdmin(user);
    }

    /**
     * Acceso de LECTURA al catálogo de productos: lo necesita tanto quien administra el
     * inventario (Dueño/Admin) como el cajero para poder vender. Por eso permite el acceso si
     * el negocio tiene habilitado {@code sales} o {@code inventory} y el rol puede usar alguno.
     *
     * @return true si el usuario puede consultar el catálogo para vender o administrar
     */
    public boolean canReadCatalog() {
        return canUse("sales") || canUse("inventory");
    }

    /**
     * Acceso de LECTURA a las sucursales: cualquier usuario operativo autenticado del negocio,
     * incluido el cajero, para poder elegir en qué sucursal cobra. No exige el módulo
     * {@code multibranch} (leer la lista es siempre necesario, aunque solo haya una sucursal).
     */
    public boolean canReadBranches() {
        AuthenticatedUser user = currentUser();
        if (user == null) {
            return false;
        }
        return user.isSuperAdmin() || !user.tenant().isBlank();
    }

    /**
     * Gestión de cajas registradoras (crear, activar): solo Dueño/Administrador, y el negocio
     * debe tener habilitado el módulo {@code cash}.
     */
    public boolean canManageCashRegisters() {
        AuthenticatedUser user = currentUser();
        if (user == null) {
            return false;
        }
        if (user.isSuperAdmin()) {
            return true;
        }
        return user.hasModule("cash") && isOwnerOrAdmin(user);
    }

    /**
     * Gestión de sucursales (crear, editar, activar): solo Dueño/Administrador y el negocio debe
     * tener habilitado el módulo comercial {@code multibranch}.
     */
    public boolean canManageBranches() {
        AuthenticatedUser user = currentUser();
        if (user == null) {
            return false;
        }
        if (user.isSuperAdmin()) {
            return true;
        }
        return user.hasModule("multibranch") && isOwnerOrAdmin(user);
    }

    private boolean isOwnerOrAdmin(AuthenticatedUser user) {
        return user.hasRole(Roles.OWNER) || user.hasRole(Roles.ADMIN);
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
