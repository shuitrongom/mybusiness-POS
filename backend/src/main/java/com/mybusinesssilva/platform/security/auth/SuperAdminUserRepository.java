package com.mybusinesssilva.platform.security.auth;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Acceso a los usuarios del Super Admin en el schema {@code admin}.
 */
@Repository
public class SuperAdminUserRepository {

    private final JdbcClient jdbc;

    public SuperAdminUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<SuperAdminUser> findByEmail(String email) {
        return jdbc.sql("""
                SELECT id, email, password_hash, full_name, mfa_enabled, mfa_secret, active
                FROM admin.superadmin_user WHERE email = :email
                """)
                .param("email", email)
                .query((rs, rowNum) -> new SuperAdminUser(
                        rs.getLong("id"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getString("full_name"),
                        rs.getBoolean("mfa_enabled"),
                        rs.getString("mfa_secret"),
                        rs.getBoolean("active")))
                .optional();
    }

    public Long insert(String email, String passwordHash, String fullName) {
        return jdbc.sql("""
                INSERT INTO admin.superadmin_user (email, password_hash, full_name)
                VALUES (:email, :hash, :name) RETURNING id
                """)
                .param("email", email)
                .param("hash", passwordHash)
                .param("name", fullName)
                .query(Long.class)
                .single();
    }
}
