package com.mybusinesssilva.platform.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias (sin contenedores) de {@link DeviceDetector}: clasifica el
 * dispositivo a partir del User-Agent para la auditoría del modo prueba.
 */
class DeviceDetectorTest {

    @Test
    void classifiesMobileUserAgents() {
        assertThat(DeviceDetector.classify(
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Mobile Safari/537.36"))
                .isEqualTo("mobile");
        assertThat(DeviceDetector.classify(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15"))
                .isEqualTo("mobile");
        assertThat(DeviceDetector.classify(
                "Mozilla/5.0 (Windows Phone 10.0; Android 6.0.1)"))
                .isEqualTo("mobile");
    }

    @Test
    void classifiesDesktopUserAgents() {
        assertThat(DeviceDetector.classify(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"))
                .isEqualTo("desktop");
        assertThat(DeviceDetector.classify(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15"))
                .isEqualTo("desktop");
    }

    @Test
    void classifiesNullOrBlankAsUnknown() {
        assertThat(DeviceDetector.classify(null)).isEqualTo("unknown");
        assertThat(DeviceDetector.classify("")).isEqualTo("unknown");
        assertThat(DeviceDetector.classify("   ")).isEqualTo("unknown");
    }
}
