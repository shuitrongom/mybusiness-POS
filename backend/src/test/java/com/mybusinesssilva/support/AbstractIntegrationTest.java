package com.mybusinesssilva.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Clase base para pruebas de integración. Usa el PostgreSQL provisto por
 * {@link SharedPostgresContainer}, que elige automáticamente entre un contenedor Testcontainers
 * (si hay Docker) o una instancia PostgreSQL nativa (si no lo hay).
 *
 * <p>La aplicación se conecta con el rol {@code pos_app} (no superusuario) para que las políticas
 * de Row-Level Security apliquen como en producción. Flyway (migraciones del schema global) corre
 * con el usuario administrador.
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgresContainer::appJdbcUrl);
        registry.add("spring.datasource.username", () -> SharedPostgresContainer.APP_USER);
        registry.add("spring.datasource.password", () -> SharedPostgresContainer.APP_PASSWORD);
        registry.add("spring.flyway.user", SharedPostgresContainer::adminUser);
        registry.add("spring.flyway.password", SharedPostgresContainer::adminPassword);
        registry.add("spring.flyway.url", SharedPostgresContainer::adminJdbcUrl);
        // El placeholder ${app_user} de las migraciones debe apuntar al rol de app de PRUEBAS,
        // para que los GRANT sobre el schema admin se otorguen a ese rol (no a 'pos_app').
        registry.add("spring.flyway.placeholders.app_user", () -> SharedPostgresContainer.APP_USER);
    }
}
