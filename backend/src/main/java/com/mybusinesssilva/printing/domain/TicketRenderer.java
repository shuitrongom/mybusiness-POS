package com.mybusinesssilva.printing.domain;

import java.math.BigDecimal;

/**
 * Renderiza un {@link TicketData} a comandos ESC/POS listos para imprimir.
 *
 * <p>Formatea el ticket para el ancho de papel indicado (número de caracteres por línea),
 * alineando importes a la derecha. Opcionalmente abre el cajón de dinero y corta el papel.
 */
public class TicketRenderer {

    /**
     * @param ticket     datos del ticket
     * @param openDrawer si se debe abrir el cajón de dinero al final
     * @param autoCut    si se debe cortar el papel al final
     * @return bytes ESC/POS
     */
    public byte[] render(TicketData ticket, boolean openDrawer, boolean autoCut) {
        int width = ticket.paperWidth() <= 0 ? 48 : ticket.paperWidth();
        EscPosBuilder b = new EscPosBuilder();

        // Encabezado centrado y en negritas.
        b.alignCenter().bold(true);
        for (String headerLine : ticket.headerLines()) {
            b.line(headerLine);
        }
        b.bold(false).normalSize();

        if (ticket.folio() != null) {
            b.line("Folio: " + ticket.folio());
        }
        if (ticket.cashier() != null) {
            b.line("Cajero: " + ticket.cashier());
        }

        b.alignLeft().line(repeat('-', width));

        // Renglones.
        for (TicketData.Item item : ticket.items()) {
            b.line(item.description());
            String left = "  " + trim(item.quantity()) + " x " + money(item.unitPrice());
            String right = money(item.lineTotal());
            b.line(twoColumns(left, right, width));
        }

        b.line(repeat('-', width));

        // Totales.
        b.line(twoColumns("Subtotal:", money(ticket.subtotal()), width));
        if (ticket.discount() != null && ticket.discount().signum() > 0) {
            b.line(twoColumns("Descuento:", money(ticket.discount()), width));
        }
        b.bold(true).line(twoColumns("TOTAL:", money(ticket.total()), width)).bold(false);
        if (ticket.paid() != null) {
            b.line(twoColumns("Pago:", money(ticket.paid()), width));
        }
        if (ticket.change() != null) {
            b.line(twoColumns("Cambio:", money(ticket.change()), width));
        }

        // Pie centrado.
        b.feed(1).alignCenter();
        for (String footerLine : ticket.footerLines()) {
            b.line(footerLine);
        }

        b.feed(3);
        if (autoCut) {
            b.cut();
        }
        if (openDrawer) {
            b.openDrawer();
        }
        return b.build();
    }

    /** Coloca un texto a la izquierda y otro a la derecha, rellenando con espacios al ancho. */
    private String twoColumns(String left, String right, int width) {
        int space = width - left.length() - right.length();
        if (space < 1) {
            space = 1;
        }
        return left + repeat(' ', space) + right;
    }

    private String repeat(char c, int n) {
        return String.valueOf(c).repeat(Math.max(0, n));
    }

    private String money(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return "$" + v.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String trim(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
