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
     * Cancela una venta completada y reingresa al inventario la mercancía de todos sus renglones.
     *
     * @param saleId venta a cancelar
     * @param actor  quién cancela (queda en el kardex)
     */
    @Transactional
    public void voidSale(long saleId, String actor) {
        String status = saleRepository.statusOf(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Venta inexistente: " + saleId));
        if ("VOIDED".equals(status)) {
            return; // Idempotente: ya está cancelada.
        }
        long branchId = saleRepository.branchOf(saleId).orElseThrow();

        // Reingresa el inventario de cada renglón (movimiento de tipo RETURN).
        for (SaleRepository.SaleLineRow line : saleRepository.linesOf(saleId)) {
            inventoryPort.applyMovement(line.productId(), branchId, MovementType.RETURN,
                    line.quantity(), "VOID:" + saleId, actor);
        }
        saleRepository.markVoided(saleId);
    }

    /**
     * Registra una devolución de productos: reingresa al inventario las cantidades devueltas.
     * A diferencia de la cancelación total, permite devolver renglones o cantidades parciales.
     *
     * @param branchId sucursal donde se recibe la devolución
     * @param returns  productos y cantidades devueltos
     * @param actor    quién procesa la devolución
     */
    @Transactional
    public void registerReturn(long branchId, java.util.List<ReturnItem> returns, String actor) {
        if (returns == null || returns.isEmpty()) {
            throw new IllegalArgumentException("La devolución debe tener al menos un producto");
        }
        for (ReturnItem item : returns) {
            inventoryPort.applyMovement(item.productId(), branchId, MovementType.RETURN,
                    item.quantity(), "RETURN", actor);
        }
    }

    /**
     * Registra una cotización o un apartado (no cobra ni descuenta inventario todavía).
     * Se persiste con estado QUOTE para poder consultarla o convertirla en venta después.
     */
    @Transactional
    public SaleResult registerQuote(Sale quote) {
        Sale saved = saleRepository.insert(quote);
        return new SaleResult(saved.getId(), saved.total(), java.math.BigDecimal.ZERO, false);
    }

    /**
     * Producto y cantidad de una devolución.
     *
     * @param productId producto devuelto
     * @param quantity  cantidad devuelta
     */
    public record ReturnItem(long productId, java.math.BigDecimal quantity) {
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
