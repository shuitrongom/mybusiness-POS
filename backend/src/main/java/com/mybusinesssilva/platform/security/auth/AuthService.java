package com.mybusinesssilva.platform.security.auth;

import com.mybusinesssilva.platform.security.JwtService;
import com.mybusinesssilva.platform.security.LoginRateLimiter;
import com.mybusinesssilva.platform.security.MfaService;
import com.mybusinesssilva.platform.security.Roles;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Servicio de autenticación del Super Admin.
 *
 * <p>Valida credenciales con Argon2id, aplica limitación de intentos (anti fuerza bruta),
 * exige MFA cuando está activo, y emite los tokens de acceso y refresh.
 */
@Service
public class AuthService {

    private final SuperAdminUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MfaService mfaService;
    private final LoginRateLimiter rateLimiter;

    public AuthService(SuperAdminUserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       MfaService mfaService,
                       LoginRateLimiter rateLimiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mfaService = mfaService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Autentica a un Super Admin y emite tokens.
     *
     * @param email    correo
     * @param password contraseña en claro
     * @param mfaCode  código TOTP (requerido solo si el usuario tiene MFA activo)
     * @return tokens de acceso y refresh
     * @throws AuthException si las credenciales son inválidas, la cuenta está bloqueada o
     *                       falta/está mal el código MFA
     */
    public AuthTokens authenticateSuperAdmin(String email, String password, String mfaCode) {
        String key = "sa:" + email;
        if (rateLimiter.isBlocked(key)) {
            throw new AuthException("Cuenta temporalmente bloqueada por intentos fallidos");
        }

        SuperAdminUser user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.active() || !passwordEncoder.matches(password, user.passwordHash())) {
            rateLimiter.recordFailure(key);
            throw new AuthException("Credenciales inválidas");
        }

        if (user.mfaEnabled()) {
            if (mfaCode == null || !mfaService.verifyCode(user.mfaSecret(), mfaCode)) {
                rateLimiter.recordFailure(key);
                throw new AuthException("Código de doble factor inválido");
            }
        }

        rateLimiter.reset(key);

        // El Super Admin no tiene tenant (opera a nivel plataforma).
        String access = jwtService.issueAccessToken(
                user.email(), "", List.of(Roles.SUPER_ADMIN), List.of());
        String refresh = jwtService.issueRefreshToken(user.email(), "");
        return new AuthTokens(access, refresh);
    }

    /**
     * Renueva el token de acceso del Super Admin a partir de un refresh token válido.
     *
     * <p>Valida que el token sea un refresh legítimo (firma, emisor, vigencia) y que corresponda
     * a un Super Admin (tenant vacío). Emite un nuevo access token con vida corta, conservando el
     * mismo refresh. Así el panel no deja de funcionar cuando el access de 15 minutos expira.
     *
     * @param refreshToken refresh token emitido en el login
     * @return nuevos tokens (access renovado + el mismo refresh)
     * @throws AuthException si el token es inválido, no es de tipo refresh, o el usuario ya no existe/está inactivo
     */
    public AuthTokens refreshSuperAdmin(String refreshToken) {
        io.jsonwebtoken.Claims claims;
        try {
            claims = jwtService.parse(refreshToken);
        } catch (RuntimeException ex) {
            throw new AuthException("Sesión expirada. Inicia sesión de nuevo.");
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new AuthException("Token de renovación inválido");
        }
        String email = claims.getSubject();
        SuperAdminUser user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.active()) {
            throw new AuthException("Usuario no disponible");
        }
        String access = jwtService.issueAccessToken(
                user.email(), "", List.of(Roles.SUPER_ADMIN), List.of());
        return new AuthTokens(access, refreshToken);
    }

    /** Tokens emitidos tras un inicio de sesión exitoso. */
    public record AuthTokens(String accessToken, String refreshToken) {
    }

    /** Error de autenticación (credenciales, MFA o bloqueo). */
    public static class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }
}
