package com.mybusinesssilva.support.adapters.in.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Payload del evento inbound de Resend (Fase 2 §2.1.1), solo con los campos que se usan. Binding
 * tolerante (Jackson 3 / {@code tools.jackson} vía el {@code ObjectMapper} del repo): los campos
 * ausentes quedan {@code null}. Se declaran AMBOS posibles portadores del destinatario
 * ({@code to} lista y {@code recipient} string) porque el nombre/forma real del campo lo confirma el
 * dueño con el primer payload real (U2); el parser los recorre en orden determinista.
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
}
