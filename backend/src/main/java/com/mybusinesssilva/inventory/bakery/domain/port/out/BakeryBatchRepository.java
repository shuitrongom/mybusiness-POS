package com.mybusinesssilva.inventory.bakery.domain.port.out;

import com.mybusinesssilva.inventory.bakery.domain.model.BatchInput;
import com.mybusinesssilva.inventory.bakery.domain.model.BatchOutput;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Puerto de salida de la TANDA (persistencia de cabecera, líneas y lecturas del
 * panel). Implementado con JDBC sobre el schema del tenant en curso.
 */
public interface BakeryBatchRepository {

    /**
     * Inserta la cabecera mínima de la tanda (totales en 0, production_id NULL) y
     * fija el folio 'T-'||id. Devuelve el id generado para el {@code reference} del
     * kardex de MP.
     */
    long insertHeader(long branchId, LocalDate batchDate, String createdBy, String createdRole, String notes);

    /** Cierra la tanda: fija production_id y los totales (la invariante del CHECK se cumple por cálculo). */
    void closeBatch(long batchId, long productionId, BigDecimal inputCost,
                    BigDecimal outputValue, BigDecimal valueOverInputs);

    /** Inserta una línea de insumo consumido (congelada). */
    void insertInput(long batchId, BatchInput input);

    /** Inserta una línea de pan producido (espejo para rendimiento). */
    void insertOutput(long batchId, BatchOutput output);

    /** Tandas de HOY (día MX) del actor dado en una sucursal. */
    List<Map<String, Object>> myBatchesToday(long branchId, String actor, LocalDate todayMx);

    /** Tandas del panel admin, por sucursal y rango. */
    List<Map<String, Object>> listBatches(Long branchId, LocalDate from, LocalDate to, int limit);

    /** Detalle persistido de una tanda (cabecera + insumos + panes); null si no existe. */
    Map<String, Object> batchDetail(long batchId);

    /** Reporte de rendimiento por (producto, insumo) desde el espejo de la tanda. */
    List<Map<String, Object>> yieldReport(Long branchId, LocalDate from, LocalDate to);

    /** Categoría A: producciones de pan sin tanda ligada (respaldo legítimo). */
    List<Map<String, Object>> backupProductions(Long branchId, LocalDate from, LocalDate to);

    /** Categoría B: ajustes/entradas manuales de pan (anomalía real). */
    List<Map<String, Object>> manualAdjustments(Long branchId, LocalDate from, LocalDate to);

    /**
     * Serie de {@code yield_total} de las últimas N tandas del mismo tipo que la tanda
     * dada (mismas product_id de salida), excluyendo la tanda misma, más reciente
     * primero. Lo usa el analizador de rendimiento.
     */
    List<BigDecimal> recentYieldsForSameType(long batchId, int n);

    /** {@code yield_total} de una tanda (Σ output / Σ input); null si no hay insumo. */
    BigDecimal yieldTotal(long batchId);
}
