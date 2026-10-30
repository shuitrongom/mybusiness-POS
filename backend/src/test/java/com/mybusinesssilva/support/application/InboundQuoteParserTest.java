package com.mybusinesssilva.support.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.support.adapters.in.rest.dto.ResendInboundEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias del parseo/limpieza del cuerpo inbound (Fase 2 §5.2): corte de cita/firma SOLO al
 * final, conservación del texto del humano cuando la cita está en medio, red de seguridad "no cortar"
 * y preferencia de {@code text} sobre {@code html}.
 */
class InboundQuoteParserTest {

    @Test
    void cutsSpanishQuoteHeaderAtEnd() {
        String body = "Ya quedó resuelto, gracias.\n\n"
                + "El 12 de marzo de 2026, Soporte <s@p.com> escribió:\n"
                + "> Hola, ¿podrías revisar esto?\n> Saludos";
        assertThat(InboundEmailParser.stripQuotedAndSignature(body))
                .isEqualTo("Ya quedó resuelto, gracias.");
    }

    @Test
    void cutsEnglishQuoteHeaderAtEnd() {
        String body = "Fixed it.\n\nOn Mar 12, 2026, Support <s@p.com> wrote:\n> please check";
        assertThat(InboundEmailParser.stripQuotedAndSignature(body)).isEqualTo("Fixed it.");
    }

    @Test
    void doesNotCutWhenQuoteIsInTheMiddleWithReplyBelow() {
        String body = "> cita de arriba\n> más cita\n\nEsta es mi respuesta real debajo de la cita.";
        String cleaned = InboundEmailParser.stripQuotedAndSignature(body);
        assertThat(cleaned).contains("Esta es mi respuesta real debajo de la cita.");
    }

    @Test
    void doesNotCutOutlookSeparatorInTheMiddle() {
        String body = "Mi respuesta importante.\n" + "_".repeat(20) + "\nCita\n\ny sigo escribiendo aquí.";
        String cleaned = InboundEmailParser.stripQuotedAndSignature(body);
        assertThat(cleaned).contains("y sigo escribiendo aquí.");
    }

    @Test
    void cutsBareSignatureSeparatorAtEnd() {
        // '-- ' al final sin texto después (solo el separador y líneas vacías): SÍ corta.
        String body = "Gracias por la ayuda.\n-- \n";
        assertThat(InboundEmailParser.stripQuotedAndSignature(body))
                .isEqualTo("Gracias por la ayuda.");
    }

    @Test
    void doesNotLoseHumanTextAfterSignatureSeparatorInTheMiddle() {
        // F1 (code-review): un '-- ' con contenido humano real debajo NO debe cortar; se conserva
        // todo el texto (invariante §5.2 / D-PARSE-2 "jamás de menos").
        String body = "Primer punto\n-- \nSegundo punto que SÍ importa";
        String cleaned = InboundEmailParser.stripQuotedAndSignature(body);
        assertThat(cleaned).contains("Primer punto");
        assertThat(cleaned).contains("Segundo punto que SÍ importa");
    }

    @Test
    void safetyNetDoesNotLoseHumanTextWhenCutWouldEmpty() {
        // Solo cita, sin contenido humano arriba: el corte dejaría vacío ⇒ extractNewText devuelve el
        // original (red de seguridad), nunca vacío si el original tenía contenido.
        var e = new ResendInboundEvent("a@b.com", List.of("x"), null, null, "s", null,
                "> solo cita\n> más cita", null);
        String out = InboundEmailParser.extractNewText(e, 65_536);
        assertThat(out).isNotBlank();
        assertThat(out).contains("solo cita");
    }

    @Test
    void emptyOriginalYieldsEmpty() {
        var e = new ResendInboundEvent("a@b.com", List.of("x"), null, null, "s", null, "   \n  ", null);
        assertThat(InboundEmailParser.extractNewText(e, 65_536)).isBlank();
    }

    @Test
    void prefersTextOverHtml() {
        var e = new ResendInboundEvent("a@b.com", List.of("x"), null, null, "s", null,
                "respuesta en texto plano", "<p>respuesta en HTML</p>");
        assertThat(InboundEmailParser.extractNewText(e, 65_536))
                .isEqualTo("respuesta en texto plano");
    }

    @Test
    void fallsBackToHtmlWhenNoText() {
        var e = new ResendInboundEvent("a@b.com", List.of("x"), null, null, "s", null, null,
                "<p>Hola equipo</p><br>segunda línea");
        String out = InboundEmailParser.extractNewText(e, 65_536);
        assertThat(out).contains("Hola equipo");
        assertThat(out).doesNotContain("<p>");
    }
}
