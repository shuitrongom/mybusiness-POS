package com.mybusinesssilva.inventory.bakery.domain.port.in;

import com.mybusinesssilva.inventory.bakery.domain.model.BakeryBatch;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Puerto de entrada de la TANDA del panadero: registro atómico (MP baja, pan sube,
 * tanda guarda) y consultas del panadero / del panel admin.
 */
public interface BakeryBatchUseCase {

    /**
     * Registra una tanda en UNA transacción atómica: congela precios de ficha, crea
     * la cabecera, consume la MP (kardex propio), produce el pan reutilizando
     * {@code registerProduction} (kardex de pan), y cierra totales y líneas.
     *
     * @return el id de la tanda creada
     */
    long registerBatch(BakeryBatch batch);

    /** Tandas de HOY (día MX) del actor dado en una sucursal (panadero: sus tandas). */
    List<Map<String, Object>> myBatchesToday(long branchId, String actor);

    /** Tandas del panel admin, por sucursal y rango. */
    List<Map<String, Object>> listBatches(Long branchId, LocalDate from, LocalDate to, int limit);

    /** Detalle de una tanda (persistido + rendimiento/desvío vía YieldAnalyzerPort). */
    Map<String, Object> batchDetail(long batchId);

    /** Reporte de rendimiento por tanda/producto. */
    List<Map<String, Object>> yieldReport(Long branchId, LocalDate from, LocalDate to);

    /** Discrepancias: producción sin tanda (Categoría A) + ajuste manual de pan (Categoría B). */
    List<Map<String, Object>> anomalies(Long branchId, LocalDate from, LocalDate to);
}
