package com.mybusinesssilva.inventory.bakery.domain.port.out;

import com.mybusinesssilva.inventory.bakery.domain.model.BakeryEntryStatus;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProduction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Puerto de salida para la persistencia del subsistema de panadería (schema del
 * tenant,
 * con {@code JdbcClient}). La RLS aísla por tenant automáticamente.
 */
public interface BakeryRepository {

    // --- Producción ---

    /**
     * Inserta la cabecera de producción, asigna folio ('PROD-'||id) y persiste los
     * renglones.
     */
    long insertProduction(BakeryProduction production);

    List<Map<String, Object>> listRecentProductions(Long branchId, LocalDate from, LocalDate to, int limit);

    // --- Estatus de entrada ---

    List<BakeryEntryStatus> listEntryStatuses();

    /**
     * Alta idempotente por code (ON CONFLICT DO NOTHING); devuelve el id
     * resultante.
     */
    long upsertEntryStatus(String code, String label);

    // --- Merma (existencia + trazabilidad) ---

    /**
     * Suma {@code quantity} a la existencia de merma del producto/sucursal (upsert
     * atómico
     * sobre {@code bakery_waste_stock}). Crea el renglón si no existe.
     */
    void addWasteStock(long productId, long branchId, BigDecimal quantity);

    /**
     * Descuenta {@code quantity} de la existencia de merma del producto/sucursal de
     * forma
     * atómica, sin dejarla negativa.
     *
     * @return {@code true} si había existencia suficiente y se descontó;
     *         {@code false} si no
     *         (el llamador traduce el {@code false} a no-sobreventa de merma)
     */
    boolean deductWasteStock(long productId, long branchId, BigDecimal quantity);

    /** Existencia actual EN MERMA (0 si no hay registro). */
    BigDecimal wasteStock(long productId, long branchId);

    /**
     * Inserta una fila de trazabilidad en {@code bakery_waste_movement}.
     *
     * @param origin      uno de CIERRE/MANUAL/VENTA/REPARTO
     * @param quantity    cantidad del movimiento (positiva)
     * @param normalPrice precio normal de referencia del artículo
     * @param wastePrice  precio de merma aplicado/registrado
     * @param difference  pérdida: {@code (normalPrice - wastePrice) * quantity}
     * @param saleId      venta ligada (solo VENTA; null en otros)
     * @param shiftId     turno ligado (CIERRE/MANUAL; null si no aplica)
     */
    void insertWasteMovement(long productId, long branchId, String origin, BigDecimal quantity,
            BigDecimal normalPrice, BigDecimal wastePrice, BigDecimal difference,
            String cashier, Long saleId, Long shiftId);

    /** Historial de movimientos de merma por rango (todos los orígenes). */
    List<Map<String, Object>> listWasteMovements(LocalDate from, LocalDate to, Long productId);

    /**
     * Historial UNIFICADO de salidas de panadería para "Salidas recientes",
     * ordenado por
     * {@code created_at} DESC y limitado por {@code limit}. Une tres fuentes vía
     * {@code UNION ALL}
     * en una sola consulta (el orden y el límite globales se aplican sobre el
     * conjunto combinado):
     * <ul>
     * <li>Salida normal: {@code inventory_document} con {@code doc_type='OUT'} y su
     * {@code concept_name} real -&gt; {@code kind = "NORMAL"}.</li>
     * <li>Merma {@code origin='MANUAL'} de {@code bakery_waste_movement} -&gt;
     * concepto
     * "Paso a merma", {@code kind = "MANUAL"}.</li>
     * <li>Merma {@code origin='REPARTO'} de {@code bakery_waste_movement} -&gt;
     * concepto
     * "Reparto de merma", {@code kind = "REPARTO"}.</li>
     * </ul>
     * Cada fila expone: {@code id} (clave estable única entre fuentes, con prefijo
     * {@code DOC-}/{@code WASTE-}), {@code kind}, {@code concept} (ya resuelto),
     * {@code folio}
     * (null o {@code sale.folio} para merma), {@code branchName}, {@code totalQty},
     * {@code totalValue} (para merma la pérdida {@code difference}),
     * {@code cashier} y
     * {@code createdAt} (instante ISO). Filtra por sucursal cuando {@code branchId}
     * no es nulo.
     *
     * @param branchId sucursal a mostrar; {@code null} = todas
     * @param limit    máximo de filas (si &le; 0 se usa 50)
     */
    List<Map<String, Object>> listUnifiedExits(Long branchId, int limit);

    /**
     * Productos de pan con existencia normal &gt; 0 en una sucursal, con su
     * cantidad actual.
     * Usado por el cierre de caja para pasar el sobrante a merma (origen CIERRE).
     *
     * @return lista de mapas {@code {productId, quantity}} con existencia normal
     *         positiva
     */
    List<Map<String, Object>> productsWithNormalStock(long branchId);

    /**
     * Productos de pan ACTIVOS con existencia &gt; 0 del tipo pedido, para el
     * selector de Salidas.
     *
     * @param kind {@code "waste"} = existencia en merma; cualquier otro = stock
     *             normal
     * @return mapas {@code {productId, productName, unit, quantity}}
     */
    List<Map<String, Object>> productsWithStock(long branchId, String kind);

    /** Sucursal de una venta. */
    Optional<Long> saleBranch(long saleId);

    /**
     * Precio normal del producto en el catálogo (referencia de la diferencia de
     * merma).
     */
    Optional<BigDecimal> productPrice(long productId);

    /**
     * Precio de merma FIJO del producto en el catálogo (0 si no está configurado).
     */
    BigDecimal productWastePrice(long productId);

    // --- Existencia por día/rango ---

    /**
     * Σ cantidad producida en el rango natural [from, to] (America/Mexico_City).
     */
    BigDecimal producedBetween(long productId, long branchId, LocalDate from, LocalDate to);

    /**
     * Σ unidades vendidas (SALE) en el rango, filtrando por created_at crudo
     * (sargable).
     */
    BigDecimal soldBetween(long productId, long branchId, LocalDate from, LocalDate to);

    /** Serie diaria producido/vendido por fecha natural dentro del rango. */
    List<Map<String, Object>> dailySeries(long productId, long branchId, LocalDate from, LocalDate to);

    /**
     * Existencias del día para TODOS los productos de pan de una sucursal, en una
     * sola consulta.
     * Por producto devuelve un mapa con: {@code productId}, {@code productName},
     * {@code unit},
     * {@code price}, {@code produced} (producido ese día), {@code sold} (vendido
     * normal ese día),
     * {@code waste} (existencia actual de merma) y {@code currentStock} (existencia
     * normal actual).
     * Incluye los productos con producción, venta o existencia (normal o merma)
     * relevantes.
     */
    List<Map<String, Object>> dailyStockAll(long branchId, LocalDate date);

    /** Existencia actual acumulada (inventory_stock) del producto/sucursal. */
    BigDecimal currentStock(long productId, long branchId);

    // --- Artículos de pan ---

    List<Map<String, Object>> searchBakeryItems(String query, Long branchId);

    List<Map<String, Object>> listBakeryItems(Long branchId);
}
