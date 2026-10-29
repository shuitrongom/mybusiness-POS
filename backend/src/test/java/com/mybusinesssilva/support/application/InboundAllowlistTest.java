package com.mybusinesssilva.support.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de la allowlist de remitentes (Fase 2 §4): normalización de {@code from},
 * comparación lowercase por dirección exacta y fail-closed con allowlist vacía.
 */
class InboundAllowlistTest {

    private static final String ALLOW = "shuitrongomez05@gmail.com,soporte@puntonubepos.com";

    @Test
    void normalizesDisplayNameAddress() {
        assertThat(InboundEmailParser.normalizeAddress("\"Sergio\" <Shuitrongomez05@Gmail.com>"))
                .isEqualTo("shuitrongomez05@gmail.com");
    }

    @Test
    void normalizesBareAddress() {
        assertThat(InboundEmailParser.normalizeAddress("  SOPORTE@PuntoNubePos.com "))
                .isEqualTo("soporte@puntonubepos.com");
    }

    @Test
    void allowedSenderMatches() {
        assertThat(InboundEmailParser.isSenderAllowed("\"X\" <shuitrongomez05@gmail.com>", ALLOW)).isTrue();
        assertThat(InboundEmailParser.isSenderAllowed("SOPORTE@puntonubepos.com", ALLOW)).isTrue();
    }

    @Test
    void unknownSenderIsRejected() {
        assertThat(InboundEmailParser.isSenderAllowed("extrano@evil.com", ALLOW)).isFalse();
    }

    @Test
    void emptyAllowlistIsFailClosed() {
        assertThat(InboundEmailParser.isSenderAllowed("shuitrongomez05@gmail.com", "")).isFalse();
        assertThat(InboundEmailParser.isSenderAllowed("shuitrongomez05@gmail.com", null)).isFalse();
    }

    @Test
    void blankSenderIsRejected() {
        assertThat(InboundEmailParser.isSenderAllowed(null, ALLOW)).isFalse();
        assertThat(InboundEmailParser.isSenderAllowed("", ALLOW)).isFalse();
    }
}
