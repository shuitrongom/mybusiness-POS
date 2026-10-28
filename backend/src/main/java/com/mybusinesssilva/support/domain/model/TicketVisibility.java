package com.mybusinesssilva.support.domain.model;

import com.mybusinesssilva.support.domain.port.in.RequesterScope;

/**
 * Punto ÚNICO de verdad de la visibilidad de un ticket según el alcance del solicitante (resolución
 * H2 del diseño). La lógica es idéntica a la que vivía como método privado en
 * {@code SupportTicketService.isVisible}: no introduce reglas nuevas.
 */
public final class TicketVisibility {

    private TicketVisibility() {
    }

    public static boolean isVisible(SupportTicket ticket, RequesterScope scope) {
        return switch (scope) {
            case RequesterScope.SuperAdmin ignored -> true;
            case RequesterScope.BusinessOwner owner ->
                    ticket.businessId() != null && ticket.businessId() == owner.businessId();
            case RequesterScope.Reporter reporter ->
                    ticket.businessId() != null && ticket.businessId() == reporter.businessId()
                            && reporter.reporterUserId() != null
                            && reporter.reporterUserId().equals(ticket.reporterUserId());
        };
    }
}
