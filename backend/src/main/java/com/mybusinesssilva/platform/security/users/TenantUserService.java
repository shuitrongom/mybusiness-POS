package com.mybusinesssilva.platform.security.users;

import com.mybusinesssilva.platform.audit.AuditService;
import com.mybusinesssilva.platform.notifications.NotificationPort;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de usuarios del negocio (cajeros, supervisores, administradores) por parte del
 * Dueño/Administrador del propio negocio. Opera sobre el schema del tenant en curso.
 *
 * <p>El alta genera una contraseña temporal y marca {@code must_change_password} para que el
 * usuario la cambie en su primer ingreso. La contraseña se devuelve una sola vez y, si hay
 * WhatsApp, se envía por el canal de notificaciones (adaptador simulado por ahora).
 */
@Service
public class TenantUserService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;
    private final NotificationPort notificationPort;
    private final AuditService auditService;

    public TenantUserService(JdbcClient jdbc, PasswordEncoder passwordEncoder,
                             NotificationPort notificationPort, AuditService auditService) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.notificationPort = notificationPort;
        this.auditService = auditService;
    }

    /** Lista los usuarios del negocio con su rol. */
    public List<Map<String, Object>> listUsers() {
        return jdbc.sql("""
                SELECT u.id, u.email, u.full_name, u.active, u.whatsapp,
                       u.must_change_password, r.code AS role_code, r.name AS role_name, u.role_id
                FROM app_user u
                LEFT JOIN role r ON r.id = u.role_id
                ORDER BY u.id
                """)
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("email", rs.getString("email"));
                    m.put("fullName", rs.getString("full_name"));
                    m.put("active", rs.getBoolean("active"));
                    m.put("whatsapp", rs.getString("whatsapp"));
                    m.put("mustChangePassword", rs.getBoolean("must_change_password"));
                    m.put("roleId", rs.getObject("role_id"));
                    m.put("roleCode", rs.getString("role_code"));
                    m.put("roleName", rs.getString("role_name"));
                    return m;
                })
                .list();
    }

    /**
     * Crea un usuario del negocio con el rol indicado y una contraseña temporal.
     *
     * @param actor    quién realiza el alta (para auditoría)
     * @param email    correo del nuevo usuario (acceso)
     * @param fullName nombre completo
     * @param whatsapp WhatsApp (opcional; para enviarle la contraseña)
     * @param roleCode código de rol (OWNER, ADMIN, SUPERVISOR, CASHIER o personalizado)
     * @return credenciales generadas (contraseña en claro, solo para mostrar/enviar una vez)
     */
    @Transactional
    public CreatedUser createUser(String actor, String email, String fullName,
                                  String whatsapp, String roleCode) {
        if (jdbc.sql("SELECT count(*) FROM app_user WHERE email = :email")
                .param("email", email).query(Long.class).single() > 0) {
            throw new IllegalArgumentException("Ya existe un usuario con el correo " + email);
        }
        Long roleId = jdbc.sql("SELECT id FROM role WHERE code = :code")
                .param("code", roleCode)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Rol inexistente: " + roleCode));

        String password = generatePassword();
        jdbc.sql("""
                INSERT INTO app_user
                    (email, password_hash, full_name, role_id, whatsapp, must_change_password)
                VALUES (:email, :hash, :name, :role, :whatsapp, TRUE)
                """)
                .param("email", email)
                .param("hash", passwordEncoder.encode(password))
                .param("name", fullName)
                .param("role", roleId)
                .param("whatsapp", whatsapp)
                .update();

        auditService.recordGlobal(actor, "USER_CREATED", "app_user", email,
                Map.of("role", roleCode));

        boolean whatsappSent = sendWhatsApp(whatsapp, fullName, email, password);
        return new CreatedUser(email, password, whatsappSent);
    }

    /** Activa o desactiva un usuario (no borra sus datos ni su historial). */
    @Transactional
    public void setActive(String actor, long userId, boolean active) {
        jdbc.sql("UPDATE app_user SET active = :active WHERE id = :id")
                .param("active", active)
                .param("id", userId)
                .update();
        auditService.recordGlobal(actor, active ? "USER_ENABLED" : "USER_DISABLED",
                "app_user", String.valueOf(userId), Map.of());
    }

    /**
     * Restablece la contraseña de un usuario a una nueva temporal y exige cambiarla al ingresar.
     *
     * @return la nueva contraseña temporal (mostrar/enviar una sola vez)
     */
    @Transactional
    public String resetPassword(String actor, long userId) {
        String password = generatePassword();
        int updated = jdbc.sql("""
                UPDATE app_user SET password_hash = :hash, must_change_password = TRUE
                WHERE id = :id
                """)
                .param("hash", passwordEncoder.encode(password))
                .param("id", userId)
                .update();
        if (updated == 0) {
            throw new IllegalArgumentException("Usuario inexistente: " + userId);
        }
        auditService.recordGlobal(actor, "USER_PASSWORD_RESET", "app_user",
                String.valueOf(userId), Map.of());
        return password;
    }

    private boolean sendWhatsApp(String whatsapp, String fullName, String email, String password) {
        if (whatsapp == null || whatsapp.isBlank()) {
            return false;
        }
        try {
            String message = ("Hola %s, se creó tu acceso a MyBusiness Silva. "
                    + "Usuario: %s | Contraseña temporal: %s. Al ingresar se te pedirá cambiarla.")
                    .formatted(fullName, email, password);
            notificationPort.sendWhatsApp(whatsapp, message);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Usuario recién creado con su contraseña temporal.
     *
     * @param email        correo de acceso
     * @param password     contraseña temporal en claro (mostrar/enviar una sola vez)
     * @param whatsappSent si se envió la contraseña por WhatsApp
     */
    public record CreatedUser(String email, String password, boolean whatsappSent) {
    }
}
