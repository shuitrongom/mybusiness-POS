package com.mybusinesssilva.platform.billing;

/**
 * Negocio en prueba cuyo periodo está por vencer dentro de la ventana configurada.
 *
 * @param businessId  identificador del negocio
 * @param name        nombre del negocio
 * @param trialEndsAt fecha de fin de la prueba en formato ISO (hora local de México)
 */
public record TrialEnding(
        long businessId,
        String name,
        String trialEndsAt) {
}
