package com.mybusinesssilva.platform.security.auth;

import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.BusinessModuleRepository;
import com.mybusinesssilva.licensing.domain.port.out.BusinessRepository;
import com.mybusinesssilva.platform.security.JwtService;
import com.mybusinesssilva.platform.security.LoginRateLimiter;
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
     * @return tokens de acceso y refresh
     * @throws AuthService.AuthException si las credenciales son inválidas, no hay negocio con acceso
     *                                   para ese usuario, o la cuenta está bloqueada por intentos
     */
    public AuthService.AuthTokens authenticate(String email, String password) {
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
            String access = jwtService.issueAccessToken(
                    user.email(), business.getSchemaName(), List.of(role), modules);
            String refresh = jwtService.issueRefreshToken(user.email(), business.getSchemaName());
            return new AuthService.AuthTokens(access, refresh);
        }

        rateLimiter.recordFailure(key);
        throw new AuthService.AuthException("Credenciales inválidas");
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
        try {
            return jdbc.sql("""
                    SELECT u.email, u.password_hash, u.active, r.code AS role_code
                    FROM %s.app_user u
                    LEFT JOIN %s.role r ON r.id = u.role_id
                    WHERE u.email = :email
                    """.formatted(schema, schema))
                    .param("email", email)
                    .query((rs, rowNum) -> new TenantUser(
                            rs.getString("email"),
                            rs.getString("password_hash"),
                            rs.getBoolean("active"),
                            rs.getString("role_code")))
                    .optional();
        } catch (org.springframework.jdbc.BadSqlGrammarException ex) {
            // El schema del tenant puede estar en una versión anterior (sin tabla role, por
            // ejemplo). Se omite ese negocio en la búsqueda en vez de abortar el login.
            log.warn("Se omite el schema {} en el login: estructura incompatible ({})",
                    schema, ex.getMostSpecificCause().getMessage());
            return Optional.empty();
        }
    }

    private record TenantUser(String email, String passwordHash, boolean active, String roleCode) {
    }
}
