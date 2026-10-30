package com.mybusinesssilva.inventory.rawmaterial.domain.model;

/**
 * Se lanza cuando se intenta BORRAR (hard delete) un insumo que NO es virgen: ya tuvo
 * existencia (stock distinto de 0) o movimientos en el kardex. La regla del dueño es
 * "no borrar lo que ya se usó": solo se puede borrar físicamente un insumo creado por
 * error que nunca movió nada; en cualquier otro caso se debe DESACTIVAR para conservar
 * su historial.
 *
 * <p>Extiende {@link RuntimeException} directamente (NO {@code IllegalArgumentException}
 * — que caería en el handler 422 — ni {@code IllegalStateException} — que caería en 409
 * sin payload). La capa web la traduce a un 409 {@code ProblemDetail} con title
 * {@code RAW_MATERIAL_IN_USE} y expone sus campos para que el front ofrezca "Desactivar"
 * en lugar de borrar. Espejo de {@code ProductHasStockException}.
 */
public class RawMaterialInUseException extends RuntimeException {

    private final long rawMaterialId;
    private final String rawMaterialName;
    private final boolean hasStock;
    private final boolean hasMovements;

    public RawMaterialInUseException(long rawMaterialId, String rawMaterialName,
                                     boolean hasStock, boolean hasMovements) {
        super(buildMessage(rawMaterialName));
        this.rawMaterialId = rawMaterialId;
        this.rawMaterialName = rawMaterialName;
        this.hasStock = hasStock;
        this.hasMovements = hasMovements;
    }

    private static String buildMessage(String name) {
        String nombre = (name == null || name.isBlank()) ? "Este insumo" : "«" + name + "»";
        return nombre + " tiene existencia o movimientos; no se puede borrar."
                + " Desactívalo para conservar su historial.";
    }

    public long rawMaterialId() {
        return rawMaterialId;
    }

    public String rawMaterialName() {
        return rawMaterialName;
    }

    public boolean hasStock() {
        return hasStock;
    }

    public boolean hasMovements() {
        return hasMovements;
    }
}
