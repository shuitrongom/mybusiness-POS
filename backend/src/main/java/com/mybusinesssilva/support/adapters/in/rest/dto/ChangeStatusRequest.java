package com.mybusinesssilva.support.adapters.in.rest.dto;

import com.mybusinesssilva.support.domain.model.TicketStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body para cambiar el estado de un ticket. La validez de la transición se comprueba en el servicio
 * ({@code TicketStatus.canTransitionTo}).
 *
 * @param status estado destino (obligatorio)
 * @param note   nota opcional del cambio
 */
public record ChangeStatusRequest(
        @NotNull TicketStatus status,
        @Size(max = 2000) String note) {
}
