package com.mybusinesssilva.inventory.bakery.domain.port.in;

import com.mybusinesssilva.inventory.bakery.domain.model.BakeryEntryStatus;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProduction;
import com.mybusinesssilva.inventory.bakery.domain.model.WasteSale;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Puerto de entrada (casos de uso) del subsistema de panadería: registro de producción,
 * catálogo de estatus, trazabilidad de merma vendible y consultas de existencia por día/rango.
 *
 * <p>Vive en el dominio de {@code inventory.bakery} para que tanto ventas (merma) como el
 * adaptador REST compartan sus tipos sin acoplarse a clases de aplicación.
 */
public interface BakeryUseCase {

    /**
     * Registra una entrada de producción (cabecera + renglones) y suma stock por cada renglón
     * a través del motor de inventario (movimiento PURCHASE), en la misma transacción.
     *
     * @return el id de la producción creada
     */
    long registerProduction(BakeryProduction production);

    /** Entradas recientes (folio + artículo), opcionalmente filtradas por sucursal y rango. */
    List<Map<String, Object>> listRecentProductions(Long branchId, LocalDate from, LocalDate to, int limit);

    /** Catálogo de estatus de entrada activos. */
    List<BakeryEntryStatus> listEntryStatuses();

    /**
     * Alta idempotente de un estatus de entrada (ON CONFLICT DO NOTHING por code).
     *
     * @return el id del estatus existente o recién creado
     */
    long createEntryStatus(String code, String label);

    /**
     * Registra la trazabilidad de merma de una venta ya validada. Para cada renglón,
     * {@code wastePrice = unitPrice} y {@code entryPrice = entryPrice} del renglón del POS.
     * Consolida por (saleId, productId) si el mismo producto llega en más de un renglón.
     */
    void recordWasteSale(long saleId, String cashier, List<WasteLineCommand> wasteLines);

    /** Historial de mermas con trazabilidad (folio, artículo, precios, diferencia, cajero, fecha). */
    List<Map<String, Object>> listWasteSales(LocalDate from, LocalDate to, Long productId);

    /**
     * Existencia del día para un producto/sucursal: producido, vendido, restante del día y,
     * solo si {@code date} es hoy, la existencia actual acumulada.
     */
    Map<String, Object> dailyStock(long productId, long branchId, LocalDate date);

    /**
     * Existencia por rango para un producto/sucursal. Con {@code groupBy = "day"} devuelve la
     * serie diaria; en caso contrario, el agregado del rango.
     */
    Map<String, Object> rangeStock(long productId, long branchId, LocalDate from, LocalDate to, String groupBy);

    /** Buscador premium de artículos de pan ({@code is_bakery = TRUE}). */
    List<Map<String, Object>> searchBakeryItems(String query, Long branchId);

    /** Tabla descriptiva de artículos de pan: artículo, cantidad, unidad, precio, costo, importe. */
    List<Map<String, Object>> listBakeryItems(Long branchId);

    /**
     * Renglón de merma que viaja desde el POS a la trazabilidad.
     *
     * @param productId  producto de pan
     * @param quantity   cantidad vendida como merma
     * @param entryPrice precio de entrada (puede ser null: se resuelve en el backend)
     * @param wastePrice precio de merma (= unitPrice del renglón del POS)
     */
    record WasteLineCommand(long productId, BigDecimal quantity,
                            BigDecimal entryPrice, BigDecimal wastePrice) {
    }
}
