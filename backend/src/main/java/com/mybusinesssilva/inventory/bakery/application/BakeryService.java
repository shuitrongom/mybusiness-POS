package com.mybusinesssilva.inventory.bakery.application;

import com.mybusinesssilva.inventory.bakery.domain.model.BakeryEntryStatus;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProduction;
import com.mybusinesssilva.inventory.bakery.domain.model.BakeryProductionLine;
import com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase;
import com.mybusinesssilva.inventory.bakery.domain.port.out.BakeryRepository;
import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.inventory.domain.port.in.InventoryPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    public void recordWasteSale(long saleId, String cashier, List<WasteLineCommand> wasteLines) {
        if (wasteLines == null || wasteLines.isEmpty()) {
            return;
        }
        for (WasteLineCommand line : wasteLines) {
            if (line.quantity() == null || line.quantity().signum() <= 0) {
                throw new IllegalArgumentException("La cantidad de merma debe ser positiva");
            }
            BigDecimal wastePrice = line.wastePrice() == null ? BigDecimal.ZERO : line.wastePrice();
            if (wastePrice.signum() < 0) {
                throw new IllegalArgumentException("El precio de merma no puede ser negativo");
            }
            BigDecimal entryPrice = resolveEntryPrice(line, saleId);
            if (wastePrice.compareTo(entryPrice) > 0) {
                throw new IllegalArgumentException(
                        "El precio de merma no puede superar el precio de entrada");
            }
            BigDecimal difference = entryPrice.subtract(wastePrice);
            repository.upsertWasteSale(saleId, line.productId(), line.quantity(),
                    entryPrice, wastePrice, difference, cashier);
        }
    }

    /**
     * Resuelve el entry_price del renglón de merma. Usa el enviado por el POS; si
     * llega null,
     * cae al entry_price del bakery_production_line más reciente del
     * producto/sucursal (la
     * sucursal se deriva de la venta), y si no hay producción previa, al
     * product.price (D-A).
     */
    private BigDecimal resolveEntryPrice(WasteLineCommand line, long saleId) {
        if (line.entryPrice() != null) {
            return line.entryPrice();
        }
        Long branchId = repository.saleBranch(saleId).orElse(null);
        if (branchId != null) {
            BigDecimal latest = repository.latestEntryPrice(line.productId(), branchId).orElse(null);
            if (latest != null) {
                return latest;
            }
        }
        return repository.productPrice(line.productId()).orElse(BigDecimal.ZERO);
    }

    @Override
    public List<Map<String, Object>> listWasteSales(LocalDate from, LocalDate to, Long productId) {
        return repository.listWasteSales(from, to, productId);
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
