package com.mybusinesssilva.platform.tenancy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * DataSource que adapta cada conexión al tenant de la petición en curso.
 *
 * <p>Al obtener una conexión del pool, fija:
 * <ul>
 *   <li>{@code search_path} → el schema del tenant (o {@code admin} si no hay tenant),
 *       de modo que las consultas apunten a las tablas correctas.</li>
 *   <li>{@code app.current_tenant} → el identificador del tenant, usado por las políticas
 *       de Row-Level Security como segunda barrera de aislamiento.</li>
 * </ul>
 *
 * <p>Se usa {@code SET LOCAL}/{@code SET} por conexión. HikariCP restablece el estado de
 * cada conexión al devolverla al pool, por lo que el contexto no se filtra entre peticiones.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    public TenantAwareDataSource(DataSource targetDataSource) {
        super(targetDataSource);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = super.getConnection();
        applyTenant(connection);
        return connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = super.getConnection(username, password);
        applyTenant(connection);
        return connection;
    }

    /**
     * Aplica el schema y la variable de sesión del tenant a la conexión dada.
     *
     * @param connection conexión recién obtenida del pool
     * @throws SQLException si falla la configuración de la sesión
     */
    private void applyTenant(Connection connection) throws SQLException {
        String schema = TenantContext.hasTenant()
                ? TenantSchema.validate(TenantContext.getTenantId())
                : TenantSchema.ADMIN_SCHEMA;
        String tenant = TenantContext.hasTenant() ? TenantContext.getTenantId() : "";

        try (Statement stmt = connection.createStatement()) {
            // El schema del tenant primero y, como respaldo para objetos globales, admin.
            stmt.execute("SET search_path TO " + schema + ", " + TenantSchema.ADMIN_SCHEMA);
            // Variable de sesión consultada por las políticas de Row-Level Security.
            stmt.execute("SET app.current_tenant = '" + tenant.replace("'", "''") + "'");
        }
    }
}
