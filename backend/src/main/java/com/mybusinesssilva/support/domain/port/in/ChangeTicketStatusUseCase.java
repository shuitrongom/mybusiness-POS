package com.mybusinesssilva.support.domain.port.in;

import com.mybusinesssilva.support.domain.model.SupportTicket;
import com.mybusinesssilva.support.domain.model.TicketStatus;
import java.util.UUID;

/**
 * Caso de uso de cambio de estado de un ticket (por {@code publicId}), validando la transición
 * ({@link TicketStatus#canTransitionTo}) y el alcance del solicitante.
 */
public interface ChangeTicketStatusUseCase {

    /** Cambia el estado del ticket; valida transición y visibilidad, y audita el cambio. */
    SupportTicket changeStatus(UUID publicId, TicketStatus newStatus, String note,
                               Actor actor, RequesterScope scope);
}
