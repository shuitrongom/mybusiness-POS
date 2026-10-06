package com.mybusinesssilva.invoicing.application;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generación de recibos electrónicos de pago (complemento de Pagos 2.0).
 *
 * <p>Corresponde a la pantalla "Generar recibos de pago": se elige el cliente, la forma de pago,
 * los datos bancarios y los documentos (facturas a crédito PPD) a los que se aplica el pago. Se
 * genera un CFDI de tipo pago (recibo) con su folio consecutivo y se timbra vía PAC.
 */
@Service
public class PaymentReceiptService {

    private final JdbcClient jdbc;
    private final IssuerService issuerService;

    public PaymentReceiptService(JdbcClient jdbc, IssuerService issuerService) {
        this.jdbc = jdbc;
        this.issuerService = issuerService;
    }

    /**
     * Genera un recibo de pago aplicando el monto a uno o varios documentos.
     *
     * @param customerId   cliente que paga
     * @param paymentForm  forma de pago SAT (c_FormaPago)
     * @param paymentDate  fecha del pago (YYYY-MM-DD)
     * @param bank         banco (opcional)
     * @param operationNo  número de operación (opcional)
     * @param docs         documentos y montos aplicados
     * @return id del complemento generado
     */
    @Transactional
    public ReceiptResult generate(Long customerId, String paymentForm, String paymentDate,
                                  String bank, String operationNo, List<AppliedDoc> docs) {
        if (docs == null || docs.isEmpty()) {
            throw new IllegalArgumentException("El recibo debe aplicar al menos un documento");
        }
        BigDecimal totalPaid = docs.stream()
                .map(AppliedDoc::paidAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalPaid.signum() <= 0) {
            throw new IllegalArgumentException("El monto del pago debe ser positivo");
        }

        // Serie/folio del recibo de pago y timbrado simulado (UUID del complemento).
        String series = seriesForPayment();
        long folio = issuerService.nextFolio("PAYMENT", series);
        String uuid = java.util.UUID.randomUUID().toString().toUpperCase();

        // El complemento se ancla al primer documento (referencia principal).
        long firstCfdiId = docs.get(0).cfdiId();

        long complementId = jdbc.sql("""
                INSERT INTO cfdi_payment_complement
                    (cfdi_id, customer_id, series, folio, paid_amount, payment_form, payment_date,
                     currency, bank, operation_no, uuid, status)
                VALUES (:cfdi, :cust, :series, :folio, :amount, :form, CAST(:date AS date),
                        'MXN', :bank, :op, :uuid, 'STAMPED')
                RETURNING id
                """)
                .param("cfdi", firstCfdiId)
                .param("cust", customerId)
                .param("series", series)
                .param("folio", folio)
                .param("amount", totalPaid)
                .param("form", paymentForm)
                .param("date", paymentDate)
                .param("bank", bank)
                .param("op", operationNo)
                .param("uuid", uuid)
                .query(Long.class)
                .single();

        // Registra cada documento pagado con su saldo anterior/nuevo.
        for (AppliedDoc d : docs) {
            BigDecimal prev = currentBalance(d.cfdiId());
            BigDecimal newBalance = prev.subtract(d.paidAmount()).max(BigDecimal.ZERO);
            jdbc.sql("""
                    INSERT INTO cfdi_payment_doc
                        (complement_id, related_cfdi_id, related_uuid, installment,
                         prev_balance, paid_amount, new_balance)
                    VALUES (:comp, :cfdi, :uuid, :inst, :prev, :paid, :newb)
                    """)
                    .param("comp", complementId)
                    .param("cfdi", d.cfdiId())
                    .param("uuid", uuidOf(d.cfdiId()))
                    .param("inst", d.installment() <= 0 ? 1 : d.installment())
                    .param("prev", prev)
                    .param("paid", d.paidAmount())
                    .param("newb", newBalance)
                    .update();
        }

        return new ReceiptResult(complementId, series, folio, uuid, totalPaid);
    }

    private String seriesForPayment() {
        return jdbc.sql("SELECT COALESCE(series_payment, 'P') FROM cfdi_issuer LIMIT 1")
                .query(String.class).optional().orElse("P");
    }

    private String uuidOf(long cfdiId) {
        return jdbc.sql("SELECT uuid FROM cfdi WHERE id = :id")
                .param("id", cfdiId).query(String.class).optional().orElse(null);
    }

    /** Saldo pendiente de un CFDI (total - suma de pagos aplicados). */
    private BigDecimal currentBalance(long cfdiId) {
        BigDecimal total = jdbc.sql("SELECT total FROM cfdi WHERE id = :id")
                .param("id", cfdiId).query(BigDecimal.class).optional().orElse(BigDecimal.ZERO);
        BigDecimal paid = jdbc.sql("SELECT COALESCE(SUM(paid_amount),0) FROM cfdi_payment_complement WHERE cfdi_id = :id AND status = 'STAMPED'")
                .param("id", cfdiId).query(BigDecimal.class).single();
        return total.subtract(paid);
    }

    /** Documento al que se aplica el pago. */
    public record AppliedDoc(long cfdiId, int installment, BigDecimal paidAmount) {
    }

    /** Resultado de generar un recibo de pago. */
    public record ReceiptResult(long complementId, String series, long folio, String uuid, BigDecimal totalPaid) {
    }
}
