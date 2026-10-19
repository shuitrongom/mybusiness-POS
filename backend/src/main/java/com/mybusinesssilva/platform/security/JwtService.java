package com.mybusinesssilva.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Emite y valida tokens JWT.
 *
 * <p>
 * El token de acceso es de vida corta e incluye los claims necesarios para
 * autorizar sin
 * ir a la base en cada petición: identificador de usuario, tenant, roles y
 * módulos habilitados
 * comercialmente para el negocio. El refresh token es de vida más larga y sirve
 * para renovar.
 */
@Service
public class JwtService {
    private static final String CLAIM_TENANT = "tenant";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_MODULES = "modules";
    private static final String CLAIM_PERMISSIONS = "perms";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    // Secreto por defecto SOLO para desarrollo. Debe coincidir con el default de
    // application.yml
    // (app.security.jwt.secret). En producción NUNCA debe usarse: si JWT_SECRET no
    // se define en
    // Railway, el valor cae a este default público y la validación de abajo aborta
    // el arranque.
    private static final String DEV_DEFAULT_SECRET = "dev-secret-change-me-please-32bytes-minimum-0123456789";
    // Mínimo exigido para HMAC-SHA256: 32 bytes (256 bits).
    private static final int MIN_SECRET_BYTES = 32;
    private final SecretKey key;
    private final SecurityProperties.Jwt jwtProps;

    public JwtService(SecurityProperties properties, Environment env) {
        this.jwtProps = properties.jwt();
        validateSecret(jwtProps.secret(), env);
        this.key = Keys.hmacShaKeyFor(jwtProps.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Verifica que el secreto JWT sea seguro en producción. En el perfil
     * {@code prod}, si el
     * secreto está vacío, es igual al default de desarrollo, o mide menos de 32
     * bytes, el arranque
     * FALLA con un mensaje claro: así nunca se despliega firmando tokens con un
     * secreto público o
     * débil. En dev/test se tolera el default para no estorbar el trabajo local.
     *
     * <p>
     * Recordatorio de despliegue: en Railway (y cualquier entorno de producción)
     * hay que definir
     * la variable de entorno {@code JWT_SECRET} con al menos 32 bytes (256 bits)
     * aleatorios.
     */
    private static void validateSecret(String secret, Environment env) {
        boolean prod = Arrays.asList(env.getActiveProfiles()).contains("prod");
        if (!prod) {
            return;
        }
        String value = secret == null ? "" : secret;
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (value.isBlank() || value.equals(DEV_DEFAULT_SECRET) || bytes < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET debe definirse en producción con al menos 32 bytes (256 bits) "
                            + "aleatorios y distinto del valor por defecto de desarrollo. "
                            + "Defina la variable de entorno JWT_SECRET en Railway antes de desplegar.");
        }
    }

    /**
     * Emite un token de acceso de vida corta.
     *
     * @param subject identificador del usuario (por ejemplo el correo o id)
     * @param tenant  schema del tenant, o vacío para usuarios globales (Super
     *                Admin)
     * @param roles   roles del usuario
     * @param modules módulos habilitados comercialmente para el negocio
     */
    public String issueAccessToken(String subject, String tenant,
            List<String> roles, List<String> modules) {
        return issueAccessToken(subject, tenant, roles, modules, List.of());
    }

    /**
     * Emite un token de acceso incluyendo los permisos efectivos del usuario
     * (module:action).
     *
     * @param permissions permisos granulares del rol del usuario
     */
    public String issueAccessToken(String subject, String tenant,
            List<String> roles, List<String> modules, List<String> permissions) {
        Instant now = Instant.now();
        Instant exp = now.plus(jwtProps.accessTtlMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .issuer(jwtProps.issuer())
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claims(Map.of(
                        CLAIM_TYPE, TYPE_ACCESS,
                        CLAIM_TENANT, tenant == null ? "" : tenant,
                        CLAIM_ROLES, roles,
                        CLAIM_MODULES, modules,
                        CLAIM_PERMISSIONS, permissions == null ? List.of() : permissions))
                .signWith(key)
                .compact();
    }

    /**
     * Emite un refresh token de vida más larga.
     */
    public String issueRefreshToken(String subject, String tenant) {
        Instant now = Instant.now();
        Instant exp = now.plus(jwtProps.refreshTtlDays(), ChronoUnit.DAYS);
        return Jwts.builder()
                .issuer(jwtProps.issuer())
                .subject(subject)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .claims(Map.of(
                        CLAIM_TYPE, TYPE_REFRESH,
                        CLAIM_TENANT, tenant == null ? "" : tenant))
                .signWith(key)
                .compact();
    }

    /**
     * Valida y parsea un token, devolviendo sus claims. Lanza excepción si es
     * inválido o expiró.
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(jwtProps.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public String tenantOf(Claims claims) {
        return claims.get(CLAIM_TENANT, String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> rolesOf(Claims claims) {
        Object roles = claims.get(CLAIM_ROLES);
        return roles instanceof List<?> list ? (List<String>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    public List<String> modulesOf(Claims claims) {
        Object modules = claims.get(CLAIM_MODULES);
        return modules instanceof List<?> list ? (List<String>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    public List<String> permissionsOf(Claims claims) {
        Object perms = claims.get(CLAIM_PERMISSIONS);
        return perms instanceof List<?> list ? (List<String>) list : List.of();
    }
}
