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
        // Validación de la referencia: el teléfono de una recarga debe tener 10 dígitos.
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() != 10) {
            throw new IllegalArgumentException("El teléfono debe tener exactamente 10 dígitos.");
        }
        ProviderResult result = provider.recharge(carrier, digits, amount);
        return persist("RECHARGE", carrier, digits, amount, branchId, cashier, result);
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

        // El saldo se descuenta por el COSTO real de la operación (monto - comisión del negocio).
        BigDecimal cost = result.success() ? amount.subtract(commission) : BigDecimal.ZERO;
        if (cost.signum() < 0) {
            cost = BigDecimal.ZERO;
        }

        BigDecimal balanceAfter = currentBalance();
        if (result.success()) {
            BigDecimal available = currentBalance();
            if (available.compareTo(cost) < 0) {
                throw new IllegalStateException(
                        "Saldo insuficiente: disponible " + available + ", requerido " + cost
                        + ". Reporta un abono para reponer tu saldo.");
            }
            balanceAfter = jdbc.sql("""
                    UPDATE payment_balance SET balance = balance - :cost, updated_at = now()
                    WHERE id = 1 RETURNING balance
                    """)
                    .param("cost", cost).query(BigDecimal.class).single();
        }

        long id = jdbc.sql("""
                INSERT INTO payment_operation
                    (op_type, carrier, reference, amount, commission, cost, customer_charge,
                     balance_after, status, provider_folio, error, branch_id, cashier)
                VALUES (:type, :carrier, :ref, :amount, :commission, :cost, :charge,
                        :balance, :status, :folio, :error, :branch, :cashier)
                RETURNING id
                """)
                .param("type", opType)
                .param("carrier", carrier)
                .param("ref", reference)
                .param("amount", amount)
                .param("commission", commission)
                .param("cost", cost)
                .param("charge", amount)
                .param("balance", balanceAfter)
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

    // =====================================================================
    // Saldo prepagado
    // =====================================================================

    /** Saldo prepagado disponible del negocio. */
    public BigDecimal currentBalance() {
        return jdbc.sql("SELECT balance FROM payment_balance WHERE id = 1")
                .query(BigDecimal.class).optional().orElse(BigDecimal.ZERO);
    }

    /** Resumen de saldo: disponible, comisiones ganadas y operaciones del día. */
    public java.util.Map<String, Object> balanceSummary() {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("balance", currentBalance());
        m.put("commissions", totalCommissions());
        m.put("todayOps", jdbc.sql("""
                SELECT COUNT(*) FROM payment_operation
                WHERE status = 'SUCCESS' AND created_at::date = CURRENT_DATE
                """).query(Long.class).single());
        m.put("todayAmount", jdbc.sql("""
                SELECT COALESCE(SUM(amount), 0) FROM payment_operation
                WHERE status = 'SUCCESS' AND created_at::date = CURRENT_DATE
                """).query(BigDecimal.class).single());
        m.put("pendingDeposits", jdbc.sql(
                "SELECT COUNT(*) FROM payment_deposit WHERE status = 'PENDING'")
                .query(Long.class).single());
        return m;
    }

    // =====================================================================
    // Abonos / depósitos
    // =====================================================================

    /** Reporta un depósito bancario (queda PENDING hasta aprobarse). */
    @Transactional
    public long reportDeposit(String posId, String name, String email, String bank, String account,
                              String reference, BigDecimal amount, java.time.LocalDate payDate,
                              String comments, String actor) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El importe del depósito debe ser positivo");
        }
        return jdbc.sql("""
                INSERT INTO payment_deposit
                    (pos_id, name, email, bank, account, reference, amount, pay_date, comments, created_by)
                VALUES (:pos, :name, :email, :bank, :account, :ref, :amount, :date, :comments, :actor)
                RETURNING id
                """)
                .param("pos", posId).param("name", name).param("email", email)
                .param("bank", bank).param("account", account).param("ref", reference)
                .param("amount", amount).param("date", payDate).param("comments", comments)
                .param("actor", actor)
                .query(Long.class).single();
    }

    /** Aprueba un depósito y suma su importe al saldo prepagado. Idempotente por estado. */
    @Transactional
    public BigDecimal approveDeposit(long depositId, String actor) {
        String status = jdbc.sql("SELECT status FROM payment_deposit WHERE id = :id")
                .param("id", depositId).query(String.class).optional()
                .orElseThrow(() -> new IllegalArgumentException("No existe el abono " + depositId));
        if (!"PENDING".equals(status)) {
            throw new IllegalStateException("El abono ya fue revisado");
        }
        BigDecimal amount = jdbc.sql("SELECT amount FROM payment_deposit WHERE id = :id")
                .param("id", depositId).query(BigDecimal.class).single();
        jdbc.sql("""
                UPDATE payment_deposit SET status = 'APPROVED', reviewed_by = :actor, reviewed_at = now()
                WHERE id = :id
                """)
                .param("actor", actor).param("id", depositId).update();
        return jdbc.sql("""
                UPDATE payment_balance SET balance = balance + :amt, updated_at = now()
                WHERE id = 1 RETURNING balance
                """)
                .param("amt", amount).query(BigDecimal.class).single();
    }

    /** Rechaza un depósito reportado. */
    @Transactional
    public void rejectDeposit(long depositId, String actor) {
        jdbc.sql("""
                UPDATE payment_deposit SET status = 'REJECTED', reviewed_by = :actor, reviewed_at = now()
                WHERE id = :id AND status = 'PENDING'
                """)
                .param("actor", actor).param("id", depositId).update();
    }

    /** Lista abonos, opcionalmente por estado. */
    public java.util.List<java.util.Map<String, Object>> listDeposits(String status) {
        return jdbc.sql("""
                SELECT id, pos_id, name, email, bank, account, reference, amount, pay_date,
                       comments, status, reviewed_by, created_at
                FROM payment_deposit
                WHERE (CAST(:st AS varchar) IS NULL OR status = :st)
                ORDER BY created_at DESC
                """)
                .param("st", status)
                .query((rs, n) -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("posId", rs.getString("pos_id"));
                    m.put("name", rs.getString("name"));
                    m.put("email", rs.getString("email"));
                    m.put("bank", rs.getString("bank"));
                    m.put("account", rs.getString("account"));
                    m.put("reference", rs.getString("reference"));
                    m.put("amount", rs.getBigDecimal("amount"));
                    m.put("payDate", rs.getObject("pay_date", java.time.LocalDate.class));
                    m.put("comments", rs.getString("comments"));
                    m.put("status", rs.getString("status"));
                    m.put("reviewedBy", rs.getString("reviewed_by"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();
    }

    // =====================================================================
    // Catálogo de compañías y servicios
    // =====================================================================

    /** Lista el catálogo (compañías de recarga o servicios), opcionalmente por tipo. */
    public java.util.List<java.util.Map<String, Object>> listCatalog(String opType) {
        return jdbc.sql("""
                SELECT id, op_type, category, provider, commission_pct, commission_fixed,
                       min_amount, max_amount, fixed_amounts, reference_label, brand_color, logo_url
                FROM payment_catalog
                WHERE active = TRUE AND (CAST(:t AS varchar) IS NULL OR op_type = :t)
                ORDER BY op_type, sort_order, provider
                """)
                .param("t", opType)
                .query((rs, n) -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("opType", rs.getString("op_type"));
                    m.put("category", rs.getString("category"));
                    m.put("provider", rs.getString("provider"));
                    m.put("commissionPct", rs.getBigDecimal("commission_pct"));
                    m.put("commissionFixed", rs.getBigDecimal("commission_fixed"));
                    m.put("minAmount", rs.getBigDecimal("min_amount"));
                    m.put("maxAmount", rs.getBigDecimal("max_amount"));
                    m.put("fixedAmounts", rs.getString("fixed_amounts"));
                    m.put("referenceLabel", rs.getString("reference_label"));
                    m.put("brandColor", rs.getString("brand_color"));
                    m.put("logoUrl", rs.getString("logo_url"));
                    return m;
                })
                .list();
    }

    // =====================================================================
    // Operaciones realizadas (historial)
    // =====================================================================

    /** Lista operaciones (recargas/pagos) con filtros de tipo y rango de fechas. */
    public java.util.List<java.util.Map<String, Object>> listOperations(
            String opType, java.time.LocalDate from, java.time.LocalDate to, int limit) {
        int max = limit <= 0 ? 100 : Math.min(limit, 500);
        return jdbc.sql("""
                SELECT id, op_type, carrier, category, reference, amount, commission, cost,
                       balance_after, status, provider_folio, cashier, created_at
                FROM payment_operation
                WHERE (CAST(:t AS varchar) IS NULL OR op_type = :t)
                  AND (CAST(:from AS date) IS NULL OR created_at::date >= :from)
                  AND (CAST(:to AS date) IS NULL OR created_at::date <= :to)
                ORDER BY created_at DESC LIMIT :max
                """)
                .param("t", opType).param("from", from).param("to", to).param("max", max)
                .query((rs, n) -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("opType", rs.getString("op_type"));
                    m.put("carrier", rs.getString("carrier"));
                    m.put("category", rs.getString("category"));
                    m.put("reference", rs.getString("reference"));
                    m.put("amount", rs.getBigDecimal("amount"));
                    m.put("commission", rs.getBigDecimal("commission"));
                    m.put("cost", rs.getBigDecimal("cost"));
                    m.put("balanceAfter", rs.getBigDecimal("balance_after"));
                    m.put("status", rs.getString("status"));
                    m.put("folio", rs.getString("provider_folio"));
                    m.put("cashier", rs.getString("cashier"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();
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
