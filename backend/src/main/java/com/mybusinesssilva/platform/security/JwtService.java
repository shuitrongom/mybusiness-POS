package com.mybusinesssilva.platform.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Emite y valida tokens JWT.
 *
 * <p>El token de acceso es de vida corta e incluye los claims necesarios para autorizar sin
 * ir a la base en cada petición: identificador de usuario, tenant, roles y módulos habilitados
 * comercialmente para el negocio. El refresh token es de vida más larga y sirve para renovar.
 */
@Service
public class JwtService {

    private static final String CLAIM_TENANT = "tenant";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_MODULES = "modules";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final SecurityProperties.Jwt jwtProps;

    public JwtService(SecurityProperties properties) {
        this.jwtProps = properties.jwt();
        this.key = Keys.hmacShaKeyFor(jwtProps.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Emite un token de acceso de vida corta.
     *
     * @param subject identificador del usuario (por ejemplo el correo o id)
     * @param tenant  schema del tenant, o vacío para usuarios globales (Super Admin)
     * @param roles   roles del usuario
     * @param modules módulos habilitados comercialmente para el negocio
     */
    public String issueAccessToken(String subject, String tenant,
                                   List<String> roles, List<String> modules) {
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
                        CLAIM_MODULES, modules))
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
     * Valida y parsea un token, devolviendo sus claims. Lanza excepción si es inválido o expiró.
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
}
