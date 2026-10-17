package com.mybusinesssilva.sales.application;

import com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase;
import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.inventory.domain.port.in.InventoryPort;
import com.mybusinesssilva.sales.domain.model.Sale;
import com.mybusinesssilva.sales.domain.model.SaleLine;
import com.mybusinesssilva.sales.domain.port.out.CustomerCreditPort;
import com.mybusinesssilva.sales.domain.port.out.ProductCatalogQueryPort;
import com.mybusinesssilva.sales.domain.port.out.SaleRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de ventas.
 *
 * <p>
 * Al registrar una venta: valida (renglones y pagos), la persiste y descuenta
 * el inventario
 * de cada renglón a través del {@link InventoryPort}, todo en la misma
 * transacción para mantener
 * consistencia entre venta e inventario.
 *
 * <p>
 * Idempotencia (soporte offline): si la venta trae una clave de idempotencia ya
 * registrada,
 * no se vuelve a procesar; se devuelve el id existente. Así, cuando el punto de
 * venta sincroniza
 * ventas encoladas sin conexión, no se duplican ni se descuenta inventario dos
 * veces.
 */
@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final InventoryPort inventoryPort;
    private final CustomerCreditPort customerCreditPort;
    private final ProductCatalogQueryPort productCatalogQueryPort;
    private final BakeryUseCase bakeryUseCase;

    public SaleService(SaleRepository saleRepository, InventoryPort inventoryPort,
            CustomerCreditPort customerCreditPort,
            ProductCatalogQueryPort productCatalogQueryPort,
            BakeryUseCase bakeryUseCase) {
        this.saleRepository = saleRepository;
        this.inventoryPort = inventoryPort;
        this.customerCreditPort = customerCreditPort;
        this.productCatalogQueryPort = productCatalogQueryPort;
        this.bakeryUseCase = bakeryUseCase;
    }

    /**
     * Registra una venta completada. Idempotente por {@code idempotencyKey}.
     *
     * @return resultado con el id de la venta e indicación de si fue duplicada (ya
     *         existía)
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

        // Si la venta viene ligada a un turno, este debe estar ABIERTO: una vez cerrada
        // la caja
        // (corte Z) no se puede seguir vendiendo en ese turno.
        if (sale.getShiftId() != null) {
            String shiftStatus = saleRepository.shiftStatus(sale.getShiftId()).orElse(null);
            if (shiftStatus == null) {
                throw new IllegalStateException("El turno de caja indicado no existe.");
            }
            if (!"OPEN".equals(shiftStatus)) {
                throw new IllegalStateException(
                        "Tu caja ya está cerrada. No puedes registrar más ventas en este turno; "
                                + "abre una nueva caja para continuar.");
            }
        }

        Sale saved = saleRepository.insert(sale);

        // Resuelve qué productos de la venta tienen control duro de existencia una sola
        // vez,
        // y aplica los movimientos de salida (estricto para enforce_stock, normal para
        // el resto).
        Set<Long> enforceStockIds = resolveEnforceStockIds(saved);
        applySaleMovements(saved, enforceStockIds);

        registerCreditIfNeeded(saved);

        return new SaleResult(saved.getId(), saved.total(), saved.change(), false);
    }

    /**
     * Registra una venta que incluye renglones de merma de panadería, de forma
     * atómica: en la
     * MISMA transacción persiste la venta, descuenta inventario (estricto donde
     * aplica para los renglones normales), descuenta la existencia de merma para
     * los renglones
     * marcados como merma y registra su trazabilidad. Si cualquiera de esos pasos
     * falla (p. ej.
     * la no-sobreventa normal o la no-sobreventa de merma), toda la transacción
     * revierte: ni la
     * venta, ni los movimientos, ni la merma quedan aplicados. Sin renglones de
     * merma se comporta
     * como {@link #registerSale(Sale)}. La marca de merma viaja en cada
     * {@link SaleLine#waste()}.
     */
    @Transactional
    public SaleResult registerSaleWithWaste(Sale sale) {
        boolean hasWaste = sale.getLines().stream().anyMatch(SaleLine::waste);
        if (!hasWaste) {
            return registerSale(sale);
        }

        if (sale.getIdempotencyKey() != null) {
            Optional<Long> existing = saleRepository.findIdByIdempotencyKey(sale.getIdempotencyKey());
            if (existing.isPresent()) {
                return new SaleResult(existing.get(), sale.total(), sale.change(), true);
            }
        }

        if (sale.getShiftId() != null) {
            String shiftStatus = saleRepository.shiftStatus(sale.getShiftId()).orElse(null);
            if (shiftStatus == null) {
                throw new IllegalStateException("El turno de caja indicado no existe.");
            }
            if (!"OPEN".equals(shiftStatus)) {
                throw new IllegalStateException(
                        "Tu caja ya está cerrada. No puedes registrar más ventas en este turno; "
                                + "abre una nueva caja para continuar.");
            }
        }

        Sale saved = saleRepository.insert(sale);

        // Renglones de merma: descuentan de la existencia de MERMA (no del stock
        // normal), con el
        // precio de merma fijo del artículo, y registran la trazabilidad con origen
        // VENTA.
        List<BakeryUseCase.WasteLineCommand> wasteCommands = saved.getLines().stream()
                .filter(SaleLine::waste)
                .map(l -> new BakeryUseCase.WasteLineCommand(l.productId(), l.quantity()))
                .toList();
        bakeryUseCase.sellWaste(saved.getId(), saved.getBranchId(), saved.getCashier(), wasteCommands);

        // Renglones normales: descuentan del stock normal (estricto donde aplica). Los
        // renglones
        // de merma se saltan aquí porque ya movieron la existencia de merma.
        Set<Long> enforceStockIds = resolveEnforceStockIds(saved);
        applySaleMovements(saved, enforceStockIds);

        registerCreditIfNeeded(saved);

        return new SaleResult(saved.getId(), saved.total(), saved.change(), false);
    }

    /**
     * Resuelve, para los renglones con producto que NO son merma, cuáles están
     * sujetos a
     * no-sobreventa. Los renglones de merma se excluyen: no descuentan del stock
     * normal.
     */
    private Set<Long> resolveEnforceStockIds(Sale saved) {
        Set<Long> productIds = saved.getLines().stream()
                .filter(line -> !line.waste())
                .map(SaleLine::productId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        return productCatalogQueryPort.enforceStockProductIds(productIds);
    }

    /**
     * Descuenta inventario por cada renglón NORMAL con producto. Si el producto
     * está en
     * {@code enforceStockIds} usa la salida estricta (rechaza y revierte si no hay
     * existencia);
     * en caso contrario usa el movimiento de venta normal. Los artículos libres y
     * los renglones
     * de merma no mueven el stock normal (la merma se descontó aparte, de
     * bakery_waste_stock).
     */
    private void applySaleMovements(Sale saved, Set<Long> enforceStockIds) {
        for (SaleLine line : saved.getLines()) {
            if (line.productId() == null || line.waste()) {
                continue;
            }
            if (enforceStockIds.contains(line.productId())) {
                inventoryPort.applyOutboundStrict(
                        line.productId(), saved.getBranchId(), MovementType.SALE,
                        line.quantity(), "SALE:" + saved.getId(), saved.getCashier());
            } else {
                inventoryPort.applyMovement(
                        line.productId(), saved.getBranchId(), MovementType.SALE,
                        line.quantity(), "SALE:" + saved.getId(), saved.getCashier());
            }
        }
    }

    /**
     * Venta a crédito: el saldo no pagado genera una cuenta por cobrar del cliente.
     */
    private void registerCreditIfNeeded(Sale saved) {
        if (saved.isOnCredit()) {
            BigDecimal balanceDue = saved.balanceDue();
            if (balanceDue.signum() > 0) {
                customerCreditPort.registerReceivable(
                        saved.getCustomerId(), saved.getId(), balanceDue,
                        saved.getBranchId(), null);
            }
        }
    }

    /**
     * Cancela una venta completada y reingresa al inventario la mercancía de todos
     * sus renglones.
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

        // Reingresa cada renglón a la existencia correcta. Los artículos libres (sin
        // producto)
        // no movieron stock, así que se omiten. Un renglón de merma se devuelve a la
        // EXISTENCIA
        // DE MERMA (no al stock normal), porque la venta descontó de ahí.
        for (SaleRepository.SaleLineRow line : saleRepository.linesOf(saleId)) {
            if (line.productId() == null) {
                continue;
            }
            if (line.waste()) {
                bakeryUseCase.returnWasteOnVoid(line.productId(), branchId, line.quantity(),
                        actor, saleId);
            } else {
                inventoryPort.applyMovement(line.productId(), branchId, MovementType.RETURN,
                        line.quantity(), "VOID:" + saleId, actor);
            }
        }
        saleRepository.markVoided(saleId);
    }

    /**
     * Registra una devolución de productos: reingresa al inventario las cantidades
     * devueltas.
     * A diferencia de la cancelación total, permite devolver renglones o cantidades
     * parciales.
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
     * Registra una cotización o un apartado (no cobra ni descuenta inventario
     * todavía).
     * Se persiste con estado QUOTE para poder consultarla o convertirla en venta
     * después.
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
     * @param duplicated true si la venta ya existía (idempotencia) y no se
     *                   reprocesó
     */
    public record SaleResult(Long saleId, java.math.BigDecimal total,
            java.math.BigDecimal change, boolean duplicated) {
    }
}
