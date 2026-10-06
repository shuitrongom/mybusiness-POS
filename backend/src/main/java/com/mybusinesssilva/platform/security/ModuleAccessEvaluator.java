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
        // Excepción: 'settings' no es un módulo comercial (es configuración interna), se rige solo
        // por permisos/rol.
        if (!"settings".equals(moduleKey) && !user.hasModule(moduleKey)) {
            return false;
        }
        // Capa de rol: Dueño y Administrador tienen acceso total a lo habilitado (atajo).
        if (isOwnerOrAdmin(user)) {
            return true;
        }
        // RBAC configurable: si el usuario trae permisos granulares (matriz de su rol), la decisión
        // se basa en ellos — puede usar el módulo si tiene cualquier permiso sobre él.
        if (!user.permissions().isEmpty()) {
            return user.hasAnyPermissionOnModule(moduleKey);
        }
        // Compatibilidad hacia atrás (tokens antiguos sin permisos): comportamiento por rol.
        if (user.hasRole(Roles.CASHIER)) {
            return CASHIER_MODULES.contains(moduleKey);
        }
        if (user.hasRole(Roles.SUPERVISOR)) {
            return !ADMIN_ONLY_MODULES.contains(moduleKey);
        }
        return !ADMIN_ONLY_MODULES.contains(moduleKey);
    }

    /**
     * @return true si el usuario puede ejecutar una acción concreta sobre un módulo (por ejemplo
     *         {@code VOID} sobre {@code sales}). Dueño/Admin siempre pueden; el resto según su
     *         matriz de permisos.
     */
    public boolean can(String moduleKey, String action) {
        AuthenticatedUser user = currentUser();
        if (user == null) {
            return false;
        }
        if (user.isSuperAdmin() || isOwnerOrAdmin(user)) {
            return true;
        }
        return user.hasPermission(moduleKey, action);
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
     * Acceso de LECTURA a la lista de clientes: lo necesita quien administra el CRM
     * (módulo {@code customers}) y también el cajero para asignar el cliente en una venta
     * (a crédito, con datos de facturación o para acumular lealtad). Por eso permite el
     * acceso si el usuario puede usar {@code customers} o {@code sales}.
     *
     * @return true si el usuario puede consultar la lista de clientes
     */
    public boolean canReadCustomers() {
        return canUse("customers") || canUse("sales");
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

    /**
     * Configuración del negocio (ticket, datos de la empresa, etc.): solo Dueño/Administrador.
     * No requiere un módulo comercial específico; basta con estar autenticado en un tenant.
     */
    public boolean canManageSettings() {
        AuthenticatedUser user = currentUser();
        if (user == null) {
            return false;
        }
        return user.isSuperAdmin() || isOwnerOrAdmin(user);
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
