package com.mybusinesssilva.platform.billing;

import com.mybusinesssilva.invoicing.domain.model.CfdiConcept;
import com.mybusinesssilva.invoicing.domain.model.ReceiverInfo;
import com.mybusinesssilva.invoicing.domain.port.out.CfdiStampingPort;
import com.mybusinesssilva.platform.audit.AuditService;
import com.mybusinesssilva.platform.notifications.NotificationPort;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facturación de las ventas del Super Admin (licencias y módulos adicionales).
 *
 * <p>Al registrar una venta, se decide el tipo de comprobante:
 * <ul>
 *   <li>Si el cliente requiere factura: se emite un CFDI 4.0 timbrado (vía el PAC) y se guarda.</li>
 *   <li>Si no la requiere: se genera un comprobante PDF (no fiscal) y se envía al correo capturado.</li>
 * </ul>
 * En ambos casos se registra la venta en {@code admin.superadmin_sale} y en la auditoría.
 */
@Service
public class SuperAdminBillingService {

    private final JdbcClient jdbc;
    private final CfdiStampingPort stampingPort;
    private final NotificationPort notificationPort;
    private final AuditService auditService;

    public SuperAdminBillingService(JdbcClient jdbc,
                                    CfdiStampingPort stampingPort,
                                    NotificationPort notificationPort,
                                    AuditService auditService) {
        this.jdbc = jdbc;
        this.stampingPort = stampingPort;
        this.notificationPort = notificationPort;
        this.auditService = auditService;
    }

    /**
     * Registra una venta del Super Admin con CFDI (si el cliente lo requiere) o comprobante PDF.
     *
     * @param actor          quién registra la venta
     * @param businessId     negocio al que se le vende
     * @param kind           LICENSE o SURCHARGE
     * @param amount         monto de la venta
     * @param requiresInvoice true si el cliente requiere factura fiscal (CFDI)
     * @param customerEmail  correo del cliente (para enviar el comprobante)
     * @param receiver       datos fiscales (obligatorios si requiresInvoice)
     * @param concept        descripción del concepto
     * @return resultado con el tipo de comprobante y su identificador
     */
    @Transactional
    public SaleReceipt registerSale(String actor, long businessId, String kind, BigDecimal amount,
                                    boolean requiresInvoice, String customerEmail,
                                    ReceiverInfo receiver, String concept) {
        String voucherType;
        String reference;

        if (requiresInvoice) {
            if (receiver == null) {
                throw new IllegalArgumentException(
                        "Se requieren los datos fiscales del cliente para emitir la factura");
            }
            List<CfdiConcept> concepts = List.of(new CfdiConcept(
                    "81111500", "E48", concept == null ? "Licencia de software" : concept,
                    BigDecimal.ONE, amount, amount));
            CfdiStampingPort.StampResult stamp = stampingPort.stampInvoice(receiver, concepts);
            if (!stamp.success()) {
                throw new IllegalStateException("No se pudo timbrar el CFDI: " + stamp.error());
            }
            voucherType = "CFDI";
            reference = stamp.uuid();
            if (customerEmail != null && !customerEmail.isBlank()) {
                notificationPort.sendEmail(customerEmail, "Tu factura (CFDI)",
                        "Adjuntamos tu factura. Folio fiscal: " + stamp.uuid(),
                        "factura.xml", stamp.xml() == null ? null : stamp.xml().getBytes());
            }
        } else {
            voucherType = "PDF";
            byte[] pdf = buildSimpleReceipt(businessId, kind, amount);
            reference = "PDF-" + businessId + "-" + System.currentTimeMillis();
            if (customerEmail != null && !customerEmail.isBlank()) {
                notificationPort.sendEmail(customerEmail, "Tu comprobante de compra",
                        "Adjuntamos el comprobante de tu compra en MyBusiness Silva.",
                        "comprobante.txt", pdf);
            }
        }

        jdbc.sql("""
                INSERT INTO admin.superadmin_sale
                    (business_id, kind, amount, voucher_type, customer_email)
                VALUES (:bid, :kind, :amount, :voucher, :email)
                """)
                .param("bid", businessId)
                .param("kind", kind)
                .param("amount", amount)
                .param("voucher", voucherType)
                .param("email", customerEmail)
                .update();

        auditService.recordGlobal(actor, "SUPERADMIN_SALE", "business",
                String.valueOf(businessId),
                Map.of("kind", kind, "amount", amount, "voucher", voucherType));

        return new SaleReceipt(voucherType, reference);
    }

    /**
     * Genera un comprobante no fiscal simple (texto). En producción se sustituye por un PDF real
     * usando una librería de PDF; la interfaz de este método no cambiaría.
     */
    private byte[] buildSimpleReceipt(long businessId, String kind, BigDecimal amount) {
        String text = "MyBusiness Silva - Comprobante de compra\n"
                + "Negocio: " + businessId + "\n"
                + "Concepto: " + ("LICENSE".equals(kind) ? "Licencia" : "Módulo adicional") + "\n"
                + "Monto: $" + amount + "\n";
        return text.getBytes();
    }

    /**
     * Resultado del registro de una venta del Super Admin.
     *
     * @param voucherType tipo de comprobante emitido (CFDI o PDF)
     * @param reference   folio fiscal (CFDI) o identificador del comprobante PDF
     */
    public record SaleReceipt(String voucherType, String reference) {
    }
}
