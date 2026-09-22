package com.mybusinesssilva.platform.security.users;

import com.mybusinesssilva.platform.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión de usuarios del negocio (cajeros, supervisores, administradores) por el
 * Dueño/Administrador. Requiere el módulo {@code roles} habilitado y el permiso de
 * administración (evaluado por {@code ModuleAccessEvaluator}).
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("@moduleAccess.canManageUsers()")
public class TenantUserController {

    private final TenantUserService userService;

    public TenantUserController(TenantUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return userService.listUsers();
    }

    @PostMapping
    public ResponseEntity<TenantUserService.CreatedUser> create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateUserRequest request) {
        TenantUserService.CreatedUser created = userService.createUser(
                actorEmail(actor), request.email().trim(), request.fullName().trim(),
                normalizeWhatsapp(request.whatsapp()), request.roleCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/enable")
    public ResponseEntity<Void> enable(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        userService.setActive(actorEmail(actor), id, true);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/disable")
    public ResponseEntity<Void> disable(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        userService.setActive(actorEmail(actor), id, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        String password = userService.resetPassword(actorEmail(actor), id);
        return ResponseEntity.ok(Map.of("password", password));
    }

    private String actorEmail(AuthenticatedUser actor) {
        return actor == null ? "unknown" : actor.subject();
    }

    private static String normalizeWhatsapp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.startsWith("52") && digits.length() > 10) {
            digits = digits.substring(digits.length() - 10);
        }
        return digits.length() == 10 ? "+52" + digits : raw.trim();
    }

    /** Alta de usuario del negocio. */
    public record CreateUserRequest(
            @NotBlank @Email(message = "El correo no es válido") String email,
            @NotBlank String fullName,
            @Pattern(regexp = "^$|^(\\+?52)?\\s*(\\d\\s*){10}$",
                    message = "El WhatsApp debe tener 10 dígitos (lada de México)")
            String whatsapp,
            @NotBlank String roleCode) {
    }
}
