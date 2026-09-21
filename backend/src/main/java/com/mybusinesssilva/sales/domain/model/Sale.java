package com.mybusinesssilva.sales.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Venta. Concentra las reglas de cálculo de totales y la validación de que los pagos cubran
 * el importe. Es inmutable: se construye con sus renglones y pagos ya definidos.
 */
public class Sale {

    private Long id;
    private String folio;
    private final long branchId;
    private final Long cashRegisterId;
    private final Long shiftId;
    private final String cashier;
    private final Long customerId;
    private final List<SaleLine> lines;
    private final List<Payment> payments;
    private final String idempotencyKey;
    private SaleStatus status;

    private Sale(long branchId, Long cashRegisterId, Long shiftId, String cashier,
                 Long customerId, List<SaleLine> lines, List<Payment> payments,
                 String idempotencyKey) {
        this.branchId = branchId;
        this.cashRegisterId = cashRegisterId;
        this.shiftId = shiftId;
        this.cashier = cashier;
        this.customerId = customerId;
        this.lines = List.copyOf(lines);
        this.payments = List.copyOf(payments);
        this.idempotencyKey = idempotencyKey;
        this.status = SaleStatus.COMPLETED;
    }

    /**
     * Crea una venta completada, validando que haya renglones y que los pagos cubran el total.
     */
    public static Sale complete(long branchId, Long cashRegisterId, Long shiftId, String cashier,
                                Long customerId, List<SaleLine> lines, List<Payment> payments,
                                String idempotencyKey) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("La venta debe tener al menos un renglón");
        }
        Sale sale = new Sale(branchId, cashRegisterId, shiftId, cashier, customerId,
                lines, payments == null ? List.of() : payments, idempotencyKey);

        BigDecimal total = sale.total();
        BigDecimal paid = sale.totalPaid();
        if (paid.compareTo(total) < 0) {
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
        Sale sale = new Sale(branchId, null, null, cashier, customerId, lines, List.of(), null);
        sale.status = SaleStatus.QUOTE;
        return sale;
    }

    /** Subtotal: suma de los importes de los renglones sin descuentos de renglón restados aún. */
    public BigDecimal subtotal() {
        return lines.stream()
                .map(l -> l.unitPrice().multiply(l.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Descuento total: suma de los descuentos de renglón. */
    public BigDecimal discount() {
        return lines.stream().map(SaleLine::discount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Total a pagar: suma de los importes netos de cada renglón. */
    public BigDecimal total() {
        return lines.stream().map(SaleLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Suma de todos los pagos recibidos. */
    public BigDecimal totalPaid() {
        return payments.stream().map(Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
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

    public Long getCustomerId() {
        return customerId;
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
