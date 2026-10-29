package com.mybusinesssilva.inventory.rawmaterial.application;

import com.mybusinesssilva.inventory.rawmaterial.domain.model.RawMaterial;
import com.mybusinesssilva.inventory.rawmaterial.domain.model.RawMaterialMovementType;
import com.mybusinesssilva.inventory.rawmaterial.domain.port.in.RawMaterialUseCase;
import com.mybusinesssilva.inventory.rawmaterial.domain.port.out.RawMaterialRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del catálogo y kardex de materia prima. Las reglas de negocio
 * (nombre/sku único, unidad válida) lanzan {@link IllegalArgumentException} → 422.
 */
@Service
public class RawMaterialService implements RawMaterialUseCase {

    /** Unidades admitidas para un insumo. */
    private static final Set<String> VALID_UNITS = Set.of("kg", "litro", "pieza");

    private final RawMaterialRepository repository;

    public RawMaterialService(RawMaterialRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public long create(RawMaterial m) {
        validateUnit(m.unit());
        if (repository.existsActiveName(m.name(), null)) {
            throw new IllegalArgumentException("Ya existe un insumo activo con ese nombre.");
        }
        if (m.sku() != null && !m.sku().isBlank() && repository.existsSku(m.sku(), null)) {
            throw new IllegalArgumentException("Ya existe un insumo con ese SKU.");
        }
        return repository.insert(m);
    }

    @Override
    @Transactional
    public void update(long id, RawMaterial m) {
        validateUnit(m.unit());
        if (repository.find(id).isEmpty()) {
            throw new IllegalArgumentException("El insumo indicado no existe.");
        }
        if (repository.existsActiveName(m.name(), id)) {
            throw new IllegalArgumentException("Ya existe un insumo activo con ese nombre.");
        }
        if (m.sku() != null && !m.sku().isBlank() && repository.existsSku(m.sku(), id)) {
            throw new IllegalArgumentException("Ya existe un insumo con ese SKU.");
        }
        repository.update(id, m);
    }

    @Override
    @Transactional
    public void setActive(long id, boolean active) {
        if (repository.find(id).isEmpty()) {
            throw new IllegalArgumentException("El insumo indicado no existe.");
        }
        repository.setActive(id, active);
    }

    @Override
    public List<Map<String, Object>> list(Long branchId) {
        return repository.listWithStock(branchId);
    }

    @Override
    @Transactional
    public void registerEntry(long id, long branchId, BigDecimal qty, BigDecimal unitCost, String actor) {
        RawMaterial material = repository.find(id)
                .orElseThrow(() -> new IllegalArgumentException("El insumo indicado no existe."));
        if (!material.active()) {
            throw new IllegalArgumentException("No se puede reponer un insumo inactivo.");
        }
        if (qty == null || qty.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad de entrada debe ser positiva.");
        }
        BigDecimal frozenCost = unitCost != null ? unitCost : material.unitCost();
        BigDecimal balance = repository.adjustQuantity(id, branchId, qty);
        repository.recordMovement(id, branchId, RawMaterialMovementType.PURCHASE_RM,
                qty, balance, frozenCost, "Entrada de insumo", null, actor);
    }

    @Override
    public List<Map<String, Object>> movements(long id, LocalDate from, LocalDate to) {
        return repository.movements(id, from, to);
    }

    private void validateUnit(String unit) {
        if (unit == null || !VALID_UNITS.contains(unit)) {
            throw new IllegalArgumentException("La unidad del insumo debe ser kg, litro o pieza.");
        }
    }
}
