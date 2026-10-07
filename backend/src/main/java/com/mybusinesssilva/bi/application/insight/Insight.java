package com.mybusinesssilva.bi.application.insight;

/**
 * Insight generado por un {@link InsightEngine}: una observación en lenguaje natural (español)
 * derivada de las métricas del negocio.
 *
 * @param tipo      categoría del insight (p. ej. "ventas", "concentracion", "proyeccion")
 * @param severidad nivel/semántica del insight
 * @param titulo    título corto en español
 * @param mensaje   explicación en español
 * @param valor     valor destacado asociado (texto ya formateado), puede ser nulo
 */
public record Insight(
        String tipo,
        Severity severidad,
        String titulo,
        String mensaje,
        String valor) {

    /** Semántica del insight para distinguir visualmente informativo, positivo o de alerta. */
    public enum Severity {
        INFO,
        POSITIVO,
        ALERTA
    }
}
