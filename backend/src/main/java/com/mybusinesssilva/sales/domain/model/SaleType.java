package com.mybusinesssilva.sales.domain.model;

/**
 * Clasificación de una venta según su composición de renglones.
 *
 * <p>Se deriva en el dominio ({@link Sale#getSaleType()}): es {@link #SPECIAL} si algún
 * renglón es un artículo libre (sin producto de catálogo), {@link #NORMAL} en caso contrario.
 */
public enum SaleType {
    /** Venta de solo catálogo: todos los renglones tienen producto. */
    NORMAL,
    /** Venta especial: al menos un renglón es artículo libre (product_id NULL). */
    SPECIAL
}
