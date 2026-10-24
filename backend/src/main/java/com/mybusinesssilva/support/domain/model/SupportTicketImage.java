package com.mybusinesssilva.support.domain.model;

/**
 * Imagen adjunta a un ticket de soporte. Solo metadatos de Cloudinary; el binario nunca se guarda
 * en la base (ver §1 del diseño).
 *
 * @param url      URL segura de Cloudinary (secure_url)
 * @param publicId identificador público de Cloudinary
 * @param width    ancho en píxeles (nullable)
 * @param height   alto en píxeles (nullable)
 * @param bytes    tamaño en bytes (nullable)
 */
public record SupportTicketImage(
        String url,
        String publicId,
        Integer width,
        Integer height,
        Long bytes) {
}
