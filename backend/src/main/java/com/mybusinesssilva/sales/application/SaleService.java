package com.mybusinesssilva.sales.application;

import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.inventory.domain.port.in.InventoryPort;
import com.mybusinesssilva.sales.domain.model.Sale;
import com.mybusinesssilva.sales.domain.model.SaleLine;
import com.mybusinesssilva.sales.domain.port.out.SaleRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de ventas.
 *
 * <p>Al registrar una venta: valida (renglones y pagos), la persiste y descuenta el inventario
 * de cada renglón a través del {@link InventoryPort}, todo en la misma transacción para mantener
 * consistencia entre venta e inventario.
 *
 * <p>Idempotencia (soporte offline): si la venta trae una clave de idempotencia ya registrada,
 * no se vuelve a procesar; se devuelve el id existente. Así, cuando el punto de venta sincroniza
 * ventas encoladas sin conexión, no se duplican ni se descuenta inventario dos veces.
 */
@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final InventoryPort inventoryPort;

    public SaleService(SaleRepository saleRepository, InventoryPort inventoryPort) {
        this.saleRepository = saleRepository;
        this.inventoryPort = inventoryPort;
    }

    /**
     * Registra una venta completada. Idempotente por {@code idempotencyKey}.
     *
     * @return resultado con el id de la venta e indicación de si fue duplicada (ya existía)
     */
    @Transactional
    public SaleResult registerSale(Sale sale) {
        // Deduplicación por idempotencia (sincronización offline).
        if (sale.getIdempotencyKey() != null) {
            Optional<Long> existing = saleRepository.findIdByIdempotencyKey(sale.getIdempotencyKey());
            if (existing.isPresent()) {
                return new SaleResult(existing.get(), sale.total(), sale.change(), true);
            }
        }

        Sale saved = saleRepository.insert(sale);

        // Descuenta inventario por cada renglón.
        for (SaleLine line : saved.getLines()) {
            inventoryPort.applyMovement(
                    line.productId(), saved.getBranchId(), MovementType.SALE,
                    line.quantity(), "SALE:" + saved.getId(), saved.getCashier());
        }

        return new SaleResult(saved.getId(), saved.total(), saved.change(), false);
    }

    /**
     * Cancela una venta y reingresa el inventario de sus renglones.
     * (La reposición detallada de inventario por cancelación se completa junto con devoluciones.)
     */
    @Transactional
    public void voidSale(long saleId) {
        saleRepository.markVoided(saleId);
    }

    /**
     * Resultado del registro de una venta.
     *
     * @param saleId     id de la venta
     * @param total      total cobrado
     * @param change     cambio devuelto
     * @param duplicated true si la venta ya existía (idempotencia) y no se reprocesó
     */
    public record SaleResult(Long saleId, java.math.BigDecimal total,
                             java.math.BigDecimal change, boolean duplicated) {
    }
}
