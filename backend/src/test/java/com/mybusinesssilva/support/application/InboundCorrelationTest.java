package com.mybusinesssilva.support.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.support.adapters.in.rest.dto.ResendInboundEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de la correlación ticket ↔ correo (Fase 2 §2.1.1): resolución determinista del
 * {@code publicId} desde la lista {@code to}, el string {@code recipient} o el token del asunto.
 */
class InboundCorrelationTest {

    private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID ID2 = UUID.fromString("99999999-8888-7777-6666-555555555555");

    private static ResendInboundEvent event(List<String> to, String recipient, String subject) {
        return new ResendInboundEvent(
                "equipo@puntonubepos.com", to, null, recipient, subject, null, "hola", null);
    }

    private static ResendInboundEvent eventReceivedFor(List<String> receivedFor, String subject) {
        return new ResendInboundEvent(
                "equipo@puntonubepos.com", null, receivedFor, null, subject, null, "hola", null);
    }

    @Test
    void resolvesFromToList() {
        var e = event(List.of("reply+" + ID + "@puntonubepos.com"), null, "Re: cualquier cosa");
        assertThat(InboundEmailParser.resolvePublicId(e)).contains(ID);
    }

    @Test
    void resolvesFromReceivedForList() {
        // Payload real de Resend: el token reply+<uuid>@ viaja en data.received_for (carrier primario).
        var e = eventReceivedFor(List.of("reply+" + ID + "@puntonubepos.com"),
                "Re: [Soporte PuntoNube] [PN-" + ID2 + "] Error X");
        assertThat(InboundEmailParser.resolvePublicId(e)).contains(ID);
    }

    @Test
    void resolvesFromRecipientString() {
        var e = event(null, "reply+" + ID + "@puntonubepos.com", "Re: cualquier cosa");
        assertThat(InboundEmailParser.resolvePublicId(e)).contains(ID);
    }

    @Test
    void resolvesFromSubjectToken() {
        var e = event(List.of("soporte@puntonubepos.com"), null, "Re: [PN-" + ID + "] Error X");
        assertThat(InboundEmailParser.resolvePublicId(e)).contains(ID);
    }

    @Test
    void toListIgnoresNonMatchingRecipients() {
        var e = event(List.of("cc@externo.com", "reply+" + ID + "@puntonubepos.com"), null, "x");
        assertThat(InboundEmailParser.resolvePublicId(e)).contains(ID);
    }

    @Test
    void firstTokenWinsWhenMultiplePresent() {
        var e = event(List.of("reply+" + ID + "@puntonubepos.com",
                "reply+" + ID2 + "@puntonubepos.com"), null, "x");
        assertThat(InboundEmailParser.resolvePublicId(e)).contains(ID);
    }

    @Test
    void toTakesPrecedenceOverSubject() {
        var e = event(List.of("reply+" + ID + "@puntonubepos.com"), null, "Re: [PN-" + ID2 + "] x");
        assertThat(InboundEmailParser.resolvePublicId(e)).contains(ID);
    }

    @Test
    void invalidUuidIsIgnored() {
        var e = event(List.of("reply+not-a-uuid-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx@puntonubepos.com"),
                null, "sin token");
        assertThat(InboundEmailParser.resolvePublicId(e)).isEmpty();
    }

    @Test
    void absenceOfTokenReturnsEmpty() {
        var e = event(List.of("soporte@puntonubepos.com"), "soporte@puntonubepos.com", "Re: sin token");
        assertThat(InboundEmailParser.resolvePublicId(e)).isEqualTo(Optional.empty());
    }
}
