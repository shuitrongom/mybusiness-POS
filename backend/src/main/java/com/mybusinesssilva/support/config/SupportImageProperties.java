package com.mybusinesssilva.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límites de imágenes del buzón, bajo el prefijo {@code app.support.image}.
 *
 * @param maxBytes tamaño máximo por imagen en bytes
 * @param maxCount número máximo de imágenes por ticket
 */
@ConfigurationProperties(prefix = "app.support.image")
public record SupportImageProperties(long maxBytes, int maxCount) {
}
