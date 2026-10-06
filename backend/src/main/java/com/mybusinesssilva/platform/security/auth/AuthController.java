package com.mybusinesssilva.platform.security.auth;

import com.mybusinesssilva.platform.security.JwtService;
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
    private final JwtService jwtService;

    public AuthController(AuthService authService, TenantAuthService tenantAuthService,
                          JwtService jwtService) {
        this.authService = authService;
        this.tenantAuthService = tenantAuthService;
        this.jwtService = jwtService;
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

    /**
     * Renueva el token de acceso usando un refresh token. Funciona para Super Admin y para
     * usuarios de negocio: si el tenant del refresh está vacío es Super Admin; si tiene tenant,
     * es usuario de negocio. El nuevo access se emite con los datos actualizados del usuario.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        try {
            // Parsea y valida el refresh (firma, emisor, vigencia) con el servicio JWT, y lee el
            // claim tenant: vacío = Super Admin; con valor = usuario de negocio.
            String tenant = jwtService.tenantOf(jwtService.parse(request.refreshToken()));
            if (tenant == null || tenant.isBlank()) {
                AuthService.AuthTokens tokens = authService.refreshSuperAdmin(request.refreshToken());
                return ResponseEntity.ok(new LoginResponse(
                        tokens.accessToken(), tokens.refreshToken(), false));
            } else {
                TenantAuthService.TenantAuthResult result = tenantAuthService.refresh(request.refreshToken());
                return ResponseEntity.ok(new LoginResponse(
                        result.accessToken(), result.refreshToken(), result.mustChangePassword()));
            }
        } catch (RuntimeException ex) {
            // Incluye AuthException (credenciales/refresh inválido) y errores de parseo del JWT.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(null, null, false));
        }
    }

    /** Petición de cambio de contraseña. */
    public record ChangePasswordRequest(
            @Email @NotBlank String email,
            @NotBlank String currentPassword,
            @NotBlank String newPassword) {
    }

    /** Petición de renovación de token. */
    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /** Respuesta con los tokens emitidos y si se requiere cambiar la contraseña. */
    public record LoginResponse(String accessToken, String refreshToken,
                                boolean mustChangePassword) {
    }
}
