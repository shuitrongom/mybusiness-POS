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
     * <p>En el camino de alta <strong>no</strong> se ejecuta {@code flyway.repair()}: el schema
     * recién creado tiene un historial de Flyway vacío, así que {@code repair()} no realiza trabajo
     * útil (solo realinea checksums de un historial ya existente) pero sí paga el costo de leer y
     * re-checksumear todas las migraciones, lo que ralentizaba el alta. La reparación de historiales
     * ya existentes (re-provisión/actualización masiva) se cubre en {@link #provisionOrRepair(String)}.
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
        configureFlyway(schema).migrate();

        return schema;
    }

    /**
     * Aprovisiona o repara el schema de un tenant: realinea los checksums del historial de Flyway
     * ({@code repair()}) y luego aplica las migraciones pendientes ({@code migrate()}). Pensado para
     * arranque o migración masiva de tenants <em>ya existentes</em>, donde un historial previo puede
     * tener checksums desalineados; <strong>no</strong> se invoca en el alta de un negocio nuevo.
     *
     * @param schema nombre de schema válido ({@code tenant_<id>})
     * @return el nombre del schema
     */
    public String provisionOrRepair(String schema) {
        TenantSchema.validate(schema);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + schema);

        Flyway flyway = configureFlyway(schema);
        // Repara el historial antes de migrar: realinea los checksums registrados con los de los
        // archivos actuales. Esto tolera cambios en migraciones ya aplicadas (idempotentes) sin
        // abortar por "checksum mismatch", que de otro modo dejaría al tenant sin poder actualizarse.
        flyway.repair();
        flyway.migrate();

        return schema;
    }

    /**
     * Construye el objeto {@link Flyway} para migrar el schema del tenant, con el placeholder
     * {@code ${tenant_schema}} apuntando al schema destino y su propia tabla de historial.
     *
     * @param schema nombre de schema del tenant
     * @return instancia de Flyway configurada (sin ejecutar aún)
     */
    private Flyway configureFlyway(String schema) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration/tenant")
                .placeholders(Map.of("tenant_schema", schema))
                .table("flyway_schema_history")
                .baselineOnMigrate(true)
                .load();
    }

    /**
     * Elimina por completo el schema de un tenant y todos sus datos (operación irreversible).
     * Se usa al dar de baja definitivamente un negocio; conviene generar un respaldo antes.
     *
     * @param schema nombre de schema válido ({@code tenant_<id>})
     */
    @Transactional
    public void dropSchema(String schema) {
        TenantSchema.validate(schema);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }
}
