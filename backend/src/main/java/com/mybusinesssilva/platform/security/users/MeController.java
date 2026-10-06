package com.mybusinesssilva.platform.security.users;

import com.mybusinesssilva.platform.security.AuthenticatedUser;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Perfil del usuario autenticado en curso. El token JWT solo lleva el correo y los roles; este
 * endpoint devuelve además el nombre completo, leyéndolo de {@code app_user} del tenant. Lo usa
 * el menú de perfil del frontend para mostrar el nombre real (no el correo).
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final JdbcClient jdbc;

    public MeController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Devuelve el perfil del usuario actual: correo, nombre completo y rol principal. */
    @GetMapping
    public MeView me(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            return new MeView("", "", "");
        }
        String email = user.subject();
        String primaryRole = user.roles().stream().findFirst().orElse("");

        // El Super Admin no vive en un schema de tenant: devolvemos lo que hay en el token.
        if (user.isSuperAdmin() || user.tenant() == null || user.tenant().isBlank()) {
            return new MeView(email, "Super Admin", primaryRole);
        }

        // Fija el tenant para que la RLS de app_user permita leer la fila del usuario.
        String previousTenant = TenantContext.getTenantId();
        TenantContext.setTenantId(user.tenant());
        try {
            Row row = jdbc.sql("""
                    SELECT u.full_name, r.name AS role_name
                    FROM app_user u
                    LEFT JOIN role r ON r.id = u.role_id
                    WHERE u.email = :email
                    """)
                    .param("email", email)
                    .query((rs, n) -> new Row(rs.getString("full_name"), rs.getString("role_name")))
                    .optional()
                    .orElse(new Row(null, null));
            String name = row.fullName() == null || row.fullName().isBlank() ? email : row.fullName();
            String roleName = row.roleName() == null || row.roleName().isBlank() ? primaryRole : row.roleName();
            return new MeView(email, name, roleName);
        } finally {
            if (previousTenant != null) {
                TenantContext.setTenantId(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    /**
     * Permisos efectivos del usuario actual (module:action) y límites de su rol (descuento máximo,
     * si puede autorizar). Lo usa el frontend para armar el menú y habilitar/ocultar acciones.
     */
    @GetMapping("/permissions")
    public java.util.Map<String, Object> permissions(@AuthenticationPrincipal AuthenticatedUser user) {
        if (user == null) {
            return java.util.Map.of("permissions", java.util.List.of(), "maxDiscountPct", 0, "canAuthorize", false);
        }
        java.util.List<String> perms = new java.util.ArrayList<>(user.permissions());
        // Dueño/Admin: acceso total implícito.
        boolean ownerAdmin = user.roles().contains("OWNER") || user.roles().contains("ADMIN");

        if (user.isSuperAdmin() || user.tenant() == null || user.tenant().isBlank()) {
            return java.util.Map.of("permissions", perms, "maxDiscountPct", 100, "canAuthorize", true, "ownerAdmin", ownerAdmin);
        }

        String previousTenant = TenantContext.getTenantId();
        TenantContext.setTenantId(user.tenant());
        try {
            Limits limits = jdbc.sql("""
                    SELECT r.max_discount_pct, r.can_authorize
                    FROM app_user u JOIN role r ON r.id = u.role_id
                    WHERE u.email = :email
                    """)
                    .param("email", user.subject())
                    .query((rs, n) -> new Limits(rs.getBigDecimal("max_discount_pct"), rs.getBoolean("can_authorize")))
                    .optional()
                    .orElse(new Limits(java.math.BigDecimal.ZERO, false));
            return java.util.Map.of(
                    "permissions", perms,
                    "maxDiscountPct", ownerAdmin ? java.math.BigDecimal.valueOf(100) : limits.maxDiscountPct(),
                    "canAuthorize", ownerAdmin || limits.canAuthorize(),
                    "ownerAdmin", ownerAdmin);
        } catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            return java.util.Map.of("permissions", perms, "maxDiscountPct", ownerAdmin ? 100 : 0, "canAuthorize", ownerAdmin, "ownerAdmin", ownerAdmin);
        } finally {
            if (previousTenant != null) {
                TenantContext.setTenantId(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    private record Limits(java.math.BigDecimal maxDiscountPct, boolean canAuthorize) {
    }

    private record Row(String fullName, String roleName) {
    }

    /** Perfil del usuario actual. */
    public record MeView(String email, String fullName, String role) {
    }
}
