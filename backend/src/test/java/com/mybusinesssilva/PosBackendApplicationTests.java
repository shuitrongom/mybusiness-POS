package com.mybusinesssilva;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.support.AbstractIntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Prueba de humo: verifica que el contexto de Spring arranca y que las migraciones
 * Flyway del schema global {@code admin} se aplican correctamente sobre un PostgreSQL real.
 */
class PosBackendApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
        assertThat(dataSource).isNotNull();
    }

    @Test
    void adminSchemaMigrationsApplied() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer planCount = jdbc.queryForObject(
                "SELECT count(*) FROM admin.plan", Integer.class);
        assertThat(planCount).isEqualTo(3);

        Integer moduleCount = jdbc.queryForObject(
                "SELECT count(*) FROM admin.module_catalog", Integer.class);
        assertThat(moduleCount).isGreaterThanOrEqualTo(15);

        Integer enterpriseModules = jdbc.queryForObject(
                "SELECT count(*) FROM admin.plan_module pm "
                        + "JOIN admin.plan p ON p.id = pm.plan_id "
                        + "WHERE p.code = 'ENTERPRISE'", Integer.class);
        assertThat(enterpriseModules).isEqualTo(15);
    }
}
