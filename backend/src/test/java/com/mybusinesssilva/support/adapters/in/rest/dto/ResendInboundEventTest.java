package com.mybusinesssilva.support.adapters.in.rest.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.support.application.InboundEmailParser;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Pruebas de binding del payload inbound de Resend (regresión del bug {@code DISCARDED_ALLOWLIST}).
 *
 * <p>El evento real {@code email.received} de Resend tiene estructura de sobre: el remitente viaja en
 * {@code data.from}, NO en un {@code from} de nivel superior. Antes del fix, el DTO leía solo el nivel
 * superior, dejaba {@code from == null} y la allowlist descartaba incluso a un remitente autorizado.
 * Estas pruebas fijan que (1) el sobre se aplana y el {@code from} normalizado matchea una allowlist
 * en minúsculas, y (2) un payload plano sigue bindeando igual.
 */
class ResendInboundEventTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ALLOW = "shuitrongomez05@gmail.com,soporte@puntonubepos.com";

    @Test
    void flattensResendEnvelopeAndAllowsListedSender() {
        // Payload real de Resend: nombre de display con mayúsculas dentro de data.from.
        String json = """
                {
                  "type": "email.received",
                  "created_at": "2026-10-30T00:46:55.000Z",
                  "data": {
                    "email_id": "e-123",
                    "from": "Serch Gómez <ShuitronGomez05@Gmail.com>",
                    "to": ["reply+11111111-2222-3333-4444-555555555555@puntonubepos.com"],
                    "subject": "Re: [PN-11111111-2222-3333-4444-555555555555] ticket",
                    "text": "gracias, resuelto"
                  }
                }
                """;

        ResendInboundEvent event = MAPPER.readValue(json, ResendInboundEvent.class);

        assertThat(event.from()).isEqualTo("Serch Gómez <ShuitronGomez05@Gmail.com>");
        assertThat(InboundEmailParser.normalizeAddress(event.from()))
                .isEqualTo("shuitrongomez05@gmail.com");
        assertThat(InboundEmailParser.isSenderAllowed(event.from(), ALLOW)).isTrue();
        assertThat(event.subject()).contains("[PN-");
    }

    @Test
    void flattensEnvelopeWithBareUppercaseAddress() {
        // email.received entrega el from "pelado"; si llega en mayúsculas, debe matchear igual.
        String json = """
                {
                  "type": "email.received",
                  "data": { "from": "SHUITRONGOMEZ05@GMAIL.COM" }
                }
                """;

        ResendInboundEvent event = MAPPER.readValue(json, ResendInboundEvent.class);

        assertThat(InboundEmailParser.isSenderAllowed(event.from(), ALLOW)).isTrue();
    }

    @Test
    void envelopeWithUnknownSenderIsRejected() {
        String json = """
                {
                  "type": "email.received",
                  "data": { "from": "extrano@evil.com" }
                }
                """;

        ResendInboundEvent event = MAPPER.readValue(json, ResendInboundEvent.class);

        assertThat(InboundEmailParser.isSenderAllowed(event.from(), ALLOW)).isFalse();
    }

    @Test
    void stillBindsFlatPayload() {
        // Compatibilidad: un payload plano (sin sobre data) sigue mapeando al nivel superior.
        String json = """
                { "from": "soporte@puntonubepos.com", "subject": "hola", "text": "cuerpo" }
                """;

        ResendInboundEvent event = MAPPER.readValue(json, ResendInboundEvent.class);

        assertThat(event.from()).isEqualTo("soporte@puntonubepos.com");
        assertThat(event.text()).isEqualTo("cuerpo");
        assertThat(InboundEmailParser.isSenderAllowed(event.from(), ALLOW)).isTrue();
    }
}
