package com.mybusinesssilva.platform.security.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de autenticación.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final TenantAuthService tenantAuthService;

    public AuthController(AuthService authService, TenantAuthService tenantAuthService) {
        this.authService = authService;
        this.tenantAuthService = tenantAuthService;
    }

    /**
     * Inicio de sesión del Super Admin.
     */
    @PostMapping("/superadmin/login")
    public ResponseEntity<LoginResponse> loginSuperAdmin(@Valid @RequestBody LoginRequest request) {
        try {
            AuthService.AuthTokens tokens = authService.authenticateSuperAdmin(
                    request.email(), request.password(), request.mfaCode());
            return ResponseEntity.ok(new LoginResponse(
                    tokens.accessToken(), tokens.refreshToken(), false));
        } catch (AuthService.AuthException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, null, false));
        }
    }

    /**
     * Inicio de sesión de un usuario de negocio (dueño, admin, supervisor, cajero).
     * Localiza el negocio del usuario por su correo y emite un token con su tenant y módulos.
     * La respuesta indica si el usuario debe cambiar su contraseña temporal en este ingreso.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            TenantAuthService.TenantAuthResult result = tenantAuthService.authenticate(
                    request.email(), request.password());
            return ResponseEntity.ok(new LoginResponse(
                    result.accessToken(), result.refreshToken(), result.mustChangePassword()));
        } catch (AuthService.AuthException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, null, false));
        }
    }

    /**
     * Cambio de contraseña de un usuario de negocio (por ejemplo, el cambio obligatorio del
     * primer ingreso). Valida la contraseña actual y emite un token nuevo al terminar.
     */
    @PostMapping("/change-password")
    public ResponseEntity<LoginResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        try {
            tenantAuthService.changePassword(
                    request.email(), request.currentPassword(), request.newPassword());
            // Reautentica con la nueva contraseña para devolver un token ya sin la marca.
            TenantAuthService.TenantAuthResult result = tenantAuthService.authenticate(
                    request.email(), request.newPassword());
            return ResponseEntity.ok(new LoginResponse(
                    result.accessToken(), result.refreshToken(), false));
        } catch (AuthService.AuthException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new LoginResponse(null, null, false));
        }
    }

    /** Petición de inicio de sesión. */
    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password,
            String mfaCode) {
    }

    /** Petición de cambio de contraseña. */
    public record ChangePasswordRequest(
            @Email @NotBlank String email,
            @NotBlank String currentPassword,
            @NotBlank String newPassword) {
    }

    /** Respuesta con los tokens emitidos y si se requiere cambiar la contraseña. */
    public record LoginResponse(String accessToken, String refreshToken,
                                boolean mustChangePassword) {
    }
}
