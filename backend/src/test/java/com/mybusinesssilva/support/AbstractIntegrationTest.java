package com.mybusinesssilva.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Clase base para pruebas de integración. Levanta un PostgreSQL real mediante
 * Testcontainers y expone su conexión a Spring.
 *
 * <p>Detalle de seguridad importante: PostgreSQL ignora la Row-Level Security para los
 * superusuarios. El usuario por defecto de Testcontainers es superusuario, por lo que las
 * pruebas de aislamiento por RLS no serían representativas con él. Para reflejar producción,
 * esta clase crea un rol de aplicación sin privilegios de superusuario ({@code pos_app}) y
 * configura Spring para conectarse con ese rol.
 */
@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {

    private static final String APP_USER = "pos_app";
    private static final String APP_PASSWORD = "pos_app_test";

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("mybusiness_silva")
                    .withUsername("pos_admin")
                    .withPassword("pos_admin_dev");

    static {
        POSTGRES.start();
        createApplicationRole();
    }

    /**
     * Crea el rol de aplicación (no superusuario) y le concede permisos para trabajar con la
     * base. Al no ser superusuario, las políticas de RLS sí se aplican sobre él.
     */
    private static void createApplicationRole() {
        try (Connection conn = java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP ROLE IF EXISTS " + APP_USER);
            stmt.execute("CREATE ROLE " + APP_USER
                    + " WITH LOGIN PASSWORD '" + APP_PASSWORD + "'");
            stmt.execute("GRANT ALL ON DATABASE " + POSTGRES.getDatabaseName()
                    + " TO " + APP_USER);
            // Permite crear schemas (aprovisionamiento de tenants) y usar el schema public.
            stmt.execute("GRANT CREATE ON DATABASE " + POSTGRES.getDatabaseName()
                    + " TO " + APP_USER);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo crear el rol de aplicación de prueba", e);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        // Flyway del schema admin corre con el usuario administrador del contenedor.
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
    }
}
