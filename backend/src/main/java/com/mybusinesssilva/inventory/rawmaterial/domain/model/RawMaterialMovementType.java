package com.mybusinesssilva.inventory.rawmaterial.domain.model;

/**
 * Tipos de movimiento del kardex propio de materia prima.
 *
 * <p><b>Invariante enum↔CHECK:</b> los literales de este enum DEBEN coincidir
 * exactamente con los del {@code chk_rm_movement_type} de la migración V47
 * ({@code CHECK (movement_type IN ('CONSUME','PURCHASE_RM','ADJUSTMENT_RM'))}).
 * El servicio solo escribe valores de este enum (nunca un literal suelto), de modo
 * que una violación del CHECK es imposible en el flujo normal. Añadir un tipo nuevo
 * exige una migración aditiva del CHECK en sincronía con este enum.
 */
public enum RawMaterialMovementType {

    /** Consumo de insumo al registrar una tanda (sale: cantidad negativa). */
    CONSUME,

    /** Entrada/reposición de insumo por compra (suma: cantidad positiva). */
    PURCHASE_RM,

    /** Ajuste manual del inventario de insumo. */
    ADJUSTMENT_RM
}
