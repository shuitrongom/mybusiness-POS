package com.mybusinesssilva.platform.security.users;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Autorización de acciones sensibles del POS (cancelar venta, descuento mayor al permitido,
 * devolución) mediante el PIN de un supervisor.
 *
 * <p>El cajero, al intentar una acción que su rol no permite o que excede su límite, solicita el
 * PIN de un usuario con capacidad de autorizar ({@code role.can_authorize = true}). Si el PIN
 * coincide con el de alguno de esos usuarios, la acción queda autorizada.
 *
 * <p>El PIN es corto y está separado de la contraseña de acceso; se guarda con hash.
 */
@Service
public class SupervisorAuthService {

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;

    public SupervisorAuthService(JdbcClient jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Verifica un PIN contra los usuarios que pueden autorizar. Devuelve el nombre del supervisor
     * que autorizó, o vacío si el PIN no corresponde a ninguno.
     */
    public java.util.Optional<String> authorize(String pin) {
        if (pin == null || pin.isBlank()) {
            return java.util.Optional.empty();
        }
        var candidates = jdbc.sql("""
                SELECT u.full_name, u.supervisor_pin_hash
                FROM app_user u JOIN role r ON r.id = u.role_id
                WHERE u.active = TRUE AND r.can_authorize = TRUE AND u.supervisor_pin_hash IS NOT NULL
                """)
                .query((rs, n) -> new Candidate(rs.getString("full_name"), rs.getString("supervisor_pin_hash")))
                .list();
        for (Candidate c : candidates) {
            if (passwordEncoder.matches(pin, c.pinHash())) {
                return java.util.Optional.of(c.fullName() == null ? "Supervisor" : c.fullName());
            }
        }
        return java.util.Optional.empty();
    }

    /** Establece o actualiza el PIN de supervisor del usuario indicado (por correo). */
    @Transactional
    public void setPin(String email, String pin) {
        if (pin == null || pin.length() < 4) {
            throw new IllegalArgumentException("El PIN debe tener al menos 4 dígitos");
        }
        int updated = jdbc.sql("UPDATE app_user SET supervisor_pin_hash = :hash WHERE email = :email")
                .param("hash", passwordEncoder.encode(pin))
                .param("email", email)
                .update();
        if (updated == 0) {
            throw new IllegalArgumentException("Usuario no encontrado: " + email);
        }
    }

    private record Candidate(String fullName, String pinHash) {
    }
}
