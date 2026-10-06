package com.mybusinesssilva.sales.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Venta. Concentra las reglas de cálculo de totales, el descuento global y la validación de
 * pagos. Soporta tres modalidades: venta de contado (los pagos cubren el total), venta a
 * crédito (los pagos cubren solo una parte y el resto genera una cuenta por cobrar) y
 * cotización/apartado (sin pagos ni afectación de inventario).
 *
 * <p>Es prácticamente inmutable: se construye con sus renglones y pagos ya definidos; solo
 * cambian el id/folio asignados al persistir y el estado al cancelar.
 */
public class Sale {

    private Long id;
    private String folio;
    private final long branchId;
    private final Long cashRegisterId;
    private final Long shiftId;
    private final String cashier;
    private final String salesperson;
    private final Long customerId;
    private final short priceListId;
    private final BigDecimal globalDiscount;
    private final String note;
    private final boolean onCredit;
    private final List<SaleLine> lines;
    private final List<Payment> payments;
    private final String idempotencyKey;
    private SaleStatus status;

    private Sale(long branchId, Long cashRegisterId, Long shiftId, String cashier,
                 String salesperson, Long customerId, short priceListId,
                 BigDecimal globalDiscount, String note, boolean onCredit,
                 List<SaleLine> lines, List<Payment> payments, String idempotencyKey) {
        this.branchId = branchId;
        this.cashRegisterId = cashRegisterId;
        this.shiftId = shiftId;
        this.cashier = cashier;
        this.salesperson = salesperson;
        this.customerId = customerId;
        this.priceListId = priceListId <= 0 ? 1 : priceListId;
        this.globalDiscount = globalDiscount == null ? BigDecimal.ZERO : globalDiscount;
        this.note = note;
        this.onCredit = onCredit;
        this.lines = List.copyOf(lines);
        this.payments = List.copyOf(payments);
        this.idempotencyKey = idempotencyKey;
        this.status = SaleStatus.COMPLETED;
    }

    /**
     * Crea una venta completada de CONTADO, validando que haya renglones y que los pagos cubran
     * el total (incluyendo descuento global). Versión simple compatible con el flujo anterior.
     */
    public static Sale complete(long branchId, Long cashRegisterId, Long shiftId, String cashier,
                                Long customerId, List<SaleLine> lines, List<Payment> payments,
                                String idempotencyKey) {
        return complete(branchId, cashRegisterId, shiftId, cashier, cashier, customerId,
                (short) 1, BigDecimal.ZERO, null, false, lines, payments, idempotencyKey);
    }

    /**
     * Crea una venta completada con todos los metadatos comerciales y soporte de crédito.
     *
     * <ul>
     *   <li>Contado ({@code onCredit = false}): los pagos deben cubrir el total; de lo contrario
     *       se lanza excepción.</li>
     *   <li>Crédito ({@code onCredit = true}): los pagos pueden ser menores al total (incluso
     *       cero). Requiere cliente asignado, porque el saldo genera una cuenta por cobrar.</li>
     * </ul>
     *
     * @param salesperson    vendedor (puede coincidir con el cajero)
     * @param priceListId    lista de precio usada (1..5)
     * @param globalDiscount descuento global de la venta (sobre el total de renglones)
     * @param note           nota/observaciones (opcional)
     * @param onCredit       true si es venta a crédito (permite pagos parciales)
     */
    public static Sale complete(long branchId, Long cashRegisterId, Long shiftId, String cashier,
                                String salesperson, Long customerId, short priceListId,
                                BigDecimal globalDiscount, String note, boolean onCredit,
                                List<SaleLine> lines, List<Payment> payments,
                                String idempotencyKey) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("La venta debe tener al menos un renglón");
        }
        Sale sale = new Sale(branchId, cashRegisterId, shiftId, cashier, salesperson, customerId,
                priceListId, globalDiscount, note, onCredit,
                lines, payments == null ? List.of() : payments, idempotencyKey);

        BigDecimal total = sale.total();
        if (total.signum() < 0) {
            throw new IllegalArgumentException(
                    "El descuento global (" + sale.globalDiscount + ") no puede superar el importe de la venta");
        }
        BigDecimal paid = sale.totalPaid();

        if (onCredit) {
            if (customerId == null) {
                throw new IllegalArgumentException(
                        "Una venta a crédito requiere un cliente asignado");
            }
            if (paid.compareTo(total) > 0) {
                throw new IllegalArgumentException(
                        "En una venta a crédito los pagos (" + paid + ") no pueden superar el total (" + total + ")");
            }
        } else if (paid.compareTo(total) < 0) {
            throw new IllegalArgumentException(
                    "Los pagos (" + paid + ") no cubren el total de la venta (" + total + ")");
        }
        return sale;
    }

    /**
     * Crea una cotización o apartado: tiene renglones pero no exige pagos ni afecta inventario.
     * Se guarda con estado QUOTE para consultarse o convertirse en venta después.
     */
    public static Sale quote(long branchId, String cashier, Long customerId,
                             List<SaleLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("La cotización debe tener al menos un renglón");
        }
        Sale sale = new Sale(branchId, null, null, cashier, cashier, customerId,
                (short) 1, BigDecimal.ZERO, null, false, lines, List.of(), null);
        sale.status = SaleStatus.QUOTE;
        return sale;
    }

    /** Subtotal: suma de los importes de los renglones (precio * cantidad), sin descuentos. */
    public BigDecimal subtotal() {
        return lines.stream()
                .map(l -> l.unitPrice().multiply(l.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Descuento total: descuentos de renglón + descuento global de la venta. */
    public BigDecimal discount() {
        BigDecimal lineDiscounts = lines.stream()
                .map(SaleLine::discount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return lineDiscounts.add(globalDiscount);
    }

    /** Total a pagar: importes netos de renglón menos el descuento global. */
    public BigDecimal total() {
        BigDecimal linesTotal = lines.stream()
                .map(SaleLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return linesTotal.subtract(globalDiscount);
    }

    /** Suma de todos los pagos recibidos. */
    public BigDecimal totalPaid() {
        return payments.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Saldo pendiente (total - pagado), nunca negativo. Relevante en ventas a crédito. */
    public BigDecimal balanceDue() {
        BigDecimal due = total().subtract(totalPaid());
        return due.signum() < 0 ? BigDecimal.ZERO : due;
    }

    /** Cambio a devolver (pagos - total), nunca negativo. */
    public BigDecimal change() {
        BigDecimal change = totalPaid().subtract(total());
        return change.signum() < 0 ? BigDecimal.ZERO : change;
    }

    // Getters y setters controlados.
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFolio() {
        return folio;
    }

    public void setFolio(String folio) {
        this.folio = folio;
    }

    public long getBranchId() {
        return branchId;
    }

    public Long getCashRegisterId() {
        return cashRegisterId;
    }

    public Long getShiftId() {
        return shiftId;
    }

    public String getCashier() {
        return cashier;
    }

    public String getSalesperson() {
        return salesperson;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public short getPriceListId() {
        return priceListId;
    }

    public BigDecimal getGlobalDiscount() {
        return globalDiscount;
    }

    public String getNote() {
        return note;
    }

    public boolean isOnCredit() {
        return onCredit;
    }

    public List<SaleLine> getLines() {
        return lines;
    }

    public List<Payment> getPayments() {
        return payments;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public SaleStatus getStatus() {
        return status;
    }

    public void markVoided() {
        this.status = SaleStatus.VOIDED;
    }
}
