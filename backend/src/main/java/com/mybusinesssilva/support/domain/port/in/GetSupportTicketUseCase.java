package com.mybusinesssilva.support.domain.port.in;

import com.mybusinesssilva.support.domain.model.SupportTicket;
import java.util.UUID;

/**
 * Caso de uso de consulta de un ticket por su identificador externo (UUID), validando la
 * visibilidad por {@link RequesterScope}.
 */
public interface GetSupportTicketUseCase {

    /** Obtiene el ticket por {@code publicId}; valida visibilidad por {@code scope}. */
    SupportTicket get(UUID publicId, RequesterScope scope);
}
