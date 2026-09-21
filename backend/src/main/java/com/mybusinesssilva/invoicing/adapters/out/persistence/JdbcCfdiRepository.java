package com.mybusinesssilva.invoicing.adapters.out.persistence;

import com.mybusinesssilva.invoicing.domain.model.CfdiStatus;
import com.mybusinesssilva.invoicing.domain.port.out.CfdiRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC del repositorio de CFDI, sobre el schema del tenant en curso.
 */
@Repository
public class JdbcCfdiRepository implements CfdiRepository {

    private final JdbcClient jdbc;

    public JdbcCfdiRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long createPending(Long saleId, String kind, String receiverRfc, String receiverName,
                              String receiverZip, String receiverRegime, String cfdiUse,
                              BigDecimal subtotal, BigDecimal tax, BigDecimal total,
                              String idempotencyKey) {
        return jdbc.sql("""
                INSERT INTO cfdi
                    (sale_id, kind, receiver_rfc, receiver_name, receiver_zip, receiver_regime,
                     cfdi_use, subtotal, tax, total, status, idempotency_key)
                VALUES (:sale, :kind, :rfc, :name, :zip, :regime, :use,
                        :subtotal, :tax, :total, 'PENDING', :idem)
                RETURNING id
                """)
                .param("sale", saleId)
                .param("kind", kind)
                .param("rfc", receiverRfc)
                .param("name", receiverName)
                .param("zip", receiverZip)
                .param("regime", receiverRegime)
                .param("use", cfdiUse)
                .param("subtotal", subtotal)
                .param("tax", tax)
                .param("total", total)
                .param("idem", idempotencyKey)
                .query(Long.class)
                .single();
    }

    @Override
    public void markStamped(long cfdiId, String uuid, String xml) {
        jdbc.sql("""
                UPDATE cfdi SET status = 'STAMPED', uuid = :uuid, xml = :xml,
                    stamp_error = NULL, stamped_at = now()
                WHERE id = :id
                """)
                .param("id", cfdiId)
                .param("uuid", uuid)
                .param("xml", xml)
                .update();
    }

    @Override
    public void markError(long cfdiId, String error) {
        jdbc.sql("UPDATE cfdi SET status = 'ERROR', stamp_error = :err WHERE id = :id")
                .param("id", cfdiId)
                .param("err", error)
                .update();
    }

    @Override
    public void markCanceled(long cfdiId) {
        jdbc.sql("UPDATE cfdi SET status = 'CANCELED', canceled_at = now() WHERE id = :id")
                .param("id", cfdiId)
                .update();
    }

    @Override
    public Optional<CfdiStatus> statusOf(long cfdiId) {
        return jdbc.sql("SELECT status FROM cfdi WHERE id = :id")
                .param("id", cfdiId)
                .query(String.class)
                .optional()
                .map(CfdiStatus::valueOf);
    }

    @Override
    public Optional<String> uuidOf(long cfdiId) {
        return jdbc.sql("SELECT uuid FROM cfdi WHERE id = :id")
                .param("id", cfdiId)
                .query(String.class)
                .optional();
    }

    @Override
    public Optional<Long> findIdByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT id FROM cfdi WHERE idempotency_key = :k")
                .param("k", idempotencyKey)
                .query(Long.class)
                .optional();
    }

    @Override
    public long addPaymentComplement(long cfdiId, BigDecimal paidAmount, String paymentForm,
                                     String paymentDate, String uuid) {
        return jdbc.sql("""
                INSERT INTO cfdi_payment_complement
                    (cfdi_id, paid_amount, payment_form, payment_date, uuid, status)
                VALUES (:cfdi, :amount, :form, CAST(:date AS date), :uuid, 'STAMPED')
                RETURNING id
                """)
                .param("cfdi", cfdiId)
                .param("amount", paidAmount)
                .param("form", paymentForm)
                .param("date", paymentDate)
                .param("uuid", uuid)
                .query(Long.class)
                .single();
    }

    @Override
    public Optional<Long> findIdBySaleId(long saleId) {
        return jdbc.sql("SELECT id FROM cfdi WHERE sale_id = :s ORDER BY id DESC LIMIT 1")
                .param("s", saleId)
                .query(Long.class)
                .optional();
    }
}
