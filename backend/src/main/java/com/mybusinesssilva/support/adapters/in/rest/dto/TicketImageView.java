package com.mybusinesssilva.support.adapters.in.rest.dto;

import com.mybusinesssilva.support.domain.model.SupportTicketImage;

/**
 * Vista de una imagen adjunta (solo metadatos públicos de Cloudinary).
 */
public record TicketImageView(
        String url,
        String publicId,
        Integer width,
        Integer height,
        Long bytes) {

    public static TicketImageView from(SupportTicketImage image) {
        return new TicketImageView(
                image.url(), image.publicId(), image.width(), image.height(), image.bytes());
    }
}
