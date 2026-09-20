package com.mybusinesssilva.platform.tenancy;

import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Envuelve el {@link DataSource} autoconfigurado por Spring Boot (HikariCP) con
 * {@link TenantAwareDataSource}, de modo que cada conexión se ajuste al tenant en curso.
 *
 * <p>Se usa un {@link BeanPostProcessor} en lugar de redefinir el DataSource para no entrar
 * en conflicto con la autoconfiguración de Spring Boot: aprovechamos el pool y las
 * propiedades que Boot ya construye a partir de {@code spring.datasource.*} y solo lo
 * decoramos con la lógica de multi-tenancy.
 */
@Configuration
public class TenancyDataSourceConfig {

    @Bean
    public static BeanPostProcessor tenantAwareDataSourcePostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                // Envuelve únicamente el DataSource principal, evitando envolver otros
                // (por ejemplo, uno ya envuelto) y evitando recursión.
                if (bean instanceof DataSource dataSource
                        && !(bean instanceof TenantAwareDataSource)) {
                    return new TenantAwareDataSource(dataSource);
                }
                return bean;
            }
        };
    }
}
