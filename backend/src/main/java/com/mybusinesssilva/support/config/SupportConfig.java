package com.mybusinesssilva.support.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Registro de la configuración tipada del buzón de soporte y del {@link RestClient} base que
 * comparten los adaptadores salientes (Resend y Cloudinary), replicando el patrón de
 * {@code SecurityConfig} ({@code @EnableConfigurationProperties}).
 *
 * <p>Los records {@code @ConfigurationProperties} no se vuelven beans por sí solos y el proyecto no
 * usa {@code @ConfigurationPropertiesScan}; por eso se registran aquí explícitamente.
 */
@Configuration
@EnableConfigurationProperties({
        NotificationsProperties.class,
        CloudinaryProperties.class,
        SupportImageProperties.class})
public class SupportConfig {

    /**
     * {@link RestClient} base sin URL fija: cada adaptador (Resend/Cloudinary) fija la URI absoluta
     * del endpoint. Estilo {@code OpenFoodFactsImporter}, pero compartido como bean.
     */
    @Bean
    public RestClient supportRestClient() {
        return RestClient.builder().build();
    }
}
