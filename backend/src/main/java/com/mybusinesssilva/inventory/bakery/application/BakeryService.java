package com.mybusinesssilva.inventory.bakery.application;

import com.mybusinesssilva.inventory.bakery.domain.model.BakeryEntryStatus;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProduction;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProductionLine;
import com.mybusinesssilva.inventory.bakery.domain.model.WasteOrigin;
import com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase;
import com.mybusinesssilva.inventory.bakery.domain.port.out.BakeryRepository;
import com.mybusinesssilva.inventory.domain.model.InsufficientStockException;
import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.inventory.domain.port.in.InventoryPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del subsistema de panadería. Reutiliza el motor de inventario a
 * través del
 * {@link InventoryPort} para que las entradas de producción sumen stock y
 * queden en el kardex,
 * sin duplicar el motor. Las escrituras son transaccionales.
 */
@Service
public class BakeryService implements BakeryUseCase {

    private final BakeryRepository repository;
    private final InventoryPort inventoryPort;

    public BakeryService(BakeryRepository repository, InventoryPort inventoryPort) {
        this.repository = repository;
        this.inventoryPort = inventoryPort;
    }

    @Override
    @Transactional
    public long registerProduction(BakeryProduction production) {
        if (production.lines().isEmpty()) {
            throw new IllegalArgumentException("La producción debe tener al menos un renglón");
        }
        long id = repository.insertProduction(production);
        // Cada renglón suma existencia por compra/producción (PURCHASE), creando el
        // kardex.
        for (BakeryProductionLine line : production.lines()) {
            inventoryPort.applyMovement(line.productId(), production.branchId(), MovementType.PURCHASE,
                    line.quantity(), "PROD:" + id, production.createdBy());
        }
        return id;
    }

    @Override
    public List<Map<String, Object>> listRecentProductions(Long branchId, LocalDate from, LocalDate to, int limit) {
        return repository.listRecentProductions(branchId, from, to, limit);
    }

    @Override
    public List<BakeryEntryStatus> listEntryStatuses() {
        return repository.listEntryStatuses();
    }

    @Override
    @Transactional
    public long createEntryStatus(String code, String label) {
        return repository.upsertEntryStatus(code, label);
    }

    @Override
    @Transactional
    public void transferToWaste(long productId, long branchId, BigDecimal quantity,
            WasteOrigin origin, String cashier, Long saleId, Long shiftId) {
        if (origin == null) {
            throw new IllegalArgumentException("El origen de la merma es obligatorio");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad de merma debe ser positiva");
        }
        BigDecimal normalPrice = repository.productPrice(productId).orElse(BigDecimal.ZERO);
        BigDecimal wastePrice = repository.productWastePrice(productId);
        BigDecimal difference = normalPrice.subtract(wastePrice).multiply(quantity);

        if (origin.isFromNormalStock()) {
            // Normal -> merma: baja el stock normal (estricto, no deja negativo) y sube la
            // merma.
            inventoryPort.applyOutboundStrict(productId, branchId, MovementType.WASTE_OUT,
                    quantity, "WASTE:" + origin.name() + ":" + (shiftId == null ? "-" : shiftId), cashier);
            repository.addWasteStock(productId, branchId, quantity);
        } else {
            // Salida de merma (VENTA/REPARTO): baja la existencia de merma, no toca el
            // normal.
            boolean ok = repository.deductWasteStock(productId, branchId, quantity);
            if (!ok) {
                BigDecimal available = repository.wasteStock(productId, branchId);
                throw new InsufficientStockException(productId, null, available, quantity);
            }
        }
        repository.insertWasteMovement(productId, branchId, origin.name(), quantity,
                normalPrice, wastePrice, difference, cashier, saleId, shiftId);
    }

    @Override
    @Transactional
    public Set<Long> sellWaste(long saleId, long branchId, String cashier, List<WasteLineCommand> lines) {
        Set<Long> wasteProductIds = new LinkedHashSet<>();
        if (lines == null || lines.isEmpty()) {
            return wasteProductIds;
        }
        for (WasteLineCommand line : lines) {
            // Venta de merma: precio FIJO del artículo, descuento de la existencia de merma
            // y
            // trazabilidad con origen VENTA, todo dentro de transferToWaste.
            transferToWaste(line.productId(), branchId, line.quantity(),
                    WasteOrigin.VENTA, cashier, saleId, null);
            wasteProductIds.add(line.productId());
        }
        return wasteProductIds;
    }

    @Override
    @Transactional
    public void returnWasteOnVoid(long productId, long branchId, BigDecimal quantity,
            String actor, long saleId) {
        if (quantity == null || quantity.signum() <= 0) {
            return;
        }
        // Devuelve la cantidad a la existencia de merma y registra un movimiento VENTA
        // que revierte
        // el de la venta (cantidad y diferencia negativas), para que saldos y reporte
        // cuadren.
        repository.addWasteStock(productId, branchId, quantity);
        BigDecimal normalPrice = repository.productPrice(productId).orElse(BigDecimal.ZERO);
        BigDecimal wastePrice = repository.productWastePrice(productId);
        BigDecimal difference = normalPrice.subtract(wastePrice).multiply(quantity).negate();
        repository.insertWasteMovement(productId, branchId, WasteOrigin.VENTA.name(),
                quantity.negate(), normalPrice, wastePrice, difference, actor, saleId, null);
    }

    @Override
    public BigDecimal wasteStock(long productId, long branchId) {
        return repository.wasteStock(productId, branchId);
    }

    @Override
    public List<Map<String, Object>> productsWithNormalStock(long branchId) {
        return repository.productsWithNormalStock(branchId);
    }

    @Override
    public List<Map<String, Object>> productsWithStock(long branchId, String kind) {
        return repository.productsWithStock(branchId, kind);
    }

    @Override
    public List<Map<String, Object>> dailyStockAll(long branchId, LocalDate date) {
        return repository.dailyStockAll(branchId, date != null ? date : LocalDate.now());
    }

    @Override
    public List<Map<String, Object>> listWasteMovements(LocalDate from, LocalDate to, Long productId) {
        return repository.listWasteMovements(from, to, productId);
    }

    @Override
    public Map<String, Object> dailyStock(long productId, long branchId, LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now();
        BigDecimal produced = repository.producedBetween(productId, branchId, day, day);
        BigDecimal sold = repository.soldBetween(productId, branchId, day, day);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", day);
        result.put("producido", produced);
        result.put("vendido", sold);
        result.put("restanteDelDia", produced.subtract(sold));
        if (!day.isBefore(LocalDate.now())) {
            result.put("existenciaActual", repository.currentStock(productId, branchId));
        }
        return result;
    }

    @Override
    public Map<String, Object> rangeStock(long productId, long branchId, LocalDate from, LocalDate to, String groupBy) {
        LocalDate f = from != null ? from : LocalDate.now().withDayOfMonth(1);
        LocalDate t = to != null ? to : LocalDate.now();
        if (f.isAfter(t)) {
            throw new IllegalArgumentException("El rango de fechas es inválido: 'from' no puede ser mayor que 'to'");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", f);
        result.put("to", t);
        BigDecimal produced = repository.producedBetween(productId, branchId, f, t);
        BigDecimal sold = repository.soldBetween(productId, branchId, f, t);
        result.put("producido", produced);
        result.put("vendido", sold);
        result.put("restanteDelRango", produced.subtract(sold));
        if ("day".equalsIgnoreCase(groupBy)) {
            result.put("serie", repository.dailySeries(productId, branchId, f, t));
        }
        if (!t.isBefore(LocalDate.now())) {
            result.put("existenciaActual", repository.currentStock(productId, branchId));
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> searchBakeryItems(String query, Long branchId) {
        return repository.searchBakeryItems(query, branchId);
    }

    @Override
    public List<Map<String, Object>> listBakeryItems(Long branchId) {
        return repository.listBakeryItems(branchId);
    }
}
