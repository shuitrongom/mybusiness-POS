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
            return ResponseEntity.ok(new LoginResponse(tokens.accessToken(), tokens.refreshToken()));
        } catch (AuthService.AuthException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, null));
        }
    }

    /**
     * Inicio de sesión de un usuario de negocio (dueño, admin, supervisor, cajero).
     * Localiza el negocio del usuario por su correo y emite un token con su tenant y módulos.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthService.AuthTokens tokens = tenantAuthService.authenticate(
                    request.email(), request.password());
            return ResponseEntity.ok(new LoginResponse(tokens.accessToken(), tokens.refreshToken()));
        } catch (AuthService.AuthException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, null));
        }
    }

    /** Petición de inicio de sesión. */
    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password,
            String mfaCode) {
    }

    /** Respuesta con los tokens emitidos. */
    public record LoginResponse(String accessToken, String refreshToken) {
    }
}
