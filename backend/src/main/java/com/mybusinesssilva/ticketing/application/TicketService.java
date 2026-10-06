package com.mybusinesssilva.ticketing.application;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Casos de uso del ticket de venta: leer y guardar la configuración del ticket del negocio,
 * y consultar ventas para su reimpresión. Opera sobre el schema del tenant en curso (el
 * {@code search_path} lo fija el DataSource consciente del tenant), así que las consultas van
 * sin prefijo de schema y la RLS aplica por {@code app.current_tenant}.
 */
@Service
public class TicketService {

    private final JdbcClient jdbc;

    public TicketService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Devuelve la configuración del ticket del negocio (fila única). */
    public TicketSettings getSettings() {
        return jdbc.sql("""
                SELECT business_name, address, phone, rfc, header_message, footer_message,
                       show_logo, show_address, show_phone, show_rfc, show_cashier, show_folio,
                       paper_width_mm, logo_url
                FROM ticket_settings
                ORDER BY id LIMIT 1
                """)
                .query((rs, n) -> new TicketSettings(
                        rs.getString("business_name"), rs.getString("address"), rs.getString("phone"),
                        rs.getString("rfc"), rs.getString("header_message"), rs.getString("footer_message"),
                        rs.getBoolean("show_logo"), rs.getBoolean("show_address"), rs.getBoolean("show_phone"),
                        rs.getBoolean("show_rfc"), rs.getBoolean("show_cashier"), rs.getBoolean("show_folio"),
                        rs.getInt("paper_width_mm"), rs.getString("logo_url")))
                .optional()
                .orElseGet(TicketSettings::defaults);
    }

    /** Guarda (actualiza) la configuración del ticket. Crea la fila si no existía. */
    public TicketSettings saveSettings(TicketSettings s) {
        int updated = jdbc.sql("""
                UPDATE ticket_settings SET
                    business_name = :name, address = :address, phone = :phone, rfc = :rfc,
                    header_message = :header, footer_message = :footer,
                    show_logo = :showLogo, show_address = :showAddress, show_phone = :showPhone,
                    show_rfc = :showRfc, show_cashier = :showCashier, show_folio = :showFolio,
                    paper_width_mm = :paper, logo_url = :logo, updated_at = now()
                """)
                .param("name", s.businessName()).param("address", s.address()).param("phone", s.phone())
                .param("rfc", s.rfc()).param("header", s.headerMessage()).param("footer", s.footerMessage())
                .param("showLogo", s.showLogo()).param("showAddress", s.showAddress())
                .param("showPhone", s.showPhone()).param("showRfc", s.showRfc())
                .param("showCashier", s.showCashier()).param("showFolio", s.showFolio())
                .param("paper", s.paperWidthMm()).param("logo", s.logoUrl())
                .update();
        if (updated == 0) {
            jdbc.sql("""
                    INSERT INTO ticket_settings
                        (business_name, address, phone, rfc, header_message, footer_message,
                         show_logo, show_address, show_phone, show_rfc, show_cashier, show_folio,
                         paper_width_mm, logo_url)
                    VALUES (:name, :address, :phone, :rfc, :header, :footer,
                            :showLogo, :showAddress, :showPhone, :showRfc, :showCashier, :showFolio,
                            :paper, :logo)
                    """)
                    .param("name", s.businessName()).param("address", s.address()).param("phone", s.phone())
                    .param("rfc", s.rfc()).param("header", s.headerMessage()).param("footer", s.footerMessage())
                    .param("showLogo", s.showLogo()).param("showAddress", s.showAddress())
                    .param("showPhone", s.showPhone()).param("showRfc", s.showRfc())
                    .param("showCashier", s.showCashier()).param("showFolio", s.showFolio())
                    .param("paper", s.paperWidthMm()).param("logo", s.logoUrl())
                    .update();
        }
        return getSettings();
    }

    /** Lista las ventas recientes (para buscar y reimprimir), más nuevas primero. */
    public List<SaleSummary> recentSales(int limit) {
        return jdbc.sql("""
                SELECT s.id, s.folio, s.total, s.status, s.cashier, s.created_at,
                       b.name AS branch_name
                FROM sale s
                LEFT JOIN branch b ON b.id = s.branch_id
                WHERE s.status = 'COMPLETED'
                ORDER BY s.created_at DESC
                LIMIT :limit
                """)
                .param("limit", Math.min(Math.max(limit, 1), 200))
                .query((rs, n) -> new SaleSummary(
                        rs.getLong("id"), rs.getString("folio"), rs.getBigDecimal("total"),
                        rs.getString("cashier"), rs.getString("branch_name"),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .list();
    }

    /** Obtiene el detalle completo de una venta (encabezado, renglones y pagos) para el ticket. */
    public SaleTicket getSaleTicket(long saleId) {
        SaleSummary head = jdbc.sql("""
                SELECT s.id, s.folio, s.total, s.status, s.cashier, s.created_at, b.name AS branch_name
                FROM sale s LEFT JOIN branch b ON b.id = s.branch_id
                WHERE s.id = :id
                """)
                .param("id", saleId)
                .query((rs, n) -> new SaleSummary(
                        rs.getLong("id"), rs.getString("folio"), rs.getBigDecimal("total"),
                        rs.getString("cashier"), rs.getString("branch_name"),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .optional()
                .orElse(null);
        if (head == null) {
            return null;
        }
        List<TicketLine> lines = jdbc.sql("""
                SELECT description, quantity, unit_price, line_total
                FROM sale_line WHERE sale_id = :id ORDER BY id
                """)
                .param("id", saleId)
                .query((rs, n) -> new TicketLine(
                        rs.getString("description"), rs.getBigDecimal("quantity"),
                        rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_total")))
                .list();
        List<TicketPayment> payments = jdbc.sql("""
                SELECT method, amount FROM sale_payment WHERE sale_id = :id ORDER BY id
                """)
                .param("id", saleId)
                .query((rs, n) -> new TicketPayment(rs.getString("method"), rs.getBigDecimal("amount")))
                .list();
        return new SaleTicket(head, lines, payments);
    }

    // ---- DTOs ----

    /** Configuración del ticket del negocio. */
    public record TicketSettings(
            String businessName, String address, String phone, String rfc,
            String headerMessage, String footerMessage,
            boolean showLogo, boolean showAddress, boolean showPhone, boolean showRfc,
            boolean showCashier, boolean showFolio, int paperWidthMm, String logoUrl) {

        public static TicketSettings defaults() {
            return new TicketSettings(null, null, null, null, null, "¡Gracias por su compra!",
                    true, true, true, false, true, true, 80, null);
        }
    }

    /** Resumen de una venta para el listado de reimpresión. */
    public record SaleSummary(long id, String folio, BigDecimal total, String cashier,
                              String branchName, OffsetDateTime createdAt) {
    }

    /** Renglón del ticket. */
    public record TicketLine(String description, BigDecimal quantity, BigDecimal unitPrice,
                             BigDecimal lineTotal) {
    }

    /** Pago del ticket. */
    public record TicketPayment(String method, BigDecimal amount) {
    }

    /** Ticket completo de una venta. */
    public record SaleTicket(SaleSummary sale, List<TicketLine> lines, List<TicketPayment> payments) {
    }
}
