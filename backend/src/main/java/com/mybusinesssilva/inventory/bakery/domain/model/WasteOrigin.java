package com.mybusinesssilva.inventory.bakery.domain.model;

/**
 * Origen de un movimiento de merma de panadería. Determina de dónde sale/entra la
 * existencia de merma y qué otra existencia se afecta:
 *
 * <ul>
 *   <li>{@link #CIERRE}: al cerrar caja, el sobrante del stock normal pasa a merma
 *       (baja normal, sube merma). Es el candado automático.</li>
 *   <li>{@link #MANUAL}: el cajero marca pan como merma antes del cierre, desde Salidas
 *       (baja normal, sube merma).</li>
 *   <li>{@link #VENTA}: venta especial de merma en el POS (baja merma, no toca el normal).</li>
 *   <li>{@link #REPARTO}: la merma se dio a un repartidor (baja merma, no toca el normal).</li>
 * </ul>
 */
public enum WasteOrigin {

    /** Transición automática del sobrante normal a merma al cerrar caja. */
    CIERRE(true),

    /** Transición manual anticipada del normal a merma (desde Salidas). */
    MANUAL(true),

    /** Venta de merma en el POS (precio de merma). */
    VENTA(false),

    /** Salida de merma por reparto a repartidor. */
    REPARTO(false);

    private final boolean fromNormalStock;

    WasteOrigin(boolean fromNormalStock) {
        this.fromNormalStock = fromNormalStock;
    }

    /**
     * @return {@code true} si el movimiento transfiere existencia desde el stock normal
     *         hacia la merma (baja normal, sube merma); {@code false} si descuenta de la
     *         existencia de merma (baja merma, sin tocar el normal).
     */
    public boolean isFromNormalStock() {
        return fromNormalStock;
    }
}
