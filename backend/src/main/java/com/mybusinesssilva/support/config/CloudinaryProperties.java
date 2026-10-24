package com.mybusinesssilva.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propiedades de Cloudinary, bajo el prefijo {@code app.cloudinary}.
 *
 * @param url URL completa {@code cloudinary://api_key:api_secret@cloud_name} (secreto). Vacío =&gt;
 *            adaptador no-op (sin imágenes).
 */
@ConfigurationProperties(prefix = "app.cloudinary")
public record CloudinaryProperties(String url) {
}
