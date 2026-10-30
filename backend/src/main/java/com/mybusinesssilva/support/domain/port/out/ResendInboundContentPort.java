package com.mybusinesssilva.support.domain.port.out;

import java.util.Optional;

/**
 * Puerto de salida para traer el CUERPO de un correo inbound desde Resend (Fase 2).
 *
 * <p><b>Por qué existe (causa raíz de {@code DISCARDED_EMPTY}):</b> el webhook {@code email.received}
 * de Resend entrega SOLO metadatos ({@code from}, {@code to}, {@code subject}, {@code email_id},
 * {@code attachments}); el {@code text}/{@code html} NUNCA viajan en el payload del webhook. Hay que
 * pedirlos aparte a la Receiving API ({@code GET https://api.resend.com/emails/receiving/{email_id}}).
 * Este puerto encapsula esa llamada saliente para mantener el servicio de ingesta puro/hexagonal.
 *
 * <p>Contrato de robustez: NUNCA lanza. Devuelve {@link Optional#empty()} ante cualquier fallo (HTTP
 * de error, timeout, respuesta ilegible o config incompleta) para que la ingesta decida un desenlace
 * distinguible ({@code DISCARDED_CONTENT_FETCH_FAILED}) sin tumbar la transacción. La API key nunca se
 * loggea.
 */
public interface ResendInboundContentPort {

    /**
     * Trae el cuerpo del correo recibido por su {@code email_id}.
     *
     * @param emailId id del correo en Resend ({@code data.email_id} del webhook)
     * @return el contenido ({@code text}/{@code html}) si la API respondió bien; {@link Optional#empty()}
     *         ante cualquier fallo o si no hay {@code email_id}
     */
    Optional<InboundContent> fetchContent(String emailId);

    /**
     * Cuerpo del correo tal como lo devuelve la Receiving API. Ambos campos pueden ser {@code null}:
     * el parser prefiere {@code text} y degrada a {@code html}.
     *
     * @param text cuerpo en texto plano (nullable)
     * @param html cuerpo HTML (nullable)
     */
    record InboundContent(String text, String html) {
    }
}
