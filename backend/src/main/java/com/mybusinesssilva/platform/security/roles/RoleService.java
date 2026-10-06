package com.mybusinesssilva.platform.security.roles;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de roles y permisos por negocio (tenant). Permite crear roles personalizados y
 * asignarles permisos granulares (módulo + acción), además de los roles predefinidos.
 *
 * <p>Opera sobre el schema del tenant en curso (search_path fijado por el DataSource).
 */
@Service
public class RoleService {

    private final JdbcClient jdbc;

    public RoleService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Lista los roles del negocio con su información básica. */
    public List<Map<String, Object>> listRoles() {
        return jdbc.sql("SELECT id, code, name, system_role FROM role ORDER BY id")
                .query((rs, n) -> Map.<String, Object>of(
                        "id", rs.getLong("id"),
                        "code", rs.getString("code"),
                        "name", rs.getString("name"),
                        "systemRole", rs.getBoolean("system_role")))
                .list();
    }

    /** Crea un rol personalizado y devuelve su id. */
    @Transactional
    public long createRole(String code, String name) {
        return jdbc.sql("""
                INSERT INTO role (code, name, system_role) VALUES (:code, :name, false)
                RETURNING id
                """)
                .param("code", code)
                .param("name", name)
                .query(Long.class)
                .single();
    }

    /** Asigna un permiso (módulo + acción) a un rol. Idempotente. */
    @Transactional
    public void grantPermission(long roleId, String moduleKey, String action) {
        jdbc.sql("""
                INSERT INTO role_permission (role_id, module_key, action)
                VALUES (:role, :module, :action)
                ON CONFLICT (role_id, module_key, action) DO NOTHING
                """)
                .param("role", roleId)
                .param("module", moduleKey)
                .param("action", action)
                .update();
    }

    /** Revoca un permiso de un rol. */
    @Transactional
    public void revokePermission(long roleId, String moduleKey, String action) {
        jdbc.sql("""
                DELETE FROM role_permission
                WHERE role_id = :role AND module_key = :module AND action = :action
                """)
                .param("role", roleId)
                .param("module", moduleKey)
                .param("action", action)
                .update();
    }

    /** @return los permisos de un rol como pares módulo/acción. */
    public List<Map<String, Object>> permissionsOf(long roleId) {
        return jdbc.sql("SELECT module_key, action FROM role_permission WHERE role_id = :role")
                .param("role", roleId)
                .query((rs, n) -> Map.<String, Object>of(
                        "moduleKey", rs.getString("module_key"),
                        "action", rs.getString("action")))
                .list();
    }

    /** Asigna un rol a un usuario del negocio. */
    @Transactional
    public void assignRoleToUser(long userId, long roleId) {
        jdbc.sql("UPDATE app_user SET role_id = :role WHERE id = :user")
                .param("role", roleId)
                .param("user", userId)
                .update();
    }

    // =====================================================================
    // RBAC configurable: catálogo, matriz, límites y permisos efectivos.
    // =====================================================================

    /**
     * Catálogo de módulos y acciones disponibles para construir la matriz de permisos en la UI.
     * Se mantiene alineado con las claves usadas por {@code ModuleAccessEvaluator} y los seeds.
     */
    public Map<String, Object> permissionCatalog() {
        List<Map<String, String>> modules = List.of(
                Map.of("key", "sales", "name", "Ventas / Punto de venta"),
                Map.of("key", "cash", "name", "Cortes de caja"),
                Map.of("key", "inventory", "name", "Inventario y productos"),
                Map.of("key", "purchasing", "name", "Compras y proveedores"),
                Map.of("key", "customers", "name", "Clientes y CRM"),
                Map.of("key", "invoicing", "name", "Facturación electrónica"),
                Map.of("key", "payments", "name", "Recargas y servicios"),
                Map.of("key", "promotions", "name", "Promociones"),
                Map.of("key", "reports", "name", "Reportes"),
                Map.of("key", "bi", "name", "Business Intelligence"),
                Map.of("key", "multibranch", "name", "Sucursales"),
                Map.of("key", "roles", "name", "Usuarios y roles"),
                Map.of("key", "printing", "name", "Impresión de tickets"),
                Map.of("key", "settings", "name", "Configuración"));
        List<Map<String, String>> actions = List.of(
                Map.of("key", "VIEW", "name", "Ver"),
                Map.of("key", "CREATE", "name", "Crear"),
                Map.of("key", "EDIT", "name", "Editar"),
                Map.of("key", "DELETE", "name", "Eliminar"),
                Map.of("key", "VOID", "name", "Cancelar"),
                Map.of("key", "DISCOUNT", "name", "Aplicar descuento"),
                Map.of("key", "RETURN", "name", "Devolver"),
                Map.of("key", "REPRINT", "name", "Reimprimir"),
                Map.of("key", "AUTHORIZE", "name", "Autorizar"),
                Map.of("key", "EXPORT", "name", "Exportar"),
                Map.of("key", "MANAGE", "name", "Administrar"));
        return Map.of("modules", modules, "actions", actions);
    }

    /** Lista roles con sus límites (descuento máx, si autoriza). */
    public List<Map<String, Object>> listRolesWithLimits() {
        return jdbc.sql("""
                SELECT id, code, name, system_role, max_discount_pct, can_authorize
                FROM role ORDER BY id
                """)
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("code", rs.getString("code"));
                    m.put("name", rs.getString("name"));
                    m.put("systemRole", rs.getBoolean("system_role"));
                    m.put("maxDiscountPct", rs.getBigDecimal("max_discount_pct"));
                    m.put("canAuthorize", rs.getBoolean("can_authorize"));
                    return m;
                })
                .list();
    }

    /** Actualiza los límites de un rol (descuento máximo y capacidad de autorizar). */
    @Transactional
    public void updateRoleLimits(long roleId, java.math.BigDecimal maxDiscountPct, boolean canAuthorize) {
        jdbc.sql("""
                UPDATE role SET max_discount_pct = :pct, can_authorize = :auth WHERE id = :id
                """)
                .param("pct", maxDiscountPct == null ? java.math.BigDecimal.ZERO : maxDiscountPct)
                .param("auth", canAuthorize)
                .param("id", roleId)
                .update();
    }

    /**
     * Reemplaza por completo la matriz de permisos de un rol con la lista dada (module_key:action).
     * Operación transaccional: borra los actuales e inserta los nuevos.
     */
    @Transactional
    public void replacePermissions(long roleId, List<Map<String, String>> permissions) {
        jdbc.sql("DELETE FROM role_permission WHERE role_id = :id").param("id", roleId).update();
        if (permissions == null) {
            return;
        }
        for (Map<String, String> p : permissions) {
            jdbc.sql("""
                    INSERT INTO role_permission (role_id, module_key, action)
                    VALUES (:role, :module, :action)
                    ON CONFLICT (role_id, module_key, action) DO NOTHING
                    """)
                    .param("role", roleId)
                    .param("module", p.get("moduleKey"))
                    .param("action", p.get("action"))
                    .update();
        }
    }

    /**
     * Permisos efectivos de un usuario (por su rol), como cadenas {@code module:action}.
     * Es lo que se embebe en el JWT y consulta el motor de autorización.
     */
    public List<String> effectivePermissions(String email) {
        return jdbc.sql("""
                SELECT rp.module_key || ':' || rp.action AS perm
                FROM app_user u
                JOIN role_permission rp ON rp.role_id = u.role_id
                WHERE u.email = :email
                """)
                .param("email", email)
                .query(String.class)
                .list();
    }
}
