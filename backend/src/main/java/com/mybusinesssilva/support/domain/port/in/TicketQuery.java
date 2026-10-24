package com.mybusinesssilva.support.domain.port.in;

import com.mybusinesssilva.support.domain.model.TicketStatus;
import com.mybusinesssilva.support.domain.model.TicketType;
import java.time.Instant;

/**
 * Filtros opcionales para listar tickets. Todos los campos son nullable; un valor {@code null}
 * significa "sin filtrar por ese criterio".
 *
 * @param businessId filtro de negocio (solo aplica al super-admin como filtro de UI; para roles de
 *                   tenant el negocio lo impone el {@link RequesterScope})
 * @param type       filtro por tipo (ERROR | MEJORA)
 * @param status     filtro por estado
 * @param from       desde (created_at &gt;= from)
 * @param to         hasta (created_at &lt;= to)
 * @param q          texto a buscar en título/descripción
 */
public record TicketQuery(
        Long businessId,
        TicketType type,
        TicketStatus status,
        Instant from,
        Instant to,
        String q) {
}
