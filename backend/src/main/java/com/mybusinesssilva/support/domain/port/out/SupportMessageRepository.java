package com.mybusinesssilva.support.domain.port.out;

import com.mybusinesssilva.support.domain.model.SupportTicketMessage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Puerto de salida del bounded context {@code support} para el hilo de mensajes. */
public interface SupportMessageRepository {

    /** Lista mensajes de un ticket, orden created_at ASC, id ASC. */
    List<SupportTicketMessage> findByTicketId(long ticketId);

    /** Inserta un mensaje y lo devuelve con id/publicId resueltos. */
    SupportTicketMessage insert(SupportTicketMessage message);

    /**
     * Marca el origen del mensaje ({@code PANEL} | {@code INBOUND_EMAIL}). Lo usa la ingesta inbound
     * (Fase 2) tras reutilizar {@code postMessage}, para señalar que la respuesta del equipo llegó por
     * correo, sin alterar la ruta del panel (que deja el default {@code PANEL}).
     */
    void markSource(long messageId, String source);

    /**
     * created_at del ÚLTIMO mensaje SUPER_ADMIN del ticket con created_at &lt; before.
     * Vacío si no hay mensaje SUPER_ADMIN previo. Excluye el recién insertado.
     */
    Optional<Instant> lastSuperAdminMessageAt(long ticketId, Instant before);
}
