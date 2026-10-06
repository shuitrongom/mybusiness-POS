package com.mybusinesssilva.platform.security.auth;

import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.BusinessModuleRepository;
import com.mybusinesssilva.licensing.domain.port.out.BusinessRepository;
import com.mybusinesssilva.platform.security.JwtService;
import com.mybusinesssilva.platform.security.LoginRateLimiter;
import com.mybusinesssilva.platform.tenancy.TenantContext;
import com.mybusinesssilva.platform.tenancy.TenantSchema;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Autenticación de los usuarios de un negocio (dueño, admin, supervisor, cajero).
 *
 * <p>A diferencia del Super Admin, estos usuarios viven dentro del schema de su tenant. Como el
 * correo es la credencial de acceso, el servicio localiza el negocio del usuario recorriendo los
 * negocios con acceso permitido y buscando el correo en cada schema. Al encontrarlo, valida la
 * contraseña con Argon2id y emite un token con el tenant, el rol y los módulos habilitados.
 *
 * <p>El recorrido por schemas es aceptable para el volumen actual; a mayor escala conviene un
 * índice global de correo → tenant. Se mantiene la barrera de RLS fijando el tenant en el
 * contexto antes de cada consulta.
 */
@Service
public class TenantAuthService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(TenantAuthService.class);

    private final BusinessRepository businessRepository;
    private final BusinessModuleRepository businessModuleRepository;
    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;

    public TenantAuthService(BusinessRepository businessRepository,
                             BusinessModuleRepository businessModuleRepository,
                             JdbcClient jdbc,
                             PasswordEncoder passwordEncoder,
                             JwtService jwtService,
                             LoginRateLimiter rateLimiter) {
        this.businessRepository = businessRepository;
        this.businessModuleRepository = businessModuleRepository;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Autentica a un usuario de negocio y emite tokens.
     *
     * @param email    correo del usuario
     * @param password contraseña en claro
     * @return tokens de acceso y refresh, más si debe cambiar la contraseña en este ingreso
     * @throws AuthService.AuthException si las credenciales son inválidas, no hay negocio con acceso
     *                                   para ese usuario, o la cuenta está bloqueada por intentos
     */
    public TenantAuthResult authenticate(String email, String password) {
        String key = "tenant:" + email;
        if (rateLimiter.isBlocked(key)) {
            throw new AuthService.AuthException("Cuenta temporalmente bloqueada por intentos fallidos");
        }

        for (Business business : businessRepository.findAll()) {
            if (!business.allowsAccess() || business.getSchemaName() == null) {
                continue;
            }
            Optional<TenantUser> found = findUser(business.getSchemaName(), email);
            if (found.isEmpty()) {
                continue;
            }
            TenantUser user = found.get();
            if (!user.active() || !passwordEncoder.matches(password, user.passwordHash())) {
                rateLimiter.recordFailure(key);
                throw new AuthService.AuthException("Credenciales inválidas");
            }

            rateLimiter.reset(key);
            String role = user.roleCode() == null ? "OWNER" : user.roleCode();
            List<String> modules = businessModuleRepository.findEnabledModuleKeys(business.getId());
            List<String> permissions = loadPermissions(business.getSchemaName(), user.email());
            String access = jwtService.issueAccessToken(
                    user.email(), business.getSchemaName(), List.of(role), modules, permissions);
            String refresh = jwtService.issueRefreshToken(user.email(), business.getSchemaName());
            return new TenantAuthResult(access, refresh, user.mustChangePassword());
        }

        rateLimiter.recordFailure(key);
        throw new AuthService.AuthException("Credenciales inválidas");
    }

    /**
     * Renueva el token de acceso de un usuario de negocio a partir de su refresh token.
     *
     * <p>Valida el refresh (firma, emisor, vigencia y tipo), localiza el negocio por el tenant del
     * token, recarga el rol del usuario y los módulos habilitados, y emite un nuevo access token.
     *
     * @param refreshToken refresh token emitido en el login del tenant
     * @return nuevos tokens (access renovado + el mismo refresh)
     * @throws AuthService.AuthException si el token es inválido o el usuario/negocio ya no permite acceso
     */
    public TenantAuthResult refresh(String refreshToken) {
        io.jsonwebtoken.Claims claims;
        try {
            claims = jwtService.parse(refreshToken);
        } catch (RuntimeException ex) {
            throw new AuthService.AuthException("Sesión expirada. Inicia sesión de nuevo.");
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new AuthService.AuthException("Token de renovación inválido");
        }
        String email = claims.getSubject();
        String tenant = jwtService.tenantOf(claims);

        for (Business business : businessRepository.findAll()) {
            if (!business.getSchemaName().equals(tenant)) {
                continue;
            }
            if (!business.allowsAccess()) {
                throw new AuthService.AuthException("El negocio no tiene acceso vigente");
            }
            Optional<TenantUser> found = findUser(business.getSchemaName(), email);
            if (found.isEmpty() || !found.get().active()) {
                throw new AuthService.AuthException("Usuario no disponible");
            }
            TenantUser user = found.get();
            String role = user.roleCode() == null ? "OWNER" : user.roleCode();
            List<String> modules = businessModuleRepository.findEnabledModuleKeys(business.getId());
            List<String> permissions = loadPermissions(business.getSchemaName(), user.email());
            String access = jwtService.issueAccessToken(
                    user.email(), business.getSchemaName(), List.of(role), modules, permissions);
            return new TenantAuthResult(access, refreshToken, user.mustChangePassword());
        }
        throw new AuthService.AuthException("Sesión no válida");
    }

    /**
     * Cambia la contraseña de un usuario de negocio y quita la marca de cambio obligatorio.
     * Se valida la contraseña actual antes de aplicar la nueva.
     *
     * @param email       correo del usuario
     * @param currentPassword contraseña actual (temporal o vigente)
     * @param newPassword nueva contraseña
     * @throws AuthService.AuthException si las credenciales actuales no son válidas
     */
    public void changePassword(String email, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new AuthService.AuthException("La nueva contraseña debe tener al menos 8 caracteres");
        }
        for (Business business : businessRepository.findAll()) {
            if (business.getSchemaName() == null) {
                continue;
            }
            Optional<TenantUser> found = findUser(business.getSchemaName(), email);
            if (found.isEmpty()) {
                continue;
            }
            TenantUser user = found.get();
            if (!passwordEncoder.matches(currentPassword, user.passwordHash())) {
                throw new AuthService.AuthException("La contraseña actual no es correcta");
            }
            updatePassword(business.getSchemaName(), email, passwordEncoder.encode(newPassword));
            return;
        }
        throw new AuthService.AuthException("Usuario no encontrado");
    }

    /** Actualiza el hash y limpia la marca de cambio obligatorio, con tabla calificada por schema. */
    private void updatePassword(String schema, String email, String newHash) {
        TenantSchema.validate(schema);
        // Igual que en findUser: se fija el tenant para que la RLS de app_user permita el UPDATE.
        String previousTenant = TenantContext.getTenantId();
        TenantContext.setTenantId(schema);
        try {
            jdbc.sql(("UPDATE %s.app_user SET password_hash = :hash, must_change_password = FALSE "
                    + "WHERE email = :email").formatted(schema))
                    .param("hash", newHash)
                    .param("email", email)
                    .update();
        } finally {
            if (previousTenant != null) {
                TenantContext.setTenantId(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    /**
     * Busca un usuario por correo dentro del schema del tenant.
     *
     * <p>Las tablas se referencian de forma calificada con el schema ({@code tenant_N.app_user})
     * porque este endpoint corre sin tenant en el contexto (el usuario aún no está autenticado),
     * de modo que el {@code search_path} de la conexión apunta a {@code admin} y no encontraría
     * las tablas del tenant. El nombre del schema se valida antes con {@code TenantSchema.validate}.
     */
    private Optional<TenantUser> findUser(String schema, String email) {
        TenantSchema.validate(schema);
        // Fija el tenant en el contexto para que TenantAwareDataSource aplique el search_path y,
        // sobre todo, la variable de sesión app.current_tenant que exige la política de RLS de
        // app_user. Sin esto, la RLS oculta la fila del usuario durante el login (aún no hay
        // sesión) y el inicio de sesión falla con "Credenciales inválidas" pese a ser correctas.
        String previousTenant = TenantContext.getTenantId();
        TenantContext.setTenantId(schema);
        try {
            return jdbc.sql("""
                    SELECT u.email, u.password_hash, u.active, u.must_change_password, r.code AS role_code
                    FROM %s.app_user u
                    LEFT JOIN %s.role r ON r.id = u.role_id
                    WHERE u.email = :email
                    """.formatted(schema, schema))
                    .param("email", email)
                    .query((rs, rowNum) -> new TenantUser(
                            rs.getString("email"),
                            rs.getString("password_hash"),
                            rs.getBoolean("active"),
                            rs.getBoolean("must_change_password"),
                            rs.getString("role_code")))
                    .optional();
        } catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            // El schema del tenant puede estar en una versión anterior (sin tabla role, por
            // ejemplo). Se omite ese negocio en la búsqueda en vez de abortar el login.
            log.warn("Se omite el schema {} en el login: estructura incompatible ({})",
                    schema, ex.getMostSpecificCause().getMessage());
            return Optional.empty();
        } finally {
            if (previousTenant != null) {
                TenantContext.setTenantId(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    /**
     * Carga los permisos efectivos del usuario (module:action) desde su rol, para embeberlos en el
     * JWT. Fija el tenant en el contexto para satisfacer la RLS de las tablas de roles.
     */
    private List<String> loadPermissions(String schema, String email) {
        TenantSchema.validate(schema);
        String previousTenant = TenantContext.getTenantId();
        TenantContext.setTenantId(schema);
        try {
            return jdbc.sql("""
                    SELECT rp.module_key || ':' || rp.action AS perm
                    FROM %s.app_user u
                    JOIN %s.role_permission rp ON rp.role_id = u.role_id
                    WHERE u.email = :email
                    """.formatted(schema, schema))
                    .param("email", email)
                    .query(String.class)
                    .list();
        } catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            // Schemas en versión anterior (sin role_permission poblada): sin permisos granulares.
            return List.of();
        } finally {
            if (previousTenant != null) {
                TenantContext.setTenantId(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    private record TenantUser(String email, String passwordHash, boolean active,
                              boolean mustChangePassword, String roleCode) {
    }

    /**
     * Resultado del inicio de sesión de un usuario de negocio.
     *
     * @param accessToken        token de acceso
     * @param refreshToken       token de refresco
     * @param mustChangePassword true si debe cambiar la contraseña temporal en este ingreso
     */
    public record TenantAuthResult(String accessToken, String refreshToken,
                                   boolean mustChangePassword) {
    }
}
