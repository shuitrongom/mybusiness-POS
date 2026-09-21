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
}
