package com.mybusinesssilva.invoicing.domain.port.out;

import com.mybusinesssilva.invoicing.domain.model.CfdiStatus;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Puerto de salida para la persistencia de CFDI (schema del tenant).
 */
public interface CfdiRepository {

    /**
     * Crea un CFDI en estado PENDING y devuelve su id.
     */
    long createPending(Long saleId, String kind, String receiverRfc, String receiverName,
                       String receiverZip, String receiverRegime, String cfdiUse,
                       BigDecimal subtotal, BigDecimal tax, BigDecimal total,
                       String idempotencyKey);

    /** Marca un CFDI como timbrado con su UUID y XML. */
    void markStamped(long cfdiId, String uuid, String xml);

    /** Marca un CFDI con error de timbrado. */
    void markError(long cfdiId, String error);

    /** Marca un CFDI como cancelado. */
    void markCanceled(long cfdiId);

    Optional<CfdiStatus> statusOf(long cfdiId);

    Optional<String> uuidOf(long cfdiId);

    Optional<Long> findIdByIdempotencyKey(String idempotencyKey);
}
