package com.mybusinesssilva.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Proveedor del PostgreSQL usado por TODA la suite de pruebas de integración.
 *
 * <p>Soporta dos modos, elegidos automáticamente según el entorno:
 *
 * <ul>
 *   <li><b>Testcontainers (Docker):</b> si Docker está disponible, arranca un contenedor
 *       {@code postgres:17} una sola vez por JVM (patrón "singleton container") y lo reutiliza
 *       entre clases. Es el modo usado en CI.</li>
 *   <li><b>PostgreSQL nativo:</b> si Docker NO está disponible (por ejemplo, una máquina de
 *       desarrollo sin virtualización), las pruebas usan una instancia local de PostgreSQL. Los
 *       datos de conexión se toman de variables de entorno con valores por defecto para el
 *       PostgreSQL nativo en el puerto 5432.</li>
 * </ul>
 *
 * <p>En ambos modos se garantiza un rol de aplicación sin privilegios de superusuario
 * ({@code pos_app_test}) porque PostgreSQL ignora la RLS para superusuarios; así las pruebas de
 * aislamiento multi-tenant son representativas de producción. Este rol es INDEPENDIENTE del
 * {@code pos_app} de desarrollo para no alterar sus credenciales.
 */
public final class SharedPostgresContainer {

    /**
     * Rol de aplicación usado por las pruebas. Es DISTINTO del rol de desarrollo ({@code pos_app})
     * a propósito: así la suite de pruebas nunca modifica las credenciales del rol con el que
     * corre el backend en desarrollo. En modo Docker el contenedor es efímero, pero mantenemos el
     * mismo nombre por consistencia.
     */
    public static final String APP_USER = "pos_app_test";
    public static final String APP_PASSWORD = "pos_app_test";

    /** Indica si Docker está disponible en el entorno de ejecución de las pruebas. */
    public static final boolean DOCKER_AVAILABLE = detectDocker();

    // --- Modo Testcontainers (solo se instancia si hay Docker) ---
    private static PostgreSQLContainer<?> container;

    // --- Datos de conexión efectivos (rellenados según el modo) ---
    private static final String ADMIN_JDBC_URL;
    private static final String ADMIN_USER;
    private static final String ADMIN_PASSWORD;
    private static final String APP_JDBC_URL;

    static {
        if (DOCKER_AVAILABLE) {
            container = new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("mybusiness_silva")
                    .withUsername("pos_admin")
                    .withPassword("pos_admin_dev")
                    .withReuse(false);
            container.start();
            ADMIN_JDBC_URL = container.getJdbcUrl();
            ADMIN_USER = container.getUsername();
            ADMIN_PASSWORD = container.getPassword();
            APP_JDBC_URL = container.getJdbcUrl();
        } else {
            // PostgreSQL nativo. Configurable por variables de entorno; por defecto apunta al
            // PostgreSQL local en el puerto 5432 con la base y credenciales de administración.
            // Base dedicada de pruebas (separada de la de desarrollo) para no contaminar datos.
            String host = envOrDefault("TEST_DB_HOST", "localhost");
            String port = envOrDefault("TEST_DB_PORT", "5432");
            String db = envOrDefault("TEST_DB_NAME", "mybusiness_silva_test");
            ADMIN_USER = envOrDefault("TEST_DB_ADMIN_USER", "postgres");
            ADMIN_PASSWORD = envOrDefault("TEST_DB_ADMIN_PASSWORD", "Pa55worD");
            ADMIN_JDBC_URL = "jdbc:postgresql://" + host + ":" + port + "/" + db;
            APP_JDBC_URL = ADMIN_JDBC_URL;
            // La base dedicada de pruebas puede no existir todavía: la creamos si hace falta,
            // conectándonos a la base 'postgres' (que siempre existe).
            ensureTestDatabaseExists(host, port, db);
        }
        createApplicationRole();
    }

    private SharedPostgresContainer() {
    }

    /** URL JDBC con la que se conecta la aplicación (rol pos_app). */
    public static String appJdbcUrl() {
        return APP_JDBC_URL;
    }

    /** URL JDBC con la que Flyway aplica las migraciones (rol administrador). */
    public static String adminJdbcUrl() {
        return ADMIN_JDBC_URL;
    }

    /** Usuario administrador (dueño) usado por Flyway. */
    public static String adminUser() {
        return ADMIN_USER;
    }

    /** Contraseña del usuario administrador usado por Flyway. */
    public static String adminPassword() {
        return ADMIN_PASSWORD;
    }

    /**
     * Recrea la base de datos de pruebas desde cero (solo en modo nativo), de modo que cada
     * ejecución de la suite arranque con una base limpia, igual que un contenedor fresco de
     * Testcontainers. Se conecta a la base de mantenimiento {@code postgres} y hace DROP + CREATE.
     * Se cierran primero las conexiones activas a esa base para poder eliminarla.
     */
    private static void ensureTestDatabaseExists(String host, String port, String db) {
        String maintenanceUrl = "jdbc:postgresql://" + host + ":" + port + "/postgres";
        try (Connection conn = DriverManager.getConnection(maintenanceUrl, ADMIN_USER, ADMIN_PASSWORD);
             Statement stmt = conn.createStatement()) {
            // Termina conexiones abiertas contra la base de test (si las hubiera) para poder borrarla.
            stmt.execute(
                    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity "
                            + "WHERE datname = '" + db + "' AND pid <> pg_backend_pid()");
            stmt.execute("DROP DATABASE IF EXISTS " + db);
            stmt.execute("CREATE DATABASE " + db);
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "No se pudo recrear la base de datos de pruebas '" + db + "'", e);
        }
    }

    private static boolean detectDocker() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    /**
     * Garantiza que exista el rol de aplicación {@code pos_app} (sin superusuario) con permisos
     * para operar sobre la base. Idempotente: puede correr varias veces sin fallar.
     */
    private static void createApplicationRole() {
        String database = databaseNameFrom(ADMIN_JDBC_URL);
        try (Connection conn = DriverManager.getConnection(ADMIN_JDBC_URL, ADMIN_USER, ADMIN_PASSWORD);
             Statement stmt = conn.createStatement()) {

            // Crea el rol solo si no existe (en el PostgreSQL nativo puede persistir entre corridas).
            stmt.execute(
                    "DO $$ BEGIN "
                            + "IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + APP_USER + "') THEN "
                            + "CREATE ROLE " + APP_USER + " WITH LOGIN PASSWORD '" + APP_PASSWORD + "'; "
                            + "ELSE "
                            + "ALTER ROLE " + APP_USER + " WITH LOGIN PASSWORD '" + APP_PASSWORD + "'; "
                            + "END IF; END $$;");
            stmt.execute("GRANT ALL ON DATABASE " + database + " TO " + APP_USER);
            stmt.execute("GRANT CREATE ON DATABASE " + database + " TO " + APP_USER);
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo preparar el rol de aplicación de prueba", e);
        }
    }

    /** Extrae el nombre de la base de datos de una URL JDBC de PostgreSQL. */
    private static String databaseNameFrom(String jdbcUrl) {
        int lastSlash = jdbcUrl.lastIndexOf('/');
        String afterSlash = jdbcUrl.substring(lastSlash + 1);
        int queryStart = afterSlash.indexOf('?');
        return queryStart >= 0 ? afterSlash.substring(0, queryStart) : afterSlash;
    }
}
