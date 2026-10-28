package com.mybusinesssilva.platform.tenant;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC única de {@link BusinessResolver} sobre el schema {@code admin}. Usa el mismo
 * SQL ya probado en producción que vivía en {@code JdbcSupportTicketRepository}.
 */
@Repository
public class JdbcBusinessResolver implements BusinessResolver {

    private final JdbcClient jdbc;

    public JdbcBusinessResolver(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Long> resolveBusinessIdBySchema(String tenantSchema) {
        return jdbc.sql("SELECT id FROM admin.business WHERE schema_name = :schema")
                .param("schema", tenantSchema)
                .query(Long.class)
                .optional();
    }
}
