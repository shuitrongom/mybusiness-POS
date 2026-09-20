package com.mybusinesssilva.platform.tenancy;

import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aprovisiona la infraestructura de datos de un negocio (tenant):
 * <ol>
 *   <li>Crea el schema {@code tenant_<id>} si no existe.</li>
 *   <li>Ejecuta las migraciones del tenant sobre ese schema (Flyway con placeholder).</li>
 * </ol>
 *
 * <p>Las migraciones del tenant viven en {@code classpath:db/migration/tenant} y usan el
 * placeholder {@code ${tenant_schema}} para apuntar al schema destino. Cada schema lleva su
 * propia tabla de historial de Flyway, de modo que las versiones se controlan por tenant.
 */
@Service
public class TenantProvisioningService {

    private final DataSource dataSource;

    public TenantProvisioningService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Aprovisiona el schema de un negocio a partir de su identificador.
     *
     * @param businessId identificador del negocio en el schema {@code admin}
     * @return el nombre del schema creado (por ejemplo {@code tenant_12})
     */
    @Transactional
    public String provision(long businessId) {
        String schema = TenantSchema.forBusinessId(businessId);
        return provisionSchema(schema);
    }

    /**
     * Aprovisiona un schema de tenant por su nombre. Idempotente: si el schema ya existe,
     * Flyway simplemente aplica las migraciones pendientes.
     *
     * @param schema nombre de schema válido ({@code tenant_<id>})
     * @return el nombre del schema
     */
    public String provisionSchema(String schema) {
        TenantSchema.validate(schema);

        // 1) Crear el schema si no existe (fuera de RLS; operación de estructura).
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + schema);

        // 2) Migrar el schema del tenant con Flyway, sustituyendo el placeholder.
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration/tenant")
                .placeholders(Map.of("tenant_schema", schema))
                .table("flyway_schema_history")
                .baselineOnMigrate(true)
                .load()
                .migrate();

        return schema;
    }
}
