package com.mybusinesssilva.platform.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.support.AbstractIntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pruebas de aislamiento multi-tenant.
 *
 * <p>Verifican que dos negocios aprovisionados en schemas distintos no pueden ver ni tocar
 * los datos del otro, comprobando las dos barreras del diseño:
 * <ul>
 *   <li>Separación por schema (search_path del tenant en curso).</li>
 *   <li>Row-Level Security (política que filtra por {@code app.current_tenant}).</li>
 * </ul>
 */
class TenantIsolationTest extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TenantProvisioningService provisioningService;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
    }

    @Test
    void eachTenantSeesOnlyItsOwnData() {
        String schemaA = "tenant_1001";
        String schemaB = "tenant_1002";

        // Aprovisiona ambos tenants (crea schema + migraciones + RLS).
        provisioningService.provisionSchema(schemaA);
        provisioningService.provisionSchema(schemaB);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Inserta una sucursal en el tenant A.
        TenantContext.setTenantId(schemaA);
        jdbc.update("INSERT INTO branch (name) VALUES (?)", "Sucursal Centro (A)");

        // Inserta una sucursal en el tenant B.
        TenantContext.setTenantId(schemaB);
        jdbc.update("INSERT INTO branch (name) VALUES (?)", "Sucursal Norte (B)");

        // Desde el tenant A solo debe verse la sucursal de A.
        TenantContext.setTenantId(schemaA);
        Integer countA = jdbc.queryForObject("SELECT count(*) FROM branch", Integer.class);
        String nameA = jdbc.queryForObject("SELECT name FROM branch", String.class);
        assertThat(countA).isEqualTo(1);
        assertThat(nameA).isEqualTo("Sucursal Centro (A)");

        // Desde el tenant B solo debe verse la sucursal de B.
        TenantContext.setTenantId(schemaB);
        Integer countB = jdbc.queryForObject("SELECT count(*) FROM branch", Integer.class);
        String nameB = jdbc.queryForObject("SELECT name FROM branch", String.class);
        assertThat(countB).isEqualTo(1);
        assertThat(nameB).isEqualTo("Sucursal Norte (B)");
    }

    @Test
    void rowLevelSecurityBlocksCrossTenantRowsWithinSameSchemaScan() {
        String schemaC = "tenant_2001";
        provisioningService.provisionSchema(schemaC);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Inserta una fila legítima del tenant C.
        TenantContext.setTenantId(schemaC);
        jdbc.update("INSERT INTO branch (name) VALUES (?)", "Sucursal C");

        // Intenta insertar una fila con tenant_id de OTRO tenant en el schema de C.
        // La política RLS con WITH CHECK debe rechazarla.
        boolean rejected = false;
        try {
            jdbc.update(
                    "INSERT INTO " + schemaC + ".branch (tenant_id, name) VALUES (?, ?)",
                    "tenant_9999", "Fila intrusa");
        } catch (Exception ex) {
            rejected = true;
        }
        assertThat(rejected)
                .as("RLS debe rechazar insertar filas de otro tenant")
                .isTrue();

        // El tenant C sigue viendo solo su fila legítima.
        Integer countC = jdbc.queryForObject("SELECT count(*) FROM branch", Integer.class);
        assertThat(countC).isEqualTo(1);
    }
}
