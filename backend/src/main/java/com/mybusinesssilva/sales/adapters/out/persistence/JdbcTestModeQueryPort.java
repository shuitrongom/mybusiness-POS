package com.mybusinesssilva.sales.adapters.out.persistence;

import com.mybusinesssilva.platform.tenancy.TenantContext;
import com.mybusinesssilva.sales.domain.port.out.TestModeQueryPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC de {@link TestModeQueryPort}: lee
 * {@code admin.business.test_mode} por el schema del tenant en curso.
 *
 * <p>
 * El {@code search_path} de un request de tenant ya incluye {@code admin} (ver
 * {@code TenantAwareDataSource}), por eso la tabla se califica explícitamente
 * {@code admin.business}, igual que {@code JdbcBusinessRepository}. Si no hay
 * tenant en contexto o no existe la fila, devuelve {@code false} (operación
 * real por defecto).
 */
@Repository
public class JdbcTestModeQueryPort implements TestModeQueryPort {

    private final JdbcClient jdbc;

    public JdbcTestModeQueryPort(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isTestModeActive() {
        String schema = TenantContext.getTenantId();
        if (schema == null || schema.isBlank()) {
            return false;
        }
        return jdbc.sql("""
                SELECT COALESCE(test_mode, false)
                FROM admin.business
                WHERE schema_name = :schema
                """)
                .param("schema", schema)
                .query(Boolean.class)
                .optional()
                .orElse(false);
    }
}
