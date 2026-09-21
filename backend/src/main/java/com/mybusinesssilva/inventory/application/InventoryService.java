package com.mybusinesssilva.inventory.application;

import com.mybusinesssilva.inventory.domain.model.InventoryMovement;
import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.inventory.domain.model.StockLevel;
import com.mybusinesssilva.inventory.domain.port.in.InventoryPort;
import com.mybusinesssilva.inventory.domain.port.out.InventoryRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de inventario. Implementa {@link InventoryPort} (consumido por ventas/compras)
 * y ofrece operaciones de ajuste, traspaso, kardex y alertas de stock mínimo.
 *
 * <p>Cada movimiento actualiza la existencia y registra el renglón del kardex en la misma
 * transacción, garantizando consistencia entre existencia e historial.
 */
@Service
public class InventoryService implements InventoryPort {

    private final InventoryRepository repository;

    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public BigDecimal applyMovement(long productId, long branchId, MovementType type,
                                    BigDecimal quantity, String reference, String actor) {
        BigDecimal signed = signedQuantity(type, quantity.abs());
        BigDecimal balance = repository.adjustQuantity(productId, branchId, signed);
        repository.recordMovement(new InventoryMovement(
                null, productId, branchId, type, signed, balance, null, reference, actor, null));
        return balance;
    }

    /**
     * Ajuste manual de inventario con motivo (queda registrado para auditoría/kardex).
     *
     * @param delta cantidad a sumar o restar (con signo)
     */
    @Transactional
    public BigDecimal adjust(long productId, long branchId, BigDecimal delta,
                             String reason, String actor) {
        BigDecimal balance = repository.adjustQuantity(productId, branchId, delta);
        repository.recordMovement(new InventoryMovement(
                null, productId, branchId, MovementType.ADJUSTMENT, delta, balance,
                reason, null, actor, null));
        return balance;
    }

    /**
     * Traspaso de un producto entre sucursales: salida en la origen y entrada en la destino,
     * de forma atómica.
     */
    @Transactional
    public void transfer(long productId, long fromBranchId, long toBranchId,
                         BigDecimal quantity, String actor) {
        if (fromBranchId == toBranchId) {
            throw new IllegalArgumentException("Las sucursales de origen y destino deben ser distintas");
        }
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("La cantidad a traspasar debe ser positiva");
        }
        String reference = "TRANSFER:" + fromBranchId + "->" + toBranchId;
        applyMovement(productId, fromBranchId, MovementType.TRANSFER_OUT, quantity, reference, actor);
        applyMovement(productId, toBranchId, MovementType.TRANSFER_IN, quantity, reference, actor);
    }

    public void setMinimum(long productId, long branchId, BigDecimal minQuantity) {
        repository.setMinQuantity(productId, branchId, minQuantity);
    }

    public StockLevel stock(long productId, long branchId) {
        return repository.findStock(productId, branchId).orElse(
                new StockLevel(productId, branchId, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    public List<InventoryMovement> kardex(long productId) {
        return repository.kardex(productId);
    }

    public List<StockLevel> lowStockAlerts() {
        return repository.findBelowMinimum();
    }

    /**
     * Determina el signo de la cantidad según el tipo de movimiento: las entradas suman,
     * las salidas restan.
     */
    private BigDecimal signedQuantity(MovementType type, BigDecimal absQuantity) {
        return switch (type) {
            case PURCHASE, TRANSFER_IN, RETURN -> absQuantity;
            case SALE, TRANSFER_OUT -> absQuantity.negate();
            case ADJUSTMENT -> absQuantity; // el ajuste usa el método adjust() con signo explícito
        };
    }
}
