package com.mybusinesssilva.support.domain.port.out;

import java.util.Optional;

/**
 * Puerto de salida de deduplicación/traza de eventos inbound de correo (Fase 2, schema {@code admin}).
 *
 * <p>Garantiza idempotencia frente a reintentos del webhook de Resend: el mismo evento
 * ({@code svix-id} o hash del cuerpo) se procesa una sola vez. El estado intermedio {@code PROCESSING}
 * y los desenlaces finales ({@code INSERTED} / {@code DISCARDED_*}) viven en
 * {@code admin.support_inbound_event}.
 */
public interface SupportInboundEventRepository {

    /**
     * Reclama el evento para su procesamiento. Inserta una fila {@code PROCESSING} con
     * {@code ON CONFLICT (event_id) DO NOTHING}.
     *
     * @return {@code true} si ESTE proceso ganó el claim (insertó la fila); {@code false} si el evento
     *         ya existía (reintento/entrega duplicada de Resend).
     */
    boolean tryClaim(String eventId);

    /**
     * Marca el desenlace final de un evento ya reclamado.
     *
     * @param eventId   clave del evento
     * @param outcome   {@code INSERTED} | {@code DISCARDED_*}
     * @param reason    motivo legible (nullable)
     * @param ticketId  id interno BIGINT del ticket (nullable si se descartó)
     * @param messageId id interno BIGINT del mensaje insertado (nullable si se descartó)
     */
    void finalizeEvent(String eventId, String outcome, String reason, Long ticketId, Long messageId);

    /** Desenlace actual del evento (para la regla de reintento). {@code PROCESSING} = en curso. */
    Optional<String> outcomeOf(String eventId);
}
