package com.mybusinesssilva.licensing.application;

import com.mybusinesssilva.platform.tenancy.TenantContext;
import java.security.SecureRandom;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea el usuario Dueño inicial dentro del schema de un negocio recién aprovisionado, y le asigna
 * el rol OWNER. Devuelve las credenciales generadas para que el Super Admin las entregue al cliente.
 *
 * <p>La contraseña se genera aleatoriamente y se almacena con hash Argon2id (nunca en claro).
 * El Super Admin la ve UNA sola vez, al momento de crear el negocio.
 */
@Component
public class BusinessOwnerProvisioner {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;

    public BusinessOwnerProvisioner(JdbcClient jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Crea el usuario dueño en el schema del tenant indicado.
     *
     * <p>El usuario queda marcado con {@code must_change_password = true} para forzar el cambio
     * de la contraseña temporal en su primer ingreso, y se guarda su WhatsApp de contacto.
     *
     * @param schema   schema del tenant (por ejemplo {@code tenant_12})
     * @param email    correo del dueño (identificador de acceso)
     * @param fullName nombre del dueño
     * @param whatsapp WhatsApp de contacto del dueño (puede ser nulo)
     * @return las credenciales generadas (contraseña en claro, solo para mostrar una vez)
     */
    public OwnerCredentials createOwner(String schema, String email, String fullName, String whatsapp) {
        String password = generatePassword();

        // Preserva el tenant que hubiera en curso para restaurarlo al terminar.
        String previousTenant = TenantContext.getTenantId();
        // Fija el tenant para que las tablas del schema correcto se usen y la RLS aplique.
        // Nota: NO se usa @Transactional aquí a propósito. El proxy transaccional abre la
        // conexión al ENTRAR al método (antes de fijar el tenant), y esa conexión quedaría
        // apuntando al schema equivocado. Sin transacción, cada consulta obtiene la conexión
        // ya con el search_path del tenant aplicado por TenantAwareDataSource.
        TenantContext.setTenantId(schema);
        try {
            Long roleId = jdbc.sql("SELECT id FROM role WHERE code = 'OWNER'")
                    .query(Long.class)
                    .optional()
                    .orElse(null);

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
        } finally {
            if (previousTenant != null) {
                TenantContext.setTenantId(previousTenant);
            } else {
                TenantContext.clear();
            }
        }

        return new OwnerCredentials(email, password);
    }

    /**
     * Crea la sucursal principal ("Matriz") del negocio si aún no existe ninguna. Un negocio
     * necesita al menos una sucursal para poder registrar ventas (el POS opera contra ella).
     *
     * @param schema schema del tenant
     */
    public void createDefaultBranch(String schema) {
        String previousTenant = TenantContext.getTenantId();
        TenantContext.setTenantId(schema);
        try {
            long branches = jdbc.sql("SELECT count(*) FROM branch")
                    .query(Long.class)
                    .single();
            if (branches == 0) {
                jdbc.sql("INSERT INTO branch (name, code, active) VALUES ('Matriz', 'MATRIZ', TRUE)")
                        .update();
            }
        } finally {
            if (previousTenant != null) {
                TenantContext.setTenantId(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Credenciales del dueño generadas al crear el negocio.
     *
     * @param email    correo de acceso
     * @param password contraseña en claro (mostrar una sola vez; luego solo queda el hash)
     */
    public record OwnerCredentials(String email, String password) {
    }
}
