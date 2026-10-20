package com.mybusinesssilva.platform.util;

import java.util.Locale;

/**
 * Clasifica el dispositivo de una petición a partir de su encabezado
 * {@code User-Agent}, para la auditoría del cambio de modo prueba (¿lo hizo el
 * dueño desde su móvil o desde una computadora?).
 *
 * <p>
 * Heurística sin dependencias externas: {@code "mobile"} si el UA contiene
 * marcadores típicos de móvil/tablet; {@code "desktop"} en caso contrario;
 * {@code "unknown"} si el UA es nulo o vacío.
 */
public final class DeviceDetector {

    private static final String[] MOBILE_MARKERS = {
            "mobi", "android", "iphone", "ipad", "ipod", "windows phone"
    };

    private DeviceDetector() {
        // Clase de utilidad: no instanciable.
    }

    /**
     * @param userAgent encabezado {@code User-Agent} de la petición (nullable)
     * @return {@code "mobile"}, {@code "desktop"} o {@code "unknown"}
     */
    public static String classify(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "unknown";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        for (String marker : MOBILE_MARKERS) {
            if (ua.contains(marker)) {
                return "mobile";
            }
        }
        return "desktop";
    }
}
