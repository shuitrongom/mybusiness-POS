package com.mybusinesssilva.inventory.bakery.domain.port.out;

import com.mybusinesssilva.inventory.bakery.domain.model.BakeryEntryStatus;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProduction;
import com.mybusinesssilva.inventory.bakery.domain.model.WasteSummary;
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
     * Inserta una fila de trazabilidad en {@code bakery_waste_movement}, le asigna
     * folio de serie {@code 'M-'} ({@code 'M-'||id}) en la MISMA transacción (mismo
     * patrón que {@code PROD-}/{@code S-}) y devuelve el id. El folio es metadato: no
     * afecta {@code quantity}/{@code difference} ni los saldos.
     *
     * @param origin      uno de CIERRE/MANUAL/VENTA/REPARTO/ENVEJECIMIENTO
     * @param quantity    cantidad del movimiento (positiva)
     * @param normalPrice precio normal de referencia del artículo
     * @param wastePrice  precio de merma aplicado/registrado
     * @param difference  pérdida: {@code (normalPrice - wastePrice) * quantity}
     * @param saleId      venta ligada (solo VENTA; null en otros)
     * @param shiftId     turno ligado (CIERRE/MANUAL; null si no aplica)
     * @return id (IDENTITY) del movimiento recién insertado
     */
    long insertWasteMovement(long productId, long branchId, String origin, BigDecimal quantity,
            BigDecimal normalPrice, BigDecimal wastePrice, BigDecimal difference,
            String cashier, Long saleId, Long shiftId);

    /** Historial de movimientos de merma por rango (todos los orígenes). */
    List<Map<String, Object>> listWasteMovements(LocalDate from, LocalDate to, Long productId);

    /**
     * Agregado de SOLO LECTURA de la merma vendible del tenant (todas las
     * sucursales): total de piezas y número de artículos ACTIVOS con existencia de
     * merma &gt; 0. No escribe nada (el corte conserva la merma intacta).
     */
    WasteSummary wasteSummary();

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
     * <li>Merma {@code origin='CIERRE'} de {@code bakery_waste_movement} -&gt;
     * concepto
     * "Sobrante a merma (fin del día)", {@code kind = "CIERRE"}.</li>
     * <li>Merma {@code origin='ENVEJECIMIENTO'} de {@code bakery_waste_movement}
     * -&gt; concepto
     * "Pan del día anterior a merma", {@code kind = "ENVEJECIMIENTO"}.</li>
     * </ul>
     * (La merma {@code origin='VENTA'} queda EXCLUIDA para no duplicar la venta del
     * POS.) Cada fila expone: {@code id} (clave estable única entre fuentes, con
     * prefijo {@code DOC-}/{@code WASTE-}), {@code kind}, {@code concept} (ya
     * resuelto), {@code folio} ({@code inventory_document.folio} para salida normal,
     * {@code bakery_waste_movement.folio} = {@code 'M-'||id} para merma),
     * {@code branchName}, {@code totalQty},
     * {@code totalValue} (para merma la pérdida {@code difference}),
     * {@code cashier} y
     * {@code createdAt} (instante ISO). Filtra por sucursal cuando {@code branchId}
     * no es nulo.
     *
     * <p>
     * Acota además al rango del día natural MX {@code [from, to]}
     * (America/Mexico_City): filtra {@code created_at} crudo sobre ambas ramas del
     * {@code UNION ALL} con el patrón sargable {@code >= from AT TIME ZONE TZ} y
     * {@code < (to + 1) AT TIME ZONE TZ}. Si {@code from}/{@code to} son nulos no
     * acota por día (compat histórica).
     *
     * @param branchId sucursal a mostrar; {@code null} = todas
     * @param from     inicio del día natural MX a mostrar; {@code null} = sin cota inferior
     * @param to       fin del día natural MX a mostrar; {@code null} = sin cota superior
     * @param limit    máximo de filas (si &le; 0 se usa 50)
     */
    List<Map<String, Object>> listUnifiedExits(Long branchId, LocalDate from, LocalDate to, int limit);

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
     * Guardia IDEMPOTENTE del envejecimiento: intenta registrar una corrida para la
     * sucursal en el día natural de hoy (America/Mexico_City) con
     * {@code ON CONFLICT (branch_id, business_date) DO NOTHING}.
     *
     * @return {@code 1} si esta llamada ganó la corrida del día (hay que envejecer);
     *         {@code 0} si ya existía (otro cron/catch-up ya la ejecutó hoy)
     */
    int markAgingRun(long branchId);

    /**
     * Candidatos a envejecimiento en una sucursal: por cada producto de pan ACTIVO
     * con existencia normal &gt; 0, la cantidad del día ANTERIOR a envejecer =
     * {@code GREATEST(existencia_actual - producido_hoy, 0)} (lo producido hoy NO
     * envejece). Solo devuelve filas con cantidad &gt; 0.
     *
     * @return mapas {@code {productId, quantity}} (cantidad = piezas a envejecer)
     */
    List<Map<String, Object>> agingCandidates(long branchId);

    /**
     * Existencia NORMAL viva de pan en TODAS las sucursales del tenant (corte
     * global prueba→real). Por cada producto de pan con existencia normal &gt; 0.
     *
     * @return mapas {@code {productId, branchId, quantity}} con existencia normal
     *         positiva, ordenados por sucursal y producto
     */
    List<Map<String, Object>> bakeryNormalStock();

    /**
     * Ids de las sucursales ACTIVAS del tenant en curso. La RLS aísla por tenant,
     * así que la consulta no necesita filtro de schema.
     *
     * @return ids de sucursal activos, ordenados ascendentemente
     */
    List<Long> activeBranchIds();

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

    /**
     * Nombre del producto en el catálogo ({@code null} si no existe). Usado solo
     * para mensajes de negocio legibles (p. ej. "Captura primero el precio de merma
     * del artículo: &lt;nombre&gt;").
     */
    String productName(long productId);

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
