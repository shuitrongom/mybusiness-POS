package com.mybusinesssilva.inventory.bakery.domain.port.out;

import com.mybusinesssilva.inventory.bakery.domain.model.YieldVerdict;

/**
 * Puerto de análisis de rendimiento de una tanda. La implementación actual es una
 * regla simple (promedio móvil); está encapsulada tras este puerto para sustituirla
 * por una basada en IA sin tocar servicio, controlador ni panel.
 */
public interface YieldAnalyzerPort {

    /**
     * @param batchId tanda a analizar
     * @return veredicto de rendimiento (yield total, promedio móvil, desvío)
     */
    YieldVerdict analyze(long batchId);
}
