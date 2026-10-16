package com.mybusinesssilva.inventory.domain.model;

import java.math.BigDecimal;

/**
 * Se lanza cuando una salida estricta de inventario (venta de un producto con
 * {@code enforce_stock = TRUE}) pediría más unidades de las que hay disponibles.
 *
 * <p>Es una excepción de dominio no checada: la capa web la traduce a un 409
 * {@code ProblemDetail} con title {@code INSUFFICIENT_STOCK} y expone sus campos como
 * propiedades para que el punto de venta pueda topar la cantidad a lo disponible.
 */
public class InsufficientStockException extends RuntimeException {

    private final long productId;
    private final String productName;
    private final BigDecimal available;
    private final BigDecimal requested;

    public InsufficientStockException(long productId, String productName,
                                      BigDecimal available, BigDecimal requested) {
        super(buildMessage(productName, available, requested));
        this.productId = productId;
        this.productName = productName;
        this.available = available;
        this.requested = requested;
    }

    private static String buildMessage(String productName, BigDecimal available, BigDecimal requested) {
        String nombre = (productName == null || productName.isBlank()) ? "este producto" : "«" + productName + "»";
        return "No hay existencia suficiente de " + nombre + ". Disponible: " + available
                + ", solicitado: " + requested + ". Registra producción o ajusta la cantidad.";
    }

    public long productId() {
        return productId;
    }

    /** @return nombre del producto, o {@code null} si no se pudo resolver. */
    public String productName() {
        return productName;
    }

    public BigDecimal available() {
        return available;
    }

    public BigDecimal requested() {
        return requested;
    }
}
