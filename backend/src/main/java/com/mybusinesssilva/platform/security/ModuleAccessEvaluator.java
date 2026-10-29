package com.mybusinesssilva.platform.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Evalúa el acceso a un módulo aplicando la regla de las tres capas del diseño:
 * un módulo es accesible para un usuario solo si está habilitado comercialmente
 * para su
 * negocio (capa del Super Admin) Y su rol tiene permiso (capa de permisos).
 *
 * <p>
 * Se expone como bean {@code moduleAccess} para usarse en expresiones de
 * seguridad, por
 * ejemplo: {@code @PreAuthorize("@moduleAccess.canUse('sales')")}.
 *
 * <p>
 * El Super Admin no está sujeto a la habilitación por módulo de un negocio:
 * opera a nivel
 * plataforma.
 */
@Component("moduleAccess")
public class ModuleAccessEvaluator {

    /**
     * Módulos operativos que un cajero (rol CASHIER) puede usar: la venta y el
     * manejo de su caja.
     * Todo lo demás (inventario, compras, facturación, clientes, recargas, BI,
     * usuarios/roles)
     * queda reservado para Dueño/Administrador/Supervisor.
     */
    private static final java.util.Set<String> CASHIER_MODULES = java.util.Set.of("sales", "cash", "bakery");

    /** Módulos de administración del negocio, reservados a Dueño/Administrador. */
    private static final java.util.Set<String> ADMIN_ONLY_MODULES = java.util.Set.of("roles");

    /**
     * Único sub-módulo de permiso del PANADERO: la producción de pan (registro de
     * tandas y lectura de insumos/panes para la tanda). Barrera dura del fallback
     * para tokens sin permisos granulares: un PANADERO solo alcanza
     * {@code bakery_prod}.
     */
    private static final java.util.Set<String> PANADERO_MODULES = java.util.Set.of("bakery_prod");

    /**
     * @param moduleKey clave del módulo (por ejemplo {@code sales},
     *                  {@code invoicing})
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
        // 'bakery_prod' es un SUB-MÓDULO de permiso del panadero: hereda la
        // habilitación COMERCIAL de 'bakery' (no es un módulo facturable propio, por
        // eso nunca está en el claim 'modules'). La capa de ROL/permiso sigue
        // decidiéndose sobre 'bakery_prod' (el moduleKey original).
        String commercialKey = "bakery_prod".equals(moduleKey) ? "bakery" : moduleKey;
        // Capa comercial: el negocio debe tener el módulo habilitado.
        // Excepción: 'settings' no es un módulo comercial (es configuración interna),
        // se rige solo por permisos/rol. 'settings' se evalúa sobre moduleKey original
        // (nunca es 'bakery_prod'); el mapeo comercial solo afecta hasModule.
        if (!"settings".equals(moduleKey) && !user.hasModule(commercialKey)) {
            return false;
        }
        // Capa de rol: Dueño y Administrador tienen acceso total a lo habilitado
        // (atajo).
        if (isOwnerOrAdmin(user)) {
            return true;
        }
        // RBAC configurable: si el usuario trae permisos granulares (matriz de su rol),
        // la decisión
        // se basa en ellos — puede usar el módulo si tiene cualquier permiso sobre él.
        if (!user.permissions().isEmpty()) {
            return user.hasAnyPermissionOnModule(moduleKey);
        }
        // Compatibilidad hacia atrás (tokens antiguos sin permisos): comportamiento por
        // rol. El PANADERO queda acotado SOLO a 'bakery_prod' aun sin permisos
        // granulares (barrera dura, independiente del seeding): sin esta rama caería en
        // el return permisivo y abriría sales/cash/bakery.
        if (user.hasRole(Roles.PANADERO)) {
            return PANADERO_MODULES.contains(moduleKey);
        }
        if (user.hasRole(Roles.CASHIER)) {
            return CASHIER_MODULES.contains(moduleKey);
        }
        if (user.hasRole(Roles.SUPERVISOR)) {
            return !ADMIN_ONLY_MODULES.contains(moduleKey);
        }
        return !ADMIN_ONLY_MODULES.contains(moduleKey);
    }

    /**
     * @return true si el usuario puede ejecutar una acción concreta sobre un módulo
     *         (por ejemplo
     *         {@code VOID} sobre {@code sales}). Dueño/Admin siempre pueden; el
     *         resto según su
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
     * @return true si el usuario puede administrar usuarios del negocio (alta de
     *         cajeros, etc.).
     *         Solo Dueño y Administrador, y el negocio debe tener el módulo
     *         {@code roles}.
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
     * Acceso de LECTURA al catálogo de productos: lo necesita tanto quien
     * administra el
     * inventario (Dueño/Admin) como el cajero para poder vender. Por eso permite el
     * acceso si
     * el negocio tiene habilitado {@code sales} o {@code inventory} y el rol puede
     * usar alguno.
     *
     * @return true si el usuario puede consultar el catálogo para vender o
     *         administrar
     */
    public boolean canReadCatalog() {
        return canUse("sales") || canUse("inventory");
    }

    /**
     * Acceso de LECTURA a la lista de clientes: lo necesita quien administra el CRM
     * (módulo {@code customers}) y también el cajero para asignar el cliente en una
     * venta
     * (a crédito, con datos de facturación o para acumular lealtad). Por eso
     * permite el
     * acceso si el usuario puede usar {@code customers} o {@code sales}.
     *
     * @return true si el usuario puede consultar la lista de clientes
     */
    public boolean canReadCustomers() {
        return canUse("customers") || canUse("sales");
    }

    /**
     * Acceso de LECTURA a las sucursales: cualquier usuario operativo autenticado
     * del negocio,
     * incluido el cajero, para poder elegir en qué sucursal cobra. No exige el
     * módulo
     * {@code multibranch} (leer la lista es siempre necesario, aunque solo haya una
     * sucursal).
     */
    public boolean canReadBranches() {
        AuthenticatedUser user = currentUser();
        if (user == null) {
            return false;
        }
        return user.isSuperAdmin() || !user.tenant().isBlank();
    }

    /**
     * Gestión de cajas registradoras (crear, activar): solo Dueño/Administrador, y
     * el negocio
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
     * Gestión de sucursales (crear, editar, activar): solo Dueño/Administrador y el
     * negocio debe
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
     * Configuración del negocio (ticket, datos de la empresa, etc.): solo
     * Dueño/Administrador.
     * No requiere un módulo comercial específico; basta con estar autenticado en un
     * tenant.
     */
    public boolean canManageSettings() {
        AuthenticatedUser user = currentUser();
        if (user == null) {
            return false;
        }
        return user.isSuperAdmin() || isOwnerOrAdmin(user);
    }

    /**
     * Administración del MODO PRUEBA (activar/desactivar): solo el DUEÑO (rol
     * {@code OWNER}); ni ADMIN ni cajeras. Es una decisión del negocio que corta
     * ventas de práctica a reales, por eso se reserva al dueño. El Super Admin
     * (plataforma) también puede, por consistencia con el resto de evaluadores.
     *
     * @return true si el usuario puede activar/desactivar el modo prueba
     */
    public boolean canManageTestMode() {
        AuthenticatedUser user = currentUser();
        if (user == null) {
            return false;
        }
        return user.isSuperAdmin() || user.hasRole(Roles.OWNER);
    }

    /**
     * Reapertura de turno (OWNER-only): solo el DUEÑO (rol {@code OWNER}) o el
     * Super Admin pueden reabrir un turno cerrado por error, y el negocio debe
     * tener habilitado el módulo comercial {@code cash}. Es una acción sensible que
     * "cuida pérdidas" (anula el corte previo y revierte el turno a abierto), por
     * eso se reserva al dueño; ni ADMIN ni cajeras.
     *
     * <p>
     * Mismo molde que {@link #canManageTestMode()} pero incluyendo la verificación
     * de módulo explícita (como {@link #canManageCashRegisters()}), para que la
     * barrera dura quede garantizada aunque la expresión del {@code @PreAuthorize}
     * del endpoint omitiera el {@code canUse('cash')} en el futuro.
     *
     * @return true si el usuario puede reabrir un turno de caja
     */
    public boolean canReopenShift() {
        AuthenticatedUser user = currentUser();
        if (user == null) {
            return false;
        }
        if (user.isSuperAdmin()) {
            // Plataforma: no sujeta a la habilitación por módulo de un negocio.
            return true;
        }
        return user.hasModule("cash") && user.hasRole(Roles.OWNER);
    }

    private boolean isOwnerOrAdmin(AuthenticatedUser user) {
        return user.hasRole(Roles.OWNER) || user.hasRole(Roles.ADMIN);
    }

    /**
     * @return el usuario autenticado en la petición, o {@code null} si no hay
     *         autenticación.
     */
    private AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }
}
