package com.mybusinesssilva.inventory.bakery.domain.port.in;

import com.mybusinesssilva.inventory.bakery.domain.model.BakeryEntryStatus;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProduction;
import com.mybusinesssilva.inventory.bakery.domain.model.WasteOrigin;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Puerto de entrada (casos de uso) del subsistema de panadería: registro de
 * producción,
 * catálogo de estatus, trazabilidad de merma vendible y consultas de existencia
 * por día/rango.
 *
 * <p>
 * Vive en el dominio de {@code inventory.bakery} para que tanto ventas (merma)
 * como el
 * adaptador REST compartan sus tipos sin acoplarse a clases de aplicación.
 */
public interface BakeryUseCase {

    /**
     * Registra una entrada de producción (cabecera + renglones) y suma stock por
     * cada renglón
     * a través del motor de inventario (movimiento PURCHASE), en la misma
     * transacción.
     *
     * @return el id de la producción creada
     */
    long registerProduction(BakeryProduction production);

    /**
     * Entradas recientes (folio + artículo), opcionalmente filtradas por sucursal y
     * rango.
     */
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
     * Transfiere existencia hacia o desde la merma de un producto, en la misma
     * transacción
     * del caso de uso que la invoca, y registra la trazabilidad en
     * {@code bakery_waste_movement}.
     *
     * <p>
     * El efecto depende del {@link WasteOrigin}:
     * <ul>
     * <li>{@code CIERRE} y {@code MANUAL}: baja el stock normal (salida estricta:
     * no deja
     * negativo) y sube la existencia de merma. Es la transición normal →
     * merma.</li>
     * <li>{@code VENTA} y {@code REPARTO}: baja la existencia de merma (no deja
     * negativo),
     * sin tocar el stock normal. Es la salida de merma.</li>
     * </ul>
     *
     * El precio de merma registrado es el precio FIJO del artículo
     * ({@code product.waste_price});
     * el precio normal de referencia es {@code product.price}. La diferencia
     * (pérdida) se calcula
     * como {@code (precio_normal - precio_merma) * cantidad}.
     *
     * @param productId producto de pan
     * @param branchId  sucursal
     * @param quantity  cantidad a transferir (positiva)
     * @param origin    origen de la transición (determina la dirección)
     * @param cashier   quién origina el movimiento
     * @param saleId    venta ligada (solo {@code VENTA}; {@code null} en otros
     *                  orígenes)
     * @param shiftId   turno ligado (solo {@code CIERRE}/{@code MANUAL};
     *                  {@code null} si no aplica)
     * @throws com.mybusinesssilva.inventory.domain.model.InsufficientStockException si
     *                                                                               no
     *                                                                               hay
     *                                                                               existencia
     *                                                                               suficiente
     *                                                                               (normal
     *                                                                               para
     *                                                                               CIERRE/MANUAL,
     *                                                                               de
     *                                                                               merma
     *                                                                               para
     *                                                                               VENTA/REPARTO)
     */
    void transferToWaste(long productId, long branchId, BigDecimal quantity,
            WasteOrigin origin, String cashier, Long saleId, Long shiftId);

    /**
     * Vende como merma los renglones indicados de una venta ya persistida, en su
     * misma
     * transacción: por cada renglón aplica el precio de merma FIJO del artículo,
     * descuenta de
     * la existencia de merma (no-sobreventa de merma) y registra el movimiento con
     * origen
     * {@code VENTA}. Devuelve el conjunto de ids de producto vendidos como merma
     * para que el
     * llamador no descuente también del stock normal.
     *
     * @param saleId   venta a la que pertenecen los renglones de merma
     * @param branchId sucursal de la venta
     * @param cashier  cajero
     * @param lines    renglones de merma (producto + cantidad); el precio lo pone
     *                 el backend
     * @return ids de producto efectivamente vendidos como merma
     */
    java.util.Set<Long> sellWaste(long saleId, long branchId, String cashier, List<WasteLineCommand> lines);

    /**
     * Devuelve a la existencia de merma la cantidad de una venta de merma
     * cancelada, y registra
     * un movimiento de trazabilidad que revierte la venta. Lo invoca la cancelación
     * de venta para
     * los renglones marcados como merma (que descontaron de merma, no del stock
     * normal).
     *
     * @param productId producto de pan
     * @param branchId  sucursal
     * @param quantity  cantidad a devolver a merma (positiva)
     * @param actor     quién cancela
     * @param saleId    venta cancelada
     */
    void returnWasteOnVoid(long productId, long branchId, BigDecimal quantity, String actor, long saleId);

    /**
     * Existencia actual EN MERMA de un producto en una sucursal (0 si no hay
     * registro).
     */
    BigDecimal wasteStock(long productId, long branchId);

    /**
     * Productos con existencia normal &gt; 0 en una sucursal, como mapas
     * {@code {productId, quantity}}.
     * Lo usa el cierre de caja para pasar el sobrante a merma (candado, origen
     * CIERRE).
     */
    List<Map<String, Object>> productsWithNormalStock(long branchId);

    /**
     * Productos de pan con existencia &gt; 0 del tipo pedido, para el selector de
     * Salidas:
     * {@code "normal"} (merma manual / salida normal) o {@code "waste"} (reparto de
     * merma).
     */
    List<Map<String, Object>> productsWithStock(long branchId, String kind);

    /**
     * Historial de movimientos de merma por rango (todos los orígenes): fecha,
     * origen, artículo,
     * cantidad, precio normal, precio de merma, diferencia, cajero. Para el reporte
     * del admin.
     */
    List<Map<String, Object>> listWasteMovements(LocalDate from, LocalDate to, Long productId);

    /**
     * Existencia del día para un producto/sucursal: producido, vendido, restante
     * del día y,
     * solo si {@code date} es hoy, la existencia actual acumulada.
     */
    Map<String, Object> dailyStock(long productId, long branchId, LocalDate date);

    /**
     * Existencia por rango para un producto/sucursal. Con {@code groupBy = "day"}
     * devuelve la
     * serie diaria; en caso contrario, el agregado del rango.
     */
    Map<String, Object> rangeStock(long productId, long branchId, LocalDate from, LocalDate to, String groupBy);

    /**
     * Existencias del DÍA para TODOS los artículos de pan de una sucursal, en una
     * sola consulta.
     * Por producto: producido, vendido (normal), en merma (existencia actual de
     * merma), restante
     * del día y valor (restante × precio). Pensado para la vista de "Existencias
     * del día" (una
     * sola pantalla, lista completa). Si {@code date} es nulo, usa hoy.
     *
     * @return lista de mapas, uno por producto de pan con existencia o movimiento
     *         ese día
     */
    List<Map<String, Object>> dailyStockAll(long branchId, LocalDate date);

    /**
     * Buscador premium de artículos de pan. En giro panadería todos los productos
     * del
     * catálogo son pan, así que busca sobre el catálogo normal del tenant (acotando
     * por texto).
     */
    List<Map<String, Object>> searchBakeryItems(String query, Long branchId);

    /**
     * Tabla descriptiva de artículos de pan: artículo, cantidad, unidad, precio,
     * costo, importe.
     */
    List<Map<String, Object>> listBakeryItems(Long branchId);

    /**
     * Renglón de merma que viaja desde el POS. Solo producto y cantidad: el precio
     * de merma
     * es FIJO en el artículo ({@code product.waste_price}) y lo aplica el backend,
     * no el POS.
     *
     * @param productId producto de pan vendido como merma
     * @param quantity  cantidad vendida como merma (positiva)
     */
    record WasteLineCommand(long productId, BigDecimal quantity) {
    }
}
