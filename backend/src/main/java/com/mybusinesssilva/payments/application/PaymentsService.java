package com.mybusinesssilva.payments.application;

import com.mybusinesssilva.payments.domain.port.out.RechargeProviderPort;
import com.mybusinesssilva.payments.domain.port.out.RechargeProviderPort.ProviderResult;
import java.math.BigDecimal;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de recargas de tiempo aire y pago de servicios.
 *
 * <p>Procesa la operación a través del agregador (vía {@link RechargeProviderPort}) y registra
 * el resultado. Si la operación falla en el agregador, se registra como FAILED y NO se cobra al
 * cliente (el importe no se considera ingreso). Si tiene éxito, se guarda el folio del proveedor
 * y la comisión ganada, para conciliación.
 */
@Service
public class PaymentsService {

    private final JdbcClient jdbc;
    private final RechargeProviderPort provider;

    public PaymentsService(JdbcClient jdbc, RechargeProviderPort provider) {
        this.jdbc = jdbc;
        this.provider = provider;
    }

    /**
     * Vende una recarga de tiempo aire.
     *
     * @return resultado con el estado y (si tuvo éxito) folio y comisión
     */
    @Transactional
    public OperationResult sellRecharge(String carrier, String phone, BigDecimal amount,
                                        Long branchId, String cashier) {
        ProviderResult result = provider.recharge(carrier, phone, amount);
        return persist("RECHARGE", carrier, phone, amount, branchId, cashier, result);
    }

    /**
     * Cobra el pago de un servicio.
     */
    @Transactional
    public OperationResult payService(String biller, String reference, BigDecimal amount,
                                      Long branchId, String cashier) {
        ProviderResult result = provider.payService(biller, reference, amount);
        return persist("SERVICE", biller, reference, amount, branchId, cashier, result);
    }

    private OperationResult persist(String opType, String carrier, String reference,
                                    BigDecimal amount, Long branchId, String cashier,
                                    ProviderResult result) {
        String status = result.success() ? "SUCCESS" : "FAILED";
        BigDecimal commission = result.success() ? result.commission() : BigDecimal.ZERO;

        long id = jdbc.sql("""
                INSERT INTO payment_operation
                    (op_type, carrier, reference, amount, commission, status,
                     provider_folio, error, branch_id, cashier)
                VALUES (:type, :carrier, :ref, :amount, :commission, :status,
                        :folio, :error, :branch, :cashier)
                RETURNING id
                """)
                .param("type", opType)
                .param("carrier", carrier)
                .param("ref", reference)
                .param("amount", amount)
                .param("commission", commission)
                .param("status", status)
                .param("folio", result.folio())
                .param("error", result.error())
                .param("branch", branchId)
                .param("cashier", cashier)
                .query(Long.class)
                .single();

        return new OperationResult(id, status, result.folio(), commission, result.error());
    }

    /** Total de comisiones ganadas (para conciliación/reportes). */
    public BigDecimal totalCommissions() {
        return jdbc.sql("SELECT COALESCE(SUM(commission), 0) FROM payment_operation WHERE status = 'SUCCESS'")
                .query(BigDecimal.class)
                .single();
    }

    /**
     * Resultado de una operación de recarga/servicio.
     *
     * @param operationId id de la operación registrada
     * @param status      SUCCESS o FAILED
     * @param folio       folio del agregador (si éxito)
     * @param commission  comisión ganada (si éxito)
     * @param error       error (si falló)
     */
    public record OperationResult(long operationId, String status, String folio,
                                  BigDecimal commission, String error) {
        public boolean success() {
            return "SUCCESS".equals(status);
        }
    }
}
