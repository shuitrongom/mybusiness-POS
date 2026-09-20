package com.mybusinesssilva.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Clase base para pruebas de integración. Levanta un PostgreSQL real mediante
 * Testcontainers y expone su conexión a Spring. El contenedor se comparte entre
 * las clases de prueba que extiendan de esta (patrón "singleton container").
 *
 * <p>Usar PostgreSQL real (y no una base en memoria) es indispensable para verificar
 * comportamientos específicos de Postgres como schema-por-tenant, Row-Level Security
 * y tipos como JSONB.
 */
@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("mybusiness_silva")
                    .withUsername("pos_admin")
                    .withPassword("pos_admin_dev");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
