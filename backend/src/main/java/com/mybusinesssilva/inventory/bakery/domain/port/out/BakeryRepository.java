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

    // --- Merma ---

    /**
     * Upsert de la trazabilidad de merma por (saleId, productId), consolidando
     * cantidad y
     * recalculando importes con los precios capturados.
     */
    void upsertWasteSale(long saleId, long productId, BigDecimal quantity,
            BigDecimal entryPrice, BigDecimal wastePrice, BigDecimal difference, String cashier);

    List<Map<String, Object>> listWasteSales(LocalDate from, LocalDate to, Long productId);

    /**
     * Sucursal de una venta (para resolver el entry_price por defecto de la merma).
     */
    Optional<Long> saleBranch(long saleId);

    /**
     * entry_price por defecto: el del bakery_production_line más reciente del
     * producto/sucursal;
     * {@link Optional#empty()} si no hay producción previa.
     */
    Optional<BigDecimal> latestEntryPrice(long productId, long branchId);

    /** Precio del producto en el catálogo (respaldo de entry_price). */
    Optional<BigDecimal> productPrice(long productId);

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

    /** Existencia actual acumulada (inventory_stock) del producto/sucursal. */
    BigDecimal currentStock(long productId, long branchId);

    // --- Artículos de pan ---

    List<Map<String, Object>> searchBakeryItems(String query, Long branchId);

    List<Map<String, Object>> listBakeryItems(Long branchId);
}
