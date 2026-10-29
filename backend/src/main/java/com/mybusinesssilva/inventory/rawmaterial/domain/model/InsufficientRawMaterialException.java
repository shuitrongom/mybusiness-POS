package com.mybusinesssilva.inventory.rawmaterial.domain.model;

import java.math.BigDecimal;

/**
 * Se lanza cuando una tanda pediría más insumo del disponible en existencia.
 *
 * <p>Extiende {@link RuntimeException} directamente (NO {@code IllegalArgumentException}
 * — que caería en el handler 422 — ni {@code IllegalStateException} — que caería en 409
 * sin el payload de detalle). La capa web la traduce a un 409 {@code ProblemDetail} con
 * title {@code INSUFFICIENT_RAW_MATERIAL} y expone sus campos para que el panadero vea un
 * modal claro. Espejo de {@code InsufficientStockException}.
 */
public class InsufficientRawMaterialException extends RuntimeException {

    private final long rawMaterialId;
    private final String rawMaterialName;
    private final BigDecimal available;
    private final BigDecimal requested;

    public InsufficientRawMaterialException(long rawMaterialId, String rawMaterialName,
                                            BigDecimal available, BigDecimal requested) {
        super(buildMessage(rawMaterialName, available, requested));
        this.rawMaterialId = rawMaterialId;
        this.rawMaterialName = rawMaterialName;
        this.available = available;
        this.requested = requested;
    }

    private static String buildMessage(String name, BigDecimal available, BigDecimal requested) {
        String nombre = (name == null || name.isBlank()) ? "este insumo" : "«" + name + "»";
        return "No hay suficiente " + nombre + ". Disponible: " + available
                + ", pediste: " + requested + ". Ajusta la cantidad o pide reposición al admin.";
    }

    public long rawMaterialId() {
        return rawMaterialId;
    }

    /** @return nombre del insumo, o {@code null} si no se pudo resolver. */
    public String rawMaterialName() {
        return rawMaterialName;
    }

    public BigDecimal available() {
        return available;
    }

    public BigDecimal requested() {
        return requested;
    }
}
