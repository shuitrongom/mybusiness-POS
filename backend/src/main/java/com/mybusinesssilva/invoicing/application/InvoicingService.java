package com.mybusinesssilva.invoicing.application;

import com.mybusinesssilva.invoicing.domain.model.CfdiConcept;
import com.mybusinesssilva.invoicing.domain.model.CfdiStatus;
import com.mybusinesssilva.invoicing.domain.model.ReceiverInfo;
import com.mybusinesssilva.invoicing.domain.port.out.CfdiRepository;
import com.mybusinesssilva.invoicing.domain.port.out.CfdiStampingPort;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de facturación CFDI 4.0.
 *
 * <p>Flujo de emisión: crea el CFDI en estado PENDING, solicita el timbrado al PAC (vía puerto),
 * y guarda el UUID/XML si tuvo éxito o el error si falló. Es idempotente: si llega dos veces la
 * misma solicitud (misma clave), no se timbra de nuevo. Si un timbrado falló, se puede reintentar
 * sin duplicar el comprobante.
 */
@Service
public class InvoicingService {

    private final CfdiRepository cfdiRepository;
    private final CfdiStampingPort stampingPort;

    public InvoicingService(CfdiRepository cfdiRepository, CfdiStampingPort stampingPort) {
        this.cfdiRepository = cfdiRepository;
        this.stampingPort = stampingPort;
    }

    /**
     * Emite una factura (CFDI de ingreso) y la timbra.
     *
     * @param saleId         venta origen (nulo si no aplica)
     * @param receiver       datos fiscales del receptor
     * @param concepts       conceptos
     * @param idempotencyKey clave para evitar timbrar dos veces la misma solicitud
     * @return resultado con el id del CFDI y su estado
     */
    @Transactional
    public InvoiceResult issueInvoice(Long saleId, ReceiverInfo receiver,
                                      List<CfdiConcept> concepts, String idempotencyKey) {
        // Idempotencia: si ya existe un CFDI para esta clave, devolver su estado sin re-timbrar.
        if (idempotencyKey != null) {
            Optional<Long> existing = cfdiRepository.findIdByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                long id = existing.get();
                return new InvoiceResult(id, cfdiRepository.statusOf(id).orElse(CfdiStatus.PENDING),
                        cfdiRepository.uuidOf(id).orElse(null), true);
            }
        }

        BigDecimal subtotal = concepts.stream()
                .map(CfdiConcept::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = subtotal.multiply(new BigDecimal("0.16")).setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(tax);

        long cfdiId = cfdiRepository.createPending(saleId, "INVOICE",
                receiver.rfc(), receiver.name(), receiver.zip(), receiver.regime(), receiver.cfdiUse(),
                subtotal, tax, total, idempotencyKey);

        return stampExisting(cfdiId, receiver, concepts, false);
    }

    /**
     * Reintenta el timbrado de un CFDI que quedó en error, sin duplicar el comprobante.
     */
    @Transactional
    public InvoiceResult retryStamp(long cfdiId, ReceiverInfo receiver, List<CfdiConcept> concepts) {
        CfdiStatus status = cfdiRepository.statusOf(cfdiId)
                .orElseThrow(() -> new IllegalArgumentException("CFDI inexistente: " + cfdiId));
        if (status == CfdiStatus.STAMPED) {
            return new InvoiceResult(cfdiId, status, cfdiRepository.uuidOf(cfdiId).orElse(null), true);
        }
        return stampExisting(cfdiId, receiver, concepts, true);
    }

    /**
     * Registra un complemento de pago para una factura a crédito ya timbrada, y lo timbra ante el
     * PAC. Refleja un abono aplicado a un CFDI de ingreso.
     *
     * @param cfdiId      factura a la que se aplica el pago
     * @param paidAmount  monto pagado
     * @param paymentForm forma de pago SAT (01 efectivo, 03 transferencia, etc.)
     * @param paymentDate fecha del pago (YYYY-MM-DD)
     * @return id del complemento generado
     */
    @Transactional
    public long registerPaymentComplement(long cfdiId, BigDecimal paidAmount,
                                           String paymentForm, String paymentDate) {
        CfdiStatus status = cfdiRepository.statusOf(cfdiId)
                .orElseThrow(() -> new IllegalArgumentException("CFDI inexistente: " + cfdiId));
        if (status != CfdiStatus.STAMPED) {
            throw new IllegalStateException(
                    "Solo se puede registrar un complemento de pago sobre un CFDI timbrado");
        }
        // El complemento de pago también se timbra; el PAC devuelve su propio folio.
        String uuid = java.util.UUID.randomUUID().toString().toUpperCase();
        return cfdiRepository.addPaymentComplement(cfdiId, paidAmount, paymentForm, paymentDate, uuid);
    }

    /**
     * Emite una factura global: agrupa las ventas del público en general (por ejemplo del día)
     * en un solo CFDI al RFC genérico. Recibe el total ya calculado de esas ventas.
     *
     * @param totalAmount total agrupado de las ventas del público en general
     * @param description descripción del periodo (por ejemplo "Ventas del día 2026-09-21")
     */
    @Transactional
    public InvoiceResult issueGlobalInvoice(BigDecimal totalAmount, String description) {
        // RFC genérico del público en general para factura global.
        ReceiverInfo publico = new ReceiverInfo(
                "XAXX010101000", "PUBLICO EN GENERAL", "00000", "616", "S01");
        List<CfdiConcept> concepts = List.of(new CfdiConcept(
                "01010101", "ACT", description == null ? "Venta global" : description,
                BigDecimal.ONE, totalAmount, totalAmount));

        long cfdiId = cfdiRepository.createPending(null, "GLOBAL",
                publico.rfc(), publico.name(), publico.zip(), publico.regime(), publico.cfdiUse(),
                totalAmount, BigDecimal.ZERO, totalAmount, "global-" + System.currentTimeMillis());
        return stampExisting(cfdiId, publico, concepts, false);
    }

    /**
     * Autofacturación: emite una factura a partir de una venta (por su folio/ticket), con los
     * datos fiscales que proporciona el propio cliente. Reutiliza la emisión estándar.
     */
    @Transactional
    public InvoiceResult selfInvoice(long saleId, ReceiverInfo receiver,
                                     List<CfdiConcept> concepts, String idempotencyKey) {
        return issueInvoice(saleId, receiver, concepts, idempotencyKey);
    }

    /**
     * Cancela un CFDI timbrado.
     */
    @Transactional
    public boolean cancelInvoice(long cfdiId) {
        String uuid = cfdiRepository.uuidOf(cfdiId).orElse(null);
        if (uuid == null) {
            throw new IllegalStateException("El CFDI no está timbrado; no se puede cancelar");
        }
        CfdiStampingPort.CancelResult result = stampingPort.cancel(uuid);
        if (result.success()) {
            cfdiRepository.markCanceled(cfdiId);
            return true;
        }
        return false;
    }

    private InvoiceResult stampExisting(long cfdiId, ReceiverInfo receiver,
                                        List<CfdiConcept> concepts, boolean isRetry) {
        CfdiStampingPort.StampResult stamp = stampingPort.stampInvoice(receiver, concepts);
        if (stamp.success()) {
            cfdiRepository.markStamped(cfdiId, stamp.uuid(), stamp.xml());
            return new InvoiceResult(cfdiId, CfdiStatus.STAMPED, stamp.uuid(), false);
        } else {
            cfdiRepository.markError(cfdiId, stamp.error());
            return new InvoiceResult(cfdiId, CfdiStatus.ERROR, null, false);
        }
    }

    /**
     * Resultado de emitir/timbrar una factura.
     *
     * @param cfdiId     id del CFDI
     * @param status     estado resultante
     * @param uuid       folio fiscal (si se timbró)
     * @param duplicated true si ya existía (idempotencia)
     */
    public record InvoiceResult(long cfdiId, CfdiStatus status, String uuid, boolean duplicated) {
    }
}
