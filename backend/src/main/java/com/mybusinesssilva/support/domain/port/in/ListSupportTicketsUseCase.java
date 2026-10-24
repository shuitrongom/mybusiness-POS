package com.mybusinesssilva.support.domain.port.in;

import com.mybusinesssilva.support.domain.model.SupportTicket;
import java.util.List;

/**
 * Caso de uso de listado de tickets según el alcance del solicitante y filtros opcionales.
 */
public interface ListSupportTicketsUseCase {

    /** Lista según el alcance del solicitante ({@code scope}) y filtros. */
    List<SupportTicket> list(TicketQuery query, RequesterScope scope);
}
