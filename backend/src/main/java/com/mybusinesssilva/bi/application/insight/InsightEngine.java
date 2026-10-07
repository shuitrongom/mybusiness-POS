package com.mybusinesssilva.bi.application.insight;

import java.util.List;
import java.util.Map;

/**
 * Motor de insights analíticos: a partir de un contexto y un mapa de métricas ya calculadas,
 * produce una lista de {@link Insight} redactados en español.
 *
 * <p>Esta interfaz desacopla la generación de insights de su implementación concreta. Hoy existe
 * una única implementación basada en reglas ({@code RuleBasedInsightEngine}) que no requiere
 * dependencias externas.
 *
 * <p>TODO: en el futuro una {@code LlmInsightEngine} implementará esta MISMA interfaz para generar
 * insights con un modelo de lenguaje (IA). Al compartir el contrato, podrá sustituir a la
 * implementación basada en reglas sin tocar los controladores REST ni el frontend: bastará con
 * cambiar qué bean se inyecta. Por eso el método recibe las métricas ya calculadas y devuelve el
 * mismo tipo {@link Insight}.
 */
public interface InsightEngine {

    /**
     * Genera insights a partir de las métricas del contexto indicado.
     *
     * @param contexto etiqueta del dominio analizado (p. ej. {@code "negocio"})
     * @param metrics  métricas ya calculadas (resumen, series, comparativas, rankings, etc.)
     * @return insights en español; nunca nulo (puede estar vacío)
     */
    List<Insight> generate(String contexto, Map<String, Object> metrics);
}
