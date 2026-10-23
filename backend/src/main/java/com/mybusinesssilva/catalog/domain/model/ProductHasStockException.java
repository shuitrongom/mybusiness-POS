package com.mybusinesssilva.catalog.domain.model;

import java.math.BigDecimal;

/**
 * Barrera dura de dominio: no se puede desactivar un producto que todavía tiene
 * existencia normal en alguna sucursal. El dueño debe agotarlo o ajustarlo a 0
 * antes de desactivarlo, para no "esconder" stock vivo del inventario.
 *
 * <p>
 * Se mapea a un 409 {@code PRODUCT_HAS_STOCK} en
 * {@link com.mybusinesssilva.platform.web.GlobalExceptionHandler}, espejo del
 * manejo de {@code INSUFFICIENT_STOCK}.
 */
public class ProductHasStockException extends RuntimeException {

    private final long productId;
    private final String productName;
    private final BigDecimal stock;

    public ProductHasStockException(long productId, String productName, BigDecimal stock) {
        super("Este producto tiene " + stock
                + " piezas en existencia. Agótalo o ajústalo a 0 antes de desactivarlo.");
        this.productId = productId;
        this.productName = productName;
        this.stock = stock;
    }

    public long productId() {
        return productId;
    }

    public String productName() {
        return productName;
    }

    public BigDecimal stock() {
        return stock;
    }
}
