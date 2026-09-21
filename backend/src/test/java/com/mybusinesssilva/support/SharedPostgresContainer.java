package com.mybusinesssilva.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Contenedor PostgreSQL compartido por TODA la suite de pruebas de integración (patrón
 * "singleton container"). Se arranca una sola vez en la JVM de pruebas y se reutiliza entre
 * clases, evitando levantar múltiples contenedores (que agota recursos y causa fallos de arranque).
 *
 * <p>El contenedor no se detiene explícitamente: Testcontainers lo limpia al terminar la JVM
 * (mediante Ryuk). Esto es intencional para reutilizarlo entre clases de prueba.
 *
 * <p>Nota de seguridad: se crea un rol de aplicación sin privilegios de superusuario
 * ({@code pos_app}) porque PostgreSQL ignora la RLS para superusuarios; así las pruebas de
 * aislamiento son representativas de producción.
 */
public final class SharedPostgresContainer {

    public static final String APP_USER = "pos_app";
    public static final String APP_PASSWORD = "pos_app_test";

    public static final PostgreSQLContainer<?> INSTANCE =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("mybusiness_silva")
                    .withUsername("pos_admin")
                    .withPassword("pos_admin_dev")
                    .withReuse(false);

    static {
        INSTANCE.start();
        createApplicationRole();
    }

    private SharedPostgresContainer() {
    }

    private static void createApplicationRole() {
        try (Connection conn = java.sql.DriverManager.getConnection(
                INSTANCE.getJdbcUrl(), INSTANCE.getUsername(), INSTANCE.getPassword());
             Statement stmt = conn.createStatement()) {

            stmt.execute("DROP ROLE IF EXISTS " + APP_USER);
            stmt.execute("CREATE ROLE " + APP_USER
                    + " WITH LOGIN PASSWORD '" + APP_PASSWORD + "'");
            stmt.execute("GRANT ALL ON DATABASE " + INSTANCE.getDatabaseName()
                    + " TO " + APP_USER);
            stmt.execute("GRANT CREATE ON DATABASE " + INSTANCE.getDatabaseName()
                    + " TO " + APP_USER);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo crear el rol de aplicación de prueba", e);
        }
    }
}
