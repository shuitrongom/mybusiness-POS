package com.mybusinesssilva.support.domain.port.out;

import com.mybusinesssilva.support.domain.model.SupportTicketImage;
import com.mybusinesssilva.support.domain.port.in.CreateSupportTicketUseCase.UploadedImage;
import java.util.List;

/**
 * Puerto de salida para subir imágenes a un proveedor externo (Cloudinary) con degradación elegante.
 */
public interface ImageStoragePort {

    /**
     * Sube las imágenes ya validadas y devuelve sus metadatos (url/public_id). Si no hay proveedor
     * configurado, devuelve lista vacía y loggea WARN. Nunca lanza para no tumbar la creación del
     * ticket: los fallos individuales se omiten y se loggean.
     */
    List<SupportTicketImage> upload(List<UploadedImage> images);

    /** Indica si el proveedor está configurado y habilitado. */
    boolean isEnabled();
}
