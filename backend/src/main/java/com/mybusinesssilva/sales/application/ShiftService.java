package com.mybusinesssilva.sales.application;

import java.math.BigDecimal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de turnos y cortes de caja.
 *
 * <p>Un turno se abre con un fondo de caja, registra entradas/salidas de efectivo, y al cerrarse
 * se calcula el corte (ventas por método de pago) y se compara con el conteo físico (arqueo),
 * reportando la diferencia.
 */
@Service
public class ShiftService {

    private final JdbcClient jdbc;

    public ShiftService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Abre un turno para una caja con su fondo inicial. */
    @Transactional
    public long openShift(long cashRegisterId, String openedBy, BigDecimal openingFloat) {
        return jdbc.sql("""
                INSERT INTO shift (cash_register_id, opened_by, opening_float, status)
                VALUES (:reg, :by, :float, 'OPEN')
                RETURNING id
                """)
                .param("reg", cashRegisterId)
                .param("by", openedBy)
                .param("float", openingFloat == null ? BigDecimal.ZERO : openingFloat)
                .query(Long.class)
                .single();
    }

    /** Registra una entrada o salida de efectivo en el turno (retiro, gasto, ingreso). */
    @Transactional
    public void recordCashMovement(long shiftId, String direction, BigDecimal amount,
                                   String reason, String actor) {
        jdbc.sql("""
                INSERT INTO cash_movement (shift_id, direction, amount, reason, actor)
                VALUES (:shift, :dir, :amount, :reason, :actor)
                """)
                .param("shift", shiftId)
                .param("dir", direction)
                .param("amount", amount)
                .param("reason", reason)
                .param("actor", actor)
                .update();
    }

    /**
     * Cierra el turno con el efectivo contado (arqueo) y devuelve el corte con la diferencia.
     */
    @Transactional
    public ShiftClosure closeShift(long shiftId, String closedBy, BigDecimal countedCash) {
        BigDecimal openingFloat = jdbc.sql("SELECT opening_float FROM shift WHERE id = :id")
                .param("id", shiftId).query(BigDecimal.class).single();

        BigDecimal cashSales = sumSalesByMethod(shiftId, "CASH");
        BigDecimal cardSales = sumSalesByMethod(shiftId, "CARD");
        BigDecimal transferSales = sumSalesByMethod(shiftId, "TRANSFER");
        BigDecimal voucherSales = sumSalesByMethod(shiftId, "VOUCHER");

        BigDecimal cashIn = sumCashMovements(shiftId, "IN");
        BigDecimal cashOut = sumCashMovements(shiftId, "OUT");

        // Efectivo esperado en caja = fondo + ventas en efectivo + entradas - salidas.
        BigDecimal expectedCash = openingFloat.add(cashSales).add(cashIn).subtract(cashOut);
        BigDecimal difference = (countedCash == null ? BigDecimal.ZERO : countedCash)
                .subtract(expectedCash);

        jdbc.sql("""
                UPDATE shift SET status = 'CLOSED', closed_by = :by, counted_cash = :counted,
                    closed_at = now()
                WHERE id = :id
                """)
                .param("id", shiftId)
                .param("by", closedBy)
                .param("counted", countedCash)
                .update();

        return new ShiftClosure(shiftId, openingFloat, cashSales, cardSales, transferSales,
                voucherSales, cashIn, cashOut, expectedCash, countedCash, difference);
    }

    private BigDecimal sumSalesByMethod(long shiftId, String method) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(sp.amount), 0) FROM sale_payment sp
                JOIN sale s ON s.id = sp.sale_id
                WHERE s.shift_id = :shift AND s.status = 'COMPLETED' AND sp.method = :method
                """)
                .param("shift", shiftId)
                .param("method", method)
                .query(BigDecimal.class)
                .single();
    }

    private BigDecimal sumCashMovements(long shiftId, String direction) {
        return jdbc.sql("""
                SELECT COALESCE(SUM(amount), 0) FROM cash_movement
                WHERE shift_id = :shift AND direction = :dir
                """)
                .param("shift", shiftId)
                .param("dir", direction)
                .query(BigDecimal.class)
                .single();
    }

    /**
     * Resultado del corte de caja al cerrar un turno.
     */
    public record ShiftClosure(
            long shiftId,
            BigDecimal openingFloat,
            BigDecimal cashSales,
            BigDecimal cardSales,
            BigDecimal transferSales,
            BigDecimal voucherSales,
            BigDecimal cashIn,
            BigDecimal cashOut,
            BigDecimal expectedCash,
            BigDecimal countedCash,
            BigDecimal difference) {
    }
}
