package com.mybusinesssilva.customers.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso del módulo de clientes/CRM.
 *
 * <p>Cubre el alta de clientes, el manejo de cuentas por cobrar (crédito) y el programa de
 * lealtad (puntos). Todas las operaciones de escritura son transaccionales para mantener la
 * consistencia entre el saldo de crédito del cliente y sus cuentas por cobrar, así como entre
 * el balance de la cuenta de lealtad y su historial de movimientos.
 *
 * <p>Persiste con {@link JdbcClient} sobre el schema del tenant en curso (el {@code search_path}
 * ya está fijado por el DataSource), por lo que las tablas se referencian sin prefijo de schema.
 */
@Service
public class CustomerService {

    private final JdbcClient jdbc;

    public CustomerService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Da de alta un cliente y devuelve su id.
     *
     * @param name        nombre del cliente
     * @param rfc         RFC (puede ser null)
     * @param phone       teléfono (puede ser null)
     * @param email       correo (puede ser null)
     * @param creditLimit límite de crédito autorizado (si es null se asume cero)
     * @return id del cliente creado
     */
    @Transactional
    public long createCustomer(String name, String rfc, String phone, String email,
                               BigDecimal creditLimit) {
        BigDecimal limit = creditLimit == null ? BigDecimal.ZERO : creditLimit;
        return jdbc.sql("""
                INSERT INTO customer (name, rfc, phone, email, credit_limit)
                VALUES (:n, :r, :p, :e, :limit)
                RETURNING id
                """)
                .param("n", name).param("r", rfc).param("p", phone).param("e", email)
                .param("limit", limit)
                .query(Long.class).single();
    }

    /**
     * Genera una cuenta por cobrar para el cliente y aumenta su crédito utilizado.
     *
     * <p>Valida que el crédito utilizado más el nuevo monto no rebase el límite de crédito del
     * cliente; de lo contrario lanza {@link IllegalArgumentException}.
     *
     * @param customerId cliente al que se le otorga el crédito
     * @param saleId     venta que origina la cuenta por cobrar
     * @param amount     monto de la cuenta por cobrar (debe ser positivo)
     * @param dueDate    fecha de vencimiento (puede ser null)
     * @return id de la cuenta por cobrar creada
     */
    @Transactional
    public long addReceivable(long customerId, long saleId, BigDecimal amount, LocalDate dueDate) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El monto de la cuenta por cobrar debe ser positivo");
        }

        BigDecimal creditLimit = jdbc.sql("SELECT credit_limit FROM customer WHERE id = :id")
                .param("id", customerId)
                .query(BigDecimal.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el cliente con id " + customerId));

        BigDecimal creditUsed = jdbc.sql("SELECT credit_used FROM customer WHERE id = :id")
                .param("id", customerId)
                .query(BigDecimal.class)
                .single();

        if (creditUsed.add(amount).compareTo(creditLimit) > 0) {
            throw new IllegalArgumentException(
                    "Límite de crédito excedido: el crédito utilizado ("
                    + creditUsed.add(amount) + ") supera el límite autorizado (" + creditLimit + ")");
        }

        long receivableId = jdbc.sql("""
                INSERT INTO account_receivable (customer_id, sale_id, amount, due_date, status)
                VALUES (:cust, :sale, :amount, :due, 'OPEN')
                RETURNING id
                """)
                .param("cust", customerId).param("sale", saleId)
                .param("amount", amount).param("due", dueDate)
                .query(Long.class).single();

        jdbc.sql("UPDATE customer SET credit_used = credit_used + :amt WHERE id = :id")
                .param("amt", amount).param("id", customerId)
                .update();

        return receivableId;
    }

    /**
     * Abona a una cuenta por cobrar y reduce el crédito utilizado del cliente correspondiente.
     *
     * <p>Suma el monto a lo pagado y, si lo pagado alcanza o supera el monto de la cuenta, la
     * marca como {@code PAID}. El crédito utilizado del cliente se reduce en el monto abonado
     * sin quedar por debajo de cero.
     *
     * @param receivableId cuenta por cobrar a la que se abona
     * @param amount       monto del abono (debe ser positivo)
     */
    @Transactional
    public void payReceivable(long receivableId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El monto del abono debe ser positivo");
        }

        Long customerId = jdbc.sql("SELECT customer_id FROM account_receivable WHERE id = :id")
                .param("id", receivableId)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe la cuenta por cobrar con id " + receivableId));

        jdbc.sql("""
                UPDATE account_receivable
                SET paid = paid + :amt,
                    status = CASE WHEN paid + :amt >= amount THEN 'PAID' ELSE 'OPEN' END
                WHERE id = :id
                """)
                .param("amt", amount).param("id", receivableId)
                .update();

        // Reduce el crédito utilizado sin bajar de cero.
        jdbc.sql("""
                UPDATE customer
                SET credit_used = GREATEST(credit_used - :amt, 0)
                WHERE id = :id
                """)
                .param("amt", amount).param("id", customerId)
                .update();
    }

    /**
     * Acumula puntos de lealtad para el cliente. Crea la cuenta de lealtad con balance cero si
     * aún no existe, suma el monto al balance y registra el movimiento {@code EARN}.
     *
     * @param customerId cliente
     * @param amount     puntos a acumular (debe ser positivo)
     * @param reference  referencia del movimiento (por ejemplo la venta que lo origina)
     * @return nuevo balance de puntos
     */
    @Transactional
    public BigDecimal earnLoyalty(long customerId, BigDecimal amount, String reference) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Los puntos a acumular deben ser positivos");
        }

        ensureLoyaltyAccount(customerId);

        BigDecimal newBalance = jdbc.sql("""
                UPDATE loyalty_account
                SET balance = balance + :amt, updated_at = now()
                WHERE customer_id = :id
                RETURNING balance
                """)
                .param("amt", amount).param("id", customerId)
                .query(BigDecimal.class).single();

        recordLoyaltyMovement(customerId, "EARN", amount, newBalance, reference);
        return newBalance;
    }

    /**
     * Canjea puntos de lealtad del cliente. Valida que exista saldo suficiente, resta el monto
     * del balance y registra el movimiento {@code REDEEM}.
     *
     * @param customerId cliente
     * @param amount     puntos a canjear (debe ser positivo)
     * @param reference  referencia del movimiento
     * @return nuevo balance de puntos
     */
    @Transactional
    public BigDecimal redeemLoyalty(long customerId, BigDecimal amount, String reference) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Los puntos a canjear deben ser positivos");
        }

        BigDecimal balance = loyaltyBalance(customerId);
        if (balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "Saldo de lealtad insuficiente: se intentan canjear " + amount
                    + " puntos pero el saldo disponible es " + balance);
        }

        BigDecimal newBalance = jdbc.sql("""
                UPDATE loyalty_account
                SET balance = balance - :amt, updated_at = now()
                WHERE customer_id = :id
                RETURNING balance
                """)
                .param("amt", amount).param("id", customerId)
                .query(BigDecimal.class).single();

        recordLoyaltyMovement(customerId, "REDEEM", amount, newBalance, reference);
        return newBalance;
    }

    /**
     * Devuelve el balance de puntos de lealtad del cliente, o cero si no tiene cuenta.
     *
     * @param customerId cliente
     * @return balance de puntos (nunca null)
     */
    public BigDecimal loyaltyBalance(long customerId) {
        return jdbc.sql("SELECT balance FROM loyalty_account WHERE customer_id = :id")
                .param("id", customerId)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    /**
     * Devuelve el crédito utilizado por el cliente.
     *
     * @param customerId cliente
     * @return crédito utilizado
     */
    public BigDecimal creditUsed(long customerId) {
        return jdbc.sql("SELECT credit_used FROM customer WHERE id = :id")
                .param("id", customerId)
                .query(BigDecimal.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el cliente con id " + customerId));
    }

    /**
     * Devuelve el límite de crédito autorizado del cliente.
     *
     * @param customerId cliente
     * @return límite de crédito
     */
    public BigDecimal creditLimit(long customerId) {
        return jdbc.sql("SELECT credit_limit FROM customer WHERE id = :id")
                .param("id", customerId)
                .query(BigDecimal.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el cliente con id " + customerId));
    }

    /**
     * Devuelve el crédito disponible del cliente (límite menos utilizado).
     *
     * @param customerId cliente
     * @return crédito disponible
     */
    public BigDecimal availableCredit(long customerId) {
        return creditLimit(customerId).subtract(creditUsed(customerId));
    }

    /**
     * Crea la cuenta de lealtad del cliente con balance cero si aún no existe. Es idempotente
     * gracias al UNIQUE sobre {@code customer_id}.
     */
    private void ensureLoyaltyAccount(long customerId) {
        jdbc.sql("""
                INSERT INTO loyalty_account (customer_id, balance)
                VALUES (:id, 0)
                ON CONFLICT (customer_id) DO NOTHING
                """)
                .param("id", customerId)
                .update();
    }

    /**
     * Registra un renglón en el historial de movimientos de lealtad.
     *
     * @param customerId   cliente
     * @param direction    sentido del movimiento ({@code EARN} o {@code REDEEM})
     * @param amount       puntos del movimiento
     * @param balanceAfter balance resultante tras el movimiento
     * @param reference    referencia del movimiento
     */
    private void recordLoyaltyMovement(long customerId, String direction, BigDecimal amount,
                                       BigDecimal balanceAfter, String reference) {
        jdbc.sql("""
                INSERT INTO loyalty_movement
                    (customer_id, direction, amount, balance_after, reference)
                VALUES (:cust, :dir, :amt, :balance, :ref)
                """)
                .param("cust", customerId).param("dir", direction).param("amt", amount)
                .param("balance", balanceAfter).param("ref", reference)
                .update();
    }
}
