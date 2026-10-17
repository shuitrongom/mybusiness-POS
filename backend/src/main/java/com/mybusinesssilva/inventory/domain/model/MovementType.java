package com.mybusinesssilva.inventory.domain.model;

/**
 * Tipo de movimiento de inventario (kardex).
 */
public enum MovementType {

    /** Entrada por compra a proveedor. */
    PURCHASE,

    /** Salida por venta. */
    SALE,

    /** Ajuste manual (positivo o negativo) con motivo. */
    ADJUSTMENT,

    /** Entrada por traspaso desde otra sucursal. */
    TRANSFER_IN,

    /** Salida por traspaso hacia otra sucursal. */
    TRANSFER_OUT,

    /** Entrada por devolución de cliente. */
    RETURN,

    /**
     * Salida del stock normal porque el pan pasó a MERMA (panadería). No es venta
     * ni traspaso:
     * la unidad sigue en la sucursal pero cambia de estado a merma vendible. Resta
     * del stock
     * normal; la existencia de merma se lleva aparte en {@code bakery_waste_stock}.
     */
    WASTE_OUT
}
