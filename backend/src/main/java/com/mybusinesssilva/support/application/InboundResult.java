package com.mybusinesssilva.support.application;

/**
 * Resultado de la ingesta inbound (Fase 2). El controller usa {@code outcome} para decidir el status
 * HTTP (todos los {@code DISCARDED_*} e {@code INSERTED} ⇒ {@code 2xx}; un fallo transitorio se
 * propaga como excepción ⇒ {@code 5xx}).
 *
 * @param outcome   {@code INSERTED} | {@code DISCARDED_ALLOWLIST} | {@code DISCARDED_NO_TOKEN} |
 *                  {@code DISCARDED_NO_TICKET} | {@code DISCARDED_EMPTY} | {@code DUPLICATE}
 * @param ticketId  id interno BIGINT del ticket (nullable)
 * @param messageId id interno BIGINT del mensaje insertado (nullable)
 */
public record InboundResult(String outcome, Long ticketId, Long messageId) {

    public static final String INSERTED = "INSERTED";
    public static final String DISCARDED_ALLOWLIST = "DISCARDED_ALLOWLIST";
    public static final String DISCARDED_NO_TOKEN = "DISCARDED_NO_TOKEN";
    public static final String DISCARDED_NO_TICKET = "DISCARDED_NO_TICKET";
    public static final String DISCARDED_EMPTY = "DISCARDED_EMPTY";
    /** El evento ya estaba reclamado/resuelto (reintento de Resend): no se reprocesa. */
    public static final String DUPLICATE = "DUPLICATE";

    public static InboundResult discarded(String outcome) {
        return new InboundResult(outcome, null, null);
    }
}
