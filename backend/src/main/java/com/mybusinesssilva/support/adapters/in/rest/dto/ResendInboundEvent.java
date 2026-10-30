package com.mybusinesssilva.support.adapters.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Payload del evento inbound de Resend (Fase 2 §2.1.1), solo con los campos que se usan. Binding
 * tolerante (Jackson 3 / {@code tools.jackson} vía el {@code ObjectMapper} del repo): los campos
 * ausentes quedan {@code null}. Se declaran AMBOS posibles portadores del destinatario
 * ({@code to} lista y {@code recipient} string) porque el nombre/forma real del campo lo confirma el
 * dueño con el primer payload real (U2); el parser los recorre en orden determinista.
 *
 * <p><b>Envoltura real de Resend (confirmada, evento {@code email.received}):</b> el payload de
 * Resend tiene estructura de sobre: campos {@code type}/{@code created_at} al nivel superior y TODO
 * el mensaje (remitente, destinatarios, asunto…) anidado dentro de un objeto {@code data}. Es decir,
 * el remitente llega en {@code data.from}, NO en un {@code from} de nivel superior. Para {@code
 * email.received} el {@code from} es la dirección "pelada" (sin nombre de display). Por eso el
 * {@code @JsonCreator} de abajo APLANA {@code data}: si viene el sobre, toma los campos de dentro de
 * {@code data}; si no, usa los de nivel superior (compatibilidad con payloads planos de prueba).
 * Fuente: Resend docs, webhook {@code email.received}
 * (https://resend.com/docs/webhooks/emails/received). Contenido reescrito para cumplir licencias.
 *
 * <p>Nota de robustez: si Resend entregara {@code to} como string en vez de lista, el único ajuste
 * posible es un deserializador tolerante ({@code @JsonDeserialize} string→singleton-list); no se
 * implementa salvo que el binding real lo exija.
 *
 * <p><b>El cuerpo NO viaja en el webhook (causa raíz de {@code DISCARDED_EMPTY}).</b> El evento
 * {@code email.received} de Resend es SOLO metadatos: {@code data.{from,to,subject,email_id,
 * attachments}}. Los campos {@code text}/{@code html} NUNCA vienen en el payload del webhook; hay que
 * pedirlos aparte a la Receiving API ({@code GET https://api.resend.com/emails/receiving/{email_id}}),
 * usando {@code data.email_id} como handle. Por eso se mapea {@code email_id} → {@code emailId}: es la
 * pieza que faltaba. {@code text}/{@code html} se conservan como campos del DTO porque algunos payloads
 * (pruebas, payloads planos, o posibles formatos futuros) sí los traen; si están presentes se usan como
 * fallback y se evita la llamada a la API. Fuente: Resend docs, receiving
 * (https://resend.com/docs/dashboard/inbound/introduction) y webhook {@code email.received}
 * (https://resend.com/docs/webhooks/emails/received). Contenido reescrito para cumplir licencias.
 *
 * <p><b>Correlación del ticket (payload real confirmado):</b> el {@code publicId} del ticket viaja en
 * {@code data.received_for[]} y {@code data.to[]} como {@code reply+<publicId>@dominio} (plus-
 * addressing), y como fallback en {@code data.subject} con el token {@code [PN-<publicId>]} (el asunto
 * puede venir con prefijo {@code Re:}/{@code Fwd:}). {@code received_for} es el carrier más fiable, por
 * eso se mapea explícitamente y el parser lo recorre junto con {@code to}.
 *
 * @param from        autor del correo ({@code "Nombre" <a@b>} o {@code a@b}) → allowlist
 * @param to          destinatarios (lista); aquí viaja {@code reply+<publicId>@} normalmente
 * @param receivedFor destinatario(s) "received for" ({@code data.received_for}); carrier primario del
 *                    token {@code reply+<publicId>@} en el payload real de Resend
 * @param recipient   destinatario único expuesto por Resend (si aplica); fallback de {@code to}
 * @param subject     asunto; fallback del token {@code [PN-<publicId>]}
 * @param emailId     id del correo recibido ({@code data.email_id}); handle para pedir el cuerpo a la
 *                    Receiving API cuando el webhook no trae {@code text}/{@code html} (caso real)
 * @param text        cuerpo en texto plano (preferido para el parseo); normalmente ausente en el webhook
 * @param html        cuerpo HTML (degradación si no hay {@code text}); normalmente ausente en el webhook
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResendInboundEvent(
        String from,
        List<String> to,
        List<String> receivedFor,
        String recipient,
        String subject,
        String emailId,
        String text,
        String html) {

    /**
     * Sobre de Resend ({@code email.received}): los campos del mensaje viven dentro de {@code data}.
     * Solo se mapean los que consume el flujo inbound; el resto se ignora ({@code ignoreUnknown}). El
     * {@code email_id} y {@code received_for} se mapean explícitamente (nombres {@code snake_case} del
     * payload) porque son el handle del cuerpo y el carrier primario de correlación, respectivamente.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(
            String from,
            List<String> to,
            @JsonProperty("received_for") List<String> receivedFor,
            String recipient,
            String subject,
            @JsonProperty("email_id") String emailId,
            String text,
            String html) {
    }

    /**
     * Creador que APLANA el sobre de Resend. Si el payload trae {@code data} (payload real de
     * Resend), los campos del mensaje se toman de ahí; si no, se usan los de nivel superior (payloads
     * planos, p. ej. los de las pruebas unitarias). Los campos del sobre que no se usan ({@code type},
     * {@code created_at}, …) se ignoran vía {@code @JsonIgnoreProperties(ignoreUnknown = true)}.
     */
    @JsonCreator
    static ResendInboundEvent fromEnvelope(
            @JsonProperty("data") Data data,
            @JsonProperty("from") String from,
            @JsonProperty("to") List<String> to,
            @JsonProperty("received_for") List<String> receivedFor,
            @JsonProperty("recipient") String recipient,
            @JsonProperty("subject") String subject,
            @JsonProperty("email_id") String emailId,
            @JsonProperty("text") String text,
            @JsonProperty("html") String html) {
        if (data != null) {
            return new ResendInboundEvent(
                    data.from(), data.to(), data.receivedFor(), data.recipient(), data.subject(),
                    data.emailId(), data.text(), data.html());
        }
        return new ResendInboundEvent(from, to, receivedFor, recipient, subject, emailId, text, html);
    }
}
