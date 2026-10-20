package com.mybusinesssilva.sales.application;

import com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase;
import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.inventory.domain.port.in.InventoryPort;
import com.mybusinesssilva.sales.domain.model.Payment;
import com.mybusinesssilva.sales.domain.model.Sale;
import com.mybusinesssilva.sales.domain.model.SaleLine;
import com.mybusinesssilva.sales.domain.port.out.CustomerCreditPort;
import com.mybusinesssilva.sales.domain.port.out.PricingQueryPort;
import com.mybusinesssilva.sales.domain.port.out.ProductCatalogQueryPort;
import com.mybusinesssilva.sales.domain.port.out.SaleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de ventas.
 *
 * <p>
 * Al registrar una venta: resuelve el precio AUTORITATIVO server-side (el
 * backend
 * es la autoridad del precio, no el POS — ALTA-1), valida (renglones y pagos),
 * la
 * persiste y descuenta el inventario de cada renglón a través del
 * {@link InventoryPort}, todo en la misma transacción para mantener
 * consistencia
 * entre venta e inventario.
 *
 * <p>
 * Idempotencia (soporte offline + anti doble-cobro): el chequeo por
 * {@code idempotencyKey} es lo PRIMERO dentro de la transacción (short-circuit
 * antes de recomputar precio y de las guardas). Un reenvío del mismo payload
 * devuelve el id ya persistido sin reprocesar; el doble submit concurrente lo
 * atrapa la {@code UNIQUE(idempotency_key)} + el {@code catch} de
 * {@link DataIntegrityViolationException}, que relee y devuelve la venta
 * existente.
 */
@Service
public class SaleService {

    private static final Logger log = LoggerFactory.getLogger(SaleService.class);

    private final SaleRepository saleRepository;
    private final InventoryPort inventoryPort;
    private final CustomerCreditPort customerCreditPort;
    private final ProductCatalogQueryPort productCatalogQueryPort;
    private final PricingQueryPort pricingQueryPort;
    private final BakeryUseCase bakeryUseCase;

    public SaleService(SaleRepository saleRepository, InventoryPort inventoryPort,
            CustomerCreditPort customerCreditPort,
            ProductCatalogQueryPort productCatalogQueryPort,
            PricingQueryPort pricingQueryPort,
            BakeryUseCase bakeryUseCase) {
        this.saleRepository = saleRepository;
        this.inventoryPort = inventoryPort;
        this.customerCreditPort = customerCreditPort;
        this.productCatalogQueryPort = productCatalogQueryPort;
        this.pricingQueryPort = pricingQueryPort;
        this.bakeryUseCase = bakeryUseCase;
    }

    /**
     * Registra una venta completada (contado o crédito) recomputando el precio
     * autoritativo. Idempotente por {@code idempotencyKey}.
     */
    @Transactional
    public SaleResult registerSale(SaleCommand cmd) {
        return register(cmd, false);
    }

    /**
     * Registra una venta que incluye renglones de merma de panadería, de forma
     * atómica: en la MISMA transacción persiste la venta, descuenta inventario
     * (estricto donde aplica para los renglones normales), descuenta la existencia
     * de merma para los renglones marcados como merma y registra su trazabilidad.
     * Si cualquiera de esos pasos falla toda la transacción revierte. Comparte el
     * MISMO esqueleto que {@link #registerSale(SaleCommand)} (idempotencia primero,
     * reprice/guardas solo en ventas nuevas, insert con red de seguridad ante la
     * colisión concurrente); el sellado de merma se activa con {@code withWaste}.
     */
    @Transactional
    public SaleResult registerSaleWithWaste(SaleCommand cmd) {
        return register(cmd, true);
    }

    /**
     * Esqueleto común de registro de venta (normal y merma).
     *
     * <ol>
     * <li>IDEMPOTENCIA PRIMERO: short-circuit que devuelve la venta existente sin
     * recomputar precio ni re-evaluar guardas (un reenvío offline debe pasar aunque
     * el producto se haya bloqueado/quedado en precio 0 después).</li>
     * <li>Validación de turno (existe + OPEN).</li>
     * <li>Reprice + guardas de negocio (solo para ventas NUEVAS) y ensamblado del
     * agregado {@link Sale}.</li>
     * <li>Insert con red de seguridad: la {@code UNIQUE(idempotency_key)} atrapa el
     * doble submit concurrente; se relee y se devuelve la venta existente como
     * dedup
     * limpio ({@code duplicated=true}), nunca un 500.</li>
     * </ol>
     */
    private SaleResult register(SaleCommand cmd, boolean withWaste) {
        // (1) IDEMPOTENCIA PRIMERO — antes de cualquier trabajo de negocio.
        if (cmd.idempotencyKey() != null) {
            Optional<Long> existing = saleRepository.findIdByIdempotencyKey(cmd.idempotencyKey());
            if (existing.isPresent()) {
                return saleResultFromExisting(existing.get());
            }
        }

        // (2) Si la venta viene ligada a un turno, este debe EXISTIR y estar ABIERTO.
        if (cmd.shiftId() != null) {
            String shiftStatus = saleRepository.shiftStatus(cmd.shiftId()).orElse(null);
            if (shiftStatus == null) {
                throw new IllegalStateException("El turno de caja indicado no existe.");
            }
            if (!"OPEN".equals(shiftStatus)) {
                throw new IllegalStateException(
                        "Tu caja ya está cerrada. No puedes registrar más ventas en este turno; "
                                + "abre una nueva caja para continuar.");
            }
        }

        // (3) Reprice + guardas + ensamblado del agregado (solo ventas NUEVAS).
        List<SaleLine> lines = resolveAndRepriceLines(cmd);
        BigDecimal globalDiscount = normalizeGlobalDiscount(cmd.globalDiscount(), lines);
        Sale sale = Sale.complete(cmd.branchId(), cmd.cashRegisterId(), cmd.shiftId(), cmd.cashier(),
                cmd.salesperson(), cmd.customerId(), cmd.priceListId(), globalDiscount, cmd.note(),
                cmd.onCredit(), lines, cmd.payments(), cmd.idempotencyKey());

        // (4) Insert con red de seguridad contra la colisión concurrente de la UNIQUE.
        try {
            Sale saved = saleRepository.insert(sale);

            if (withWaste) {
                List<BakeryUseCase.WasteLineCommand> wasteCommands = saved.getLines().stream()
                        .filter(SaleLine::waste)
                        .map(l -> new BakeryUseCase.WasteLineCommand(l.productId(), l.quantity()))
                        .toList();
                // Sella la merma solo si de verdad hay renglones de merma (robusto si se
                // llamara
                // esta ruta sin merma; el controlador solo rutea aquí cuando anyMatch(waste)).
                if (!wasteCommands.isEmpty()) {
                    bakeryUseCase.sellWaste(saved.getId(), saved.getBranchId(), saved.getCashier(),
                            wasteCommands);
                }
            }

            Set<Long> enforceStockIds = resolveEnforceStockIds(saved);
            applySaleMovements(saved, enforceStockIds);
            registerCreditIfNeeded(saved);

            return new SaleResult(saved.getId(), saved.total(), saved.change(), false);
        } catch (DataIntegrityViolationException dup) {
            // Doble submit CONCURRENTE con la misma clave: la UNIQUE rechaza el segundo
            // insert. Se relee y se devuelve el id existente como dedup limpio, nunca 500.
            if (cmd.idempotencyKey() == null) {
                throw dup;
            }
            Long existingId = saleRepository.findIdByIdempotencyKey(cmd.idempotencyKey())
                    .orElseThrow(() -> dup);
            return saleResultFromExisting(existingId);
        }
    }

    /**
     * Construye el resultado idempotente de un reenvío leyendo los totales
     * persistidos (sin reconstruir el agregado {@code Sale}). El cambio se deriva
     * como {@code max(0, amountPaid - total)} (no existe columna {@code change}).
     */
    private SaleResult saleResultFromExisting(long saleId) {
        SaleRepository.SaleTotals t = saleRepository.totalsOf(saleId)
                .orElseThrow(() -> new IllegalStateException(
                        "No se pudo releer la venta existente " + saleId));
        BigDecimal total = t.total() == null ? BigDecimal.ZERO : t.total();
        BigDecimal paid = t.amountPaid() == null ? BigDecimal.ZERO : t.amountPaid();
        BigDecimal change = paid.subtract(total);
        if (change.signum() < 0) {
            change = BigDecimal.ZERO;
        }
        return new SaleResult(saleId, total, change, true);
    }

    /**
     * Fase de resolución y recomputación de precios server-side (ALTA-1 + MEDIA-2).
     * El backend ignora el precio del front en renglones de catálogo y usa el
     * precio
     * especial de cliente &gt; lista &gt; base; la merma fuerza {@code waste_price}
     * (ignora el front incluso en crédito); el artículo libre ({@code productId}
     * null) SÍ usa el precio del front y exige {@code unitPriceFront > 0}. Aplica
     * las
     * guardas de negocio (bloqueado, precio 0, bajo costo) y el tope conservador de
     * crédito al alza (§3.4.1). El descuento del front se respeta sin tope, acotado
     * a
     * {@code lineTotal >= 0}.
     */
    private List<SaleLine> resolveAndRepriceLines(SaleCommand cmd) {
        Set<Long> productIds = cmd.lines().stream()
                .map(RawLine::productId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<Long, PricingQueryPort.ProductPricing> pricing = pricingQueryPort.resolve(productIds, cmd.priceListId(),
                cmd.customerId());

        List<SaleLine> result = new ArrayList<>(cmd.lines().size());
        for (RawLine raw : cmd.lines()) {
            BigDecimal quantity = raw.quantity();
            if (raw.productId() == null) {
                // Artículo libre: el precio SÍ viene del front; se exige > 0.
                BigDecimal unitFront = raw.unitPriceFront() == null ? BigDecimal.ZERO : raw.unitPriceFront();
                if (unitFront.signum() <= 0) {
                    throw new IllegalArgumentException(
                            "El artículo especial requiere un importe mayor a 0.");
                }
                BigDecimal base = round2(unitFront);
                BigDecimal discount = clampDiscount(raw.discountFront(), base, quantity);
                result.add(new SaleLine(null, raw.description(), quantity, base, discount,
                        raw.unit(), false));
                continue;
            }

            // Renglón de catálogo.
            PricingQueryPort.ProductPricing pp = pricing.get(raw.productId());
            if (pp == null) {
                throw new IllegalArgumentException("El artículo ya no existe en el catálogo.");
            }
            String name = pp.name();

            // Guardas de negocio (orden: bloqueado -> precio 0 -> bajo costo).
            if (pp.blocked()) {
                throw new IllegalStateException(
                        "«" + name + "» está bloqueado y no se puede vender. Desbloquéalo en el catálogo.");
            }

            BigDecimal base;
            if (raw.waste()) {
                BigDecimal wastePrice = pp.wastePrice();
                if (wastePrice == null || wastePrice.signum() == 0) {
                    throw new IllegalArgumentException(
                            "«" + name + "» no tiene precio de merma. Captúralo en el catálogo.");
                }
                // Merma: SIEMPRE el precio de merma autoritativo; se ignora el front incluso
                // en crédito (no hay precio pactado que respetar; es una venta de merma).
                base = round2(wastePrice);
            } else {
                BigDecimal effective = pp.effectiveSalePrice();
                if (effective == null || effective.signum() == 0) {
                    throw new IllegalArgumentException(
                            "«" + name + "» no tiene precio. Captura primero el precio del artículo.");
                }
                // Bajo costo: compara el precio de catálogo contra el costo (sin descuento).
                if (!pp.allowBelowCost() && pp.cost() != null && pp.cost().signum() > 0
                        && effective.compareTo(pp.cost()) < 0) {
                    throw new IllegalArgumentException(
                            "«" + name + "» se vendería por debajo de su costo. "
                                    + "Ajusta el precio o habilita venta bajo costo en el catálogo.");
                }
                base = round2(effective);

                // Tope conservador de crédito al alza (§3.4.1): en crédito, si la base de
                // catálogo SUBIÓ respecto a lo que el POS mostró, se respeta el precio que vio
                // el cliente para no inflar su saldo. Solo al alza y solo en crédito.
                BigDecimal unitFront = raw.unitPriceFront();
                if (cmd.onCredit() && unitFront != null && base.compareTo(round2(unitFront)) > 0) {
                    BigDecimal capped = round2(unitFront);
                    log.warn("Venta a crédito: base de catálogo subió; se respeta el precio mostrado "
                            + "al cliente. producto={}, baseBack={}, unitFront={}, venta(idem)={}",
                            raw.productId(), base, capped, cmd.idempotencyKey());
                    base = capped;
                }
            }

            BigDecimal discount = clampDiscount(raw.discountFront(), base, quantity, name, cmd.idempotencyKey());
            result.add(new SaleLine(raw.productId(), raw.description(), quantity, base, discount,
                    raw.unit(), raw.waste()));
        }
        return result;
    }

    /**
     * Descuento normalizado a {@code [0, base*qty]} (sin traza), para artículo
     * libre.
     */
    private BigDecimal clampDiscount(BigDecimal discountFront, BigDecimal base, BigDecimal quantity) {
        return clampDiscount(discountFront, base, quantity, null, null);
    }

    /**
     * Descuento normalizado a {@code [0, base*qty]} para que
     * {@code lineTotal >= 0}.
     * Si recorta (el front mandó un descuento mayor que el importe, solo posible
     * por
     * un cambio de catálogo en caliente), registra la discrepancia para
     * trazabilidad
     * (§6.2).
     */
    private BigDecimal clampDiscount(BigDecimal discountFront, BigDecimal base, BigDecimal quantity,
            String name, String idempotencyKey) {
        BigDecimal discount = discountFront == null ? BigDecimal.ZERO : discountFront;
        if (discount.signum() < 0) {
            discount = BigDecimal.ZERO;
        }
        discount = round2(discount);
        BigDecimal maxDiscount = round2(base.multiply(quantity));
        if (discount.compareTo(maxDiscount) > 0) {
            if (name != null) {
                log.warn("Descuento de promo recortado: producto={}, descuentoFront={}, baseBack*qty={}, "
                        + "venta(idem)={}", name, discount, maxDiscount, idempotencyKey);
            }
            discount = maxDiscount;
        }
        return discount;
    }

    /**
     * Descuento global normalizado a
     * {@code max(0, min(globalDiscount, sumaLineTotals))}
     * para que {@code total() >= 0}.
     */
    private BigDecimal normalizeGlobalDiscount(BigDecimal globalDiscount, List<SaleLine> lines) {
        BigDecimal raw = globalDiscount == null ? BigDecimal.ZERO : globalDiscount;
        if (raw.signum() < 0) {
            raw = BigDecimal.ZERO;
        }
        BigDecimal linesTotal = lines.stream()
                .map(SaleLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (raw.compareTo(linesTotal) > 0) {
            raw = linesTotal;
        }
        return round2(raw);
    }

    private BigDecimal round2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Resuelve, para los renglones con producto que NO son merma, cuáles están
     * sujetos a no-sobreventa. Los renglones de merma se excluyen: no descuentan
     * del
     * stock normal.
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
     * está
     * en {@code enforceStockIds} usa la salida estricta (rechaza y revierte si no
     * hay
     * existencia); en caso contrario usa el movimiento de venta normal. Los
     * artículos
     * libres y los renglones de merma no mueven el stock normal (la merma se
     * descontó
     * aparte, de bakery_waste_stock).
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
        // producto) no movieron stock, así que se omiten. Un renglón de merma se
        // devuelve a la EXISTENCIA DE MERMA (no al stock normal), porque la venta
        // descontó de ahí.
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
     * devueltas. A diferencia de la cancelación total, permite devolver renglones o
     * cantidades parciales.
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
     * todavía). La cotización NO recomputa precio (no cobra ni descuenta
     * inventario).
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
     * Comando crudo de registro de venta: el controlador lo arma con los datos del
     * request (incluido el precio/descuento del front) y el servicio recomputa el
     * precio autoritativo. {@code globalDiscount} es el crudo del front (el
     * servicio
     * lo normaliza).
     */
    public record SaleCommand(long branchId, Long cashRegisterId, Long shiftId, String cashier,
            String salesperson, Long customerId, short priceListId, BigDecimal globalDiscount,
            String note, boolean onCredit, List<RawLine> lines, List<Payment> payments,
            String idempotencyKey) {
    }

    /**
     * Renglón crudo del comando de venta.
     * {@code unitPriceFront}/{@code discountFront}
     * son los valores que mostró el POS; el backend ignora {@code unitPriceFront}
     * en
     * renglones de catálogo (lo usa solo en artículo libre y como tope de crédito
     * al
     * alza) y respeta {@code discountFront} acotado a {@code lineTotal >= 0}.
     */
    public record RawLine(Long productId, String description, BigDecimal quantity,
            BigDecimal unitPriceFront, BigDecimal discountFront, String unit, boolean waste) {
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
