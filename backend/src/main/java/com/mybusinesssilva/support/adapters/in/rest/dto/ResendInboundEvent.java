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
 * @param from      autor del correo ({@code "Nombre" <a@b>} o {@code a@b}) → allowlist
 * @param to        destinatarios (lista); aquí viaja {@code reply+<publicId>@} normalmente
 * @param recipient destinatario único expuesto por Resend (si aplica); fallback de {@code to}
 * @param subject   asunto; fallback del token {@code [PN-<publicId>]}
 * @param text      cuerpo en texto plano (preferido para el parseo)
 * @param html      cuerpo HTML (degradación si no hay {@code text})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResendInboundEvent(
        String from,
        List<String> to,
        String recipient,
        String subject,
        String text,
        String html) {

    /**
     * Sobre de Resend ({@code email.received}): los campos del mensaje viven dentro de {@code data}.
     * Solo se mapean los que consume el flujo inbound; el resto se ignora ({@code ignoreUnknown}).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(
            String from,
            List<String> to,
            String recipient,
            String subject,
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
            @JsonProperty("recipient") String recipient,
            @JsonProperty("subject") String subject,
            @JsonProperty("text") String text,
            @JsonProperty("html") String html) {
        if (data != null) {
            return new ResendInboundEvent(
                    data.from(), data.to(), data.recipient(), data.subject(), data.text(), data.html());
        }
        return new ResendInboundEvent(from, to, recipient, subject, text, html);
    }
}
