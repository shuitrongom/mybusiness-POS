package com.mybusinesssilva.platform.security.roles;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión de roles y permisos del negocio. Requiere el módulo {@code roles} habilitado.
 * Permite crear roles personalizados, asignarles permisos y asignar roles a usuarios.
 */
@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("@moduleAccess.canUse('roles')")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return roleService.listRolesWithLimits();
    }

    /** Catálogo de módulos y acciones para construir la matriz de permisos. */
    @GetMapping("/catalog")
    public Map<String, Object> catalog() {
        return roleService.permissionCatalog();
    }

    /** Reemplaza toda la matriz de permisos de un rol de una vez. */
    @PostMapping("/{id}/permissions/replace")
    public ResponseEntity<Void> replace(@PathVariable long id, @RequestBody ReplaceRequest request) {
        roleService.replacePermissions(id, request.permissions());
        return ResponseEntity.noContent().build();
    }

    /** Actualiza los límites del rol (descuento máximo, capacidad de autorizar). */
    @PostMapping("/{id}/limits")
    public ResponseEntity<Void> limits(@PathVariable long id, @RequestBody LimitsRequest request) {
        roleService.updateRoleLimits(id, request.maxDiscountPct(), request.canAuthorize());
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<Map<String, Long>> create(@Valid @RequestBody CreateRoleRequest request) {
        long id = roleService.createRole(request.code(), request.name());
        return ResponseEntity.ok(Map.of("roleId", id));
    }

    @GetMapping("/{id}/permissions")
    public List<Map<String, Object>> permissions(@PathVariable long id) {
        return roleService.permissionsOf(id);
    }

    @PostMapping("/{id}/permissions")
    public ResponseEntity<Void> grant(
            @PathVariable long id, @Valid @RequestBody PermissionRequest request) {
        roleService.grantPermission(id, request.moduleKey(), request.action());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/permissions")
    public ResponseEntity<Void> revoke(
            @PathVariable long id, @Valid @RequestBody PermissionRequest request) {
        roleService.revokePermission(id, request.moduleKey(), request.action());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/assign/{userId}")
    public ResponseEntity<Void> assign(@PathVariable long id, @PathVariable long userId) {
        roleService.assignRoleToUser(userId, id);
        return ResponseEntity.noContent().build();
    }

    /** Alta de rol personalizado. */
    public record CreateRoleRequest(@NotBlank String code, @NotBlank String name) {
    }

    /** Permiso (módulo + acción). */
    public record PermissionRequest(@NotBlank String moduleKey, @NotBlank String action) {
    }

    /** Reemplazo completo de la matriz de permisos de un rol. */
    public record ReplaceRequest(@NotNull List<Map<String, String>> permissions) {
    }

    /** Límites del rol. */
    public record LimitsRequest(java.math.BigDecimal maxDiscountPct, boolean canAuthorize) {
    }
}
