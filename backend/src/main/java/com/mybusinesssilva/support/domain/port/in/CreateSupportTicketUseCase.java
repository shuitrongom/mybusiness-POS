package com.mybusinesssilva.support.domain.port.in;

import com.mybusinesssilva.support.domain.model.SupportTicket;
import com.mybusinesssilva.support.domain.model.TicketContext;
import com.mybusinesssilva.support.domain.model.TicketType;
import java.util.List;

/**
 * Caso de uso de alta de un ticket de soporte: valida, sube imágenes (si hay y Cloudinary está
 * activo), persiste, audita y notifica (ver §3 del diseño).
 */
public interface CreateSupportTicketUseCase {

    /** Crea el ticket, sube imágenes (si hay y Cloudinary está activo), audita y notifica. */
    SupportTicket create(NewTicketCommand command);

    /**
     * Comando de alta. El {@link TicketContext} ya viene resuelto por el controller (tenant,
     * usuario, rol, UA, device).
     *
     * @param type        tipo del ticket (ERROR | MEJORA)
     * @param title       título
     * @param description descripción
     * @param screenPath  ruta del front (nullable)
     * @param appVersion  versión del front (nullable)
     * @param context     contexto automático resuelto por el controller
     * @param images      imágenes adjuntas sin validar (bytes + nombre + content-type declarado)
     */
    record NewTicketCommand(
            TicketType type,
            String title,
            String description,
            String screenPath,
            String appVersion,
            TicketContext context,
            List<UploadedImage> images) {
    }

    /**
     * Imagen cruda recibida del cliente. El {@code declaredContentType} NO es confiable: la
     * validación real se hace por magic bytes en el servicio.
     *
     * @param originalFilename    nombre original del archivo (nullable)
     * @param declaredContentType content-type declarado por el cliente (NO confiable)
     * @param bytes               contenido del archivo
     */
    record UploadedImage(String originalFilename, String declaredContentType, byte[] bytes) {
    }
}
