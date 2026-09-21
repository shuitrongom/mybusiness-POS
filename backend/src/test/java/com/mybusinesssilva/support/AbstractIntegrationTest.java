package com.mybusinesssilva.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Clase base para pruebas de integración. Usa un contenedor PostgreSQL compartido por toda la
 * suite ({@link SharedPostgresContainer}), lo que evita arrancar un contenedor por clase y los
 * fallos de arranque por agotamiento de recursos.
 *
 * <p>La aplicación se conecta con el rol {@code pos_app} (no superusuario) para que las políticas
 * de Row-Level Security apliquen como en producción. Flyway (migraciones del schema global)
 * corre con el usuario administrador del contenedor.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SharedPostgresContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", () -> SharedPostgresContainer.APP_USER);
        registry.add("spring.datasource.password", () -> SharedPostgresContainer.APP_PASSWORD);
        registry.add("spring.flyway.user", SharedPostgresContainer.INSTANCE::getUsername);
        registry.add("spring.flyway.password", SharedPostgresContainer.INSTANCE::getPassword);
        registry.add("spring.flyway.url", SharedPostgresContainer.INSTANCE::getJdbcUrl);
    }
}
