package com.mybusinesssilva.support.domain.model;

import java.util.Set;

/**
 * Estados del ciclo de vida de un ticket de soporte y sus transiciones válidas.
 *
 * <p>Máquina de estados (ver §7 del diseño):
 * <pre>
 *   NUEVO        -> EN_REVISION, DESCARTADO
 *   EN_REVISION  -> RESUELTO, DESCARTADO
 *   RESUELTO     -> CERRADO, EN_REVISION (reabrir)
 *   CERRADO      -> (terminal)
 *   DESCARTADO   -> (terminal)
 * </pre>
 */
public enum TicketStatus {
    NUEVO,
    EN_REVISION,
    RESUELTO,
    CERRADO,
    DESCARTADO;

    /**
     * Indica si el estado actual puede transicionar al estado dado.
     *
     * @param target estado destino
     * @return {@code true} si la transición es válida
     */
    public boolean canTransitionTo(TicketStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case NUEVO -> Set.of(EN_REVISION, DESCARTADO).contains(target);
            case EN_REVISION -> Set.of(RESUELTO, DESCARTADO).contains(target);
            case RESUELTO -> Set.of(CERRADO, EN_REVISION).contains(target);
            case CERRADO, DESCARTADO -> false;
        };
    }
}
