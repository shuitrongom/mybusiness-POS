package com.mybusinesssilva.platform;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans transversales de la plataforma.
 */
@Configuration
public class PlatformConfig {

    /**
     * Reloj del sistema. Inyectarlo (en lugar de usar {@code Instant.now()} directamente)
     * permite pruebas deterministas del ciclo de licencia.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
