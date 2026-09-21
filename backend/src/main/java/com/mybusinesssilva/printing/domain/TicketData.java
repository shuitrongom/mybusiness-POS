package com.mybusinesssilva.printing.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Datos de un ticket de venta a imprimir. Es independiente de la fuente (una venta, una
 * reimpresión, etc.): quien imprime arma este objeto y el generador produce el ESC/POS.
 *
 * @param headerLines  líneas del encabezado (nombre del negocio, dirección, RFC)
 * @param footerLines  líneas del pie (agradecimiento, leyendas)
 * @param folio        folio del ticket
 * @param cashier      cajero
 * @param items        renglones del ticket
 * @param subtotal     subtotal
 * @param discount     descuento total
 * @param total        total
 * @param paid         total pagado
 * @param change       cambio
 * @param paperWidth   ancho en caracteres (58mm≈32, 80mm≈48)
 */
public record TicketData(
        List<String> headerLines,
        List<String> footerLines,
        String folio,
        String cashier,
        List<Item> items,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal total,
        BigDecimal paid,
        BigDecimal change,
        int paperWidth) {

    /**
     * Renglón del ticket.
     *
     * @param description descripción del producto
     * @param quantity    cantidad
     * @param unitPrice   precio unitario
     * @param lineTotal   importe del renglón
     */
    public record Item(String description, BigDecimal quantity,
                       BigDecimal unitPrice, BigDecimal lineTotal) {
    }
}
