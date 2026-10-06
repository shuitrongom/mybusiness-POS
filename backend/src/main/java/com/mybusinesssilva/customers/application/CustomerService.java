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
     * Lista los clientes activos del negocio, opcionalmente filtrados por texto (nombre, RFC,
     * teléfono o email). Incluye el crédito disponible para mostrarlo en el punto de venta.
     *
     * @param query texto de búsqueda (puede ser null o vacío para traer todos)
     * @param limit máximo de resultados (si es null o &lt;= 0 se usan 50)
     * @return lista de clientes como mapas listos para serializar a JSON
     */
    public java.util.List<java.util.Map<String, Object>> listCustomers(String query, Integer limit) {
        int max = (limit == null || limit <= 0) ? 50 : Math.min(limit, 500);
        String q = query == null ? "" : query.trim();
        boolean hasQuery = !q.isBlank();
        String like = "%" + q.toLowerCase() + "%";

        return jdbc.sql("""
                SELECT id, name, rfc, phone, email, credit_limit, credit_used,
                       (credit_limit - credit_used) AS credit_available
                FROM customer
                WHERE active = TRUE
                  AND (:hasQuery = FALSE
                       OR lower(name) LIKE :like
                       OR lower(COALESCE(rfc, '')) LIKE :like
                       OR lower(COALESCE(phone, '')) LIKE :like
                       OR lower(COALESCE(email, '')) LIKE :like)
                ORDER BY name
                LIMIT :max
                """)
                .param("hasQuery", hasQuery)
                .param("like", like)
                .param("max", max)
                .query((rs, n) -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("name", rs.getString("name"));
                    m.put("rfc", rs.getString("rfc"));
                    m.put("phone", rs.getString("phone"));
                    m.put("email", rs.getString("email"));
                    m.put("creditLimit", rs.getBigDecimal("credit_limit"));
                    m.put("creditUsed", rs.getBigDecimal("credit_used"));
                    m.put("creditAvailable", rs.getBigDecimal("credit_available"));
                    return m;
                })
                .list();
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
     * Da de alta un cliente con todos sus datos enterprise (fiscales CFDI 4.0, comerciales y
     * direcciones). Persiste el cliente y sus direcciones en la misma transacción.
     *
     * @param c cliente a crear (sin id)
     * @return id del cliente creado
     */
    @Transactional
    public long createCustomerFull(com.mybusinesssilva.customers.domain.model.Customer c) {
        long id = jdbc.sql("""
                INSERT INTO customer
                    (name, legal_name, person_type, rfc, tax_regime, cfdi_use, zip_code,
                     phone, mobile, email, contact_name, salesperson, credit_limit, credit_days,
                     default_price_list, classification, external_code, notes, image_url, active)
                VALUES
                    (:name, :legalName, :personType, :rfc, :taxRegime, :cfdiUse, :zip,
                     :phone, :mobile, :email, :contact, :salesperson, :creditLimit, :creditDays,
                     :priceList, :classification, :externalCode, :notes, :imageUrl, TRUE)
                RETURNING id
                """)
                .param("name", c.name())
                .param("legalName", c.legalName() == null ? c.name() : c.legalName())
                .param("personType", c.personType())
                .param("rfc", c.rfc())
                .param("taxRegime", c.taxRegime())
                .param("cfdiUse", c.cfdiUse())
                .param("zip", c.zipCode())
                .param("phone", c.phone())
                .param("mobile", c.mobile())
                .param("email", c.email())
                .param("contact", c.contactName())
                .param("salesperson", c.salesperson())
                .param("creditLimit", c.creditLimit())
                .param("creditDays", c.creditDays())
                .param("priceList", c.defaultPriceList())
                .param("classification", c.classification())
                .param("externalCode", c.externalCode())
                .param("notes", c.notes())
                .param("imageUrl", c.imageUrl())
                .query(Long.class).single();

        replaceAddresses(id, c.addresses());
        return id;
    }

    /**
     * Actualiza todos los datos de un cliente existente y reemplaza sus direcciones. No toca el
     * crédito utilizado (se maneja por cuentas por cobrar).
     */
    @Transactional
    public void updateCustomer(com.mybusinesssilva.customers.domain.model.Customer c) {
        if (c.id() == null) {
            throw new IllegalArgumentException("El cliente a actualizar requiere id");
        }
        int updated = jdbc.sql("""
                UPDATE customer SET
                    name = :name, legal_name = :legalName, person_type = :personType, rfc = :rfc,
                    tax_regime = :taxRegime, cfdi_use = :cfdiUse, zip_code = :zip,
                    phone = :phone, mobile = :mobile, email = :email, contact_name = :contact,
                    salesperson = :salesperson, credit_limit = :creditLimit, credit_days = :creditDays,
                    default_price_list = :priceList, classification = :classification,
                    external_code = :externalCode, notes = :notes, image_url = :imageUrl,
                    active = :active
                WHERE id = :id
                """)
                .param("id", c.id())
                .param("name", c.name())
                .param("legalName", c.legalName() == null ? c.name() : c.legalName())
                .param("personType", c.personType())
                .param("rfc", c.rfc())
                .param("taxRegime", c.taxRegime())
                .param("cfdiUse", c.cfdiUse())
                .param("zip", c.zipCode())
                .param("phone", c.phone())
                .param("mobile", c.mobile())
                .param("email", c.email())
                .param("contact", c.contactName())
                .param("salesperson", c.salesperson())
                .param("creditLimit", c.creditLimit())
                .param("creditDays", c.creditDays())
                .param("priceList", c.defaultPriceList())
                .param("classification", c.classification())
                .param("externalCode", c.externalCode())
                .param("notes", c.notes())
                .param("imageUrl", c.imageUrl())
                .param("active", c.active())
                .update();
        if (updated == 0) {
            throw new IllegalArgumentException("No existe el cliente con id " + c.id());
        }
        replaceAddresses(c.id(), c.addresses());
    }

    /** Reemplaza todas las direcciones de un cliente por las dadas (borra y reinserta). */
    private void replaceAddresses(long customerId,
                                  java.util.List<com.mybusinesssilva.customers.domain.model.Customer.CustomerAddress> addresses) {
        jdbc.sql("DELETE FROM customer_address WHERE customer_id = :id")
                .param("id", customerId).update();
        if (addresses == null) {
            return;
        }
        for (var a : addresses) {
            jdbc.sql("""
                    INSERT INTO customer_address
                        (customer_id, kind, label, street, ext_number, int_number, neighborhood,
                         city, state, zip_code, country, reference, is_default)
                    VALUES
                        (:cust, :kind, :label, :street, :ext, :int, :neigh,
                         :city, :state, :zip, :country, :ref, :isDefault)
                    """)
                    .param("cust", customerId)
                    .param("kind", a.kind())
                    .param("label", a.label())
                    .param("street", a.street())
                    .param("ext", a.extNumber())
                    .param("int", a.intNumber())
                    .param("neigh", a.neighborhood())
                    .param("city", a.city())
                    .param("state", a.state())
                    .param("zip", a.zipCode())
                    .param("country", a.country())
                    .param("ref", a.reference())
                    .param("isDefault", a.isDefault())
                    .update();
        }
    }

    /**
     * Obtiene el cliente completo por id, incluyendo sus direcciones. Devuelve vacío si no existe.
     */
    public java.util.Optional<com.mybusinesssilva.customers.domain.model.Customer> getCustomer(long id) {
        var customer = jdbc.sql("""
                SELECT id, name, legal_name, person_type, rfc, tax_regime, cfdi_use, zip_code,
                       phone, mobile, email, contact_name, salesperson, credit_limit, credit_used,
                       credit_days, default_price_list, classification, external_code, notes,
                       image_url, active
                FROM customer WHERE id = :id
                """)
                .param("id", id)
                .query((rs, n) -> mapCustomer(rs))
                .optional();

        if (customer.isEmpty()) {
            return java.util.Optional.empty();
        }

        var addresses = jdbc.sql("""
                SELECT id, kind, label, street, ext_number, int_number, neighborhood,
                       city, state, zip_code, country, reference, is_default
                FROM customer_address WHERE customer_id = :id ORDER BY id
                """)
                .param("id", id)
                .query((rs, n) -> new com.mybusinesssilva.customers.domain.model.Customer.CustomerAddress(
                        rs.getLong("id"), rs.getString("kind"), rs.getString("label"),
                        rs.getString("street"), rs.getString("ext_number"), rs.getString("int_number"),
                        rs.getString("neighborhood"), rs.getString("city"), rs.getString("state"),
                        rs.getString("zip_code"), rs.getString("country"), rs.getString("reference"),
                        rs.getBoolean("is_default")))
                .list();

        var base = customer.get();
        return java.util.Optional.of(withAddresses(base, addresses));
    }

    private com.mybusinesssilva.customers.domain.model.Customer withAddresses(
            com.mybusinesssilva.customers.domain.model.Customer base,
            java.util.List<com.mybusinesssilva.customers.domain.model.Customer.CustomerAddress> addresses) {
        return new com.mybusinesssilva.customers.domain.model.Customer(
                base.id(), base.name(), base.legalName(), base.personType(), base.rfc(),
                base.taxRegime(), base.cfdiUse(), base.zipCode(), base.phone(), base.mobile(),
                base.email(), base.contactName(), base.salesperson(), base.creditLimit(),
                base.creditUsed(), base.creditDays(), base.defaultPriceList(), base.classification(),
                base.externalCode(), base.notes(), base.imageUrl(), base.active(), addresses);
    }

    private com.mybusinesssilva.customers.domain.model.Customer mapCustomer(java.sql.ResultSet rs)
            throws java.sql.SQLException {
        return new com.mybusinesssilva.customers.domain.model.Customer(
                rs.getLong("id"), rs.getString("name"), rs.getString("legal_name"),
                rs.getString("person_type"), rs.getString("rfc"), rs.getString("tax_regime"),
                rs.getString("cfdi_use"), rs.getString("zip_code"), rs.getString("phone"),
                rs.getString("mobile"), rs.getString("email"), rs.getString("contact_name"),
                rs.getString("salesperson"), rs.getBigDecimal("credit_limit"),
                rs.getBigDecimal("credit_used"), rs.getInt("credit_days"),
                rs.getInt("default_price_list"), rs.getString("classification"),
                rs.getString("external_code"), rs.getString("notes"), rs.getString("image_url"),
                rs.getBoolean("active"), java.util.List.of());
    }

    /** Desactiva un cliente (borrado lógico para preservar su histórico de ventas y crédito). */
    @Transactional
    public void deactivateCustomer(long id) {
        jdbc.sql("UPDATE customer SET active = FALSE WHERE id = :id").param("id", id).update();
    }

    // ---------------------------------------------------------------------
    // Cobranza (cuentas por cobrar).
    // ---------------------------------------------------------------------

    /**
     * Lista las cuentas por cobrar (cobranza), opcionalmente filtradas por estado (OPEN/PAID) y
     * cliente. Incluye el nombre del cliente y el saldo pendiente (amount - paid).
     */
    public java.util.List<java.util.Map<String, Object>> listReceivables(String status, Long customerId) {
        return jdbc.sql("""
                SELECT ar.id, ar.customer_id, c.name AS customer_name, ar.sale_id, ar.amount,
                       ar.paid, (ar.amount - ar.paid) AS balance, ar.status, ar.due_date,
                       ar.branch_id, ar.created_at
                FROM account_receivable ar
                JOIN customer c ON c.id = ar.customer_id
                WHERE (CAST(:status AS varchar) IS NULL OR ar.status = :status)
                  AND (CAST(:customer AS bigint) IS NULL OR ar.customer_id = :customer)
                ORDER BY ar.status ASC, ar.due_date NULLS LAST, ar.created_at
                """)
                .param("status", status)
                .param("customer", customerId)
                .query((rs, n) -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("customerId", rs.getLong("customer_id"));
                    m.put("customerName", rs.getString("customer_name"));
                    m.put("saleId", rs.getObject("sale_id"));
                    m.put("amount", rs.getBigDecimal("amount"));
                    m.put("paid", rs.getBigDecimal("paid"));
                    m.put("balance", rs.getBigDecimal("balance"));
                    m.put("status", rs.getString("status"));
                    m.put("dueDate", rs.getObject("due_date", java.time.LocalDate.class));
                    m.put("branchId", rs.getObject("branch_id"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();
    }

    /** Resumen de cobranza: total pendiente, cuentas abiertas y cuentas vencidas. */
    public java.util.Map<String, Object> receivablesSummary() {
        return jdbc.sql("""
                SELECT
                    COALESCE(SUM(CASE WHEN status = 'OPEN' THEN amount - paid ELSE 0 END), 0) AS total_pending,
                    COUNT(*) FILTER (WHERE status = 'OPEN') AS open_count,
                    COUNT(*) FILTER (WHERE status = 'OPEN' AND due_date IS NOT NULL AND due_date < CURRENT_DATE) AS overdue_count
                FROM account_receivable
                """)
                .query((rs, n) -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("totalPending", rs.getBigDecimal("total_pending"));
                    m.put("openCount", rs.getInt("open_count"));
                    m.put("overdueCount", rs.getInt("overdue_count"));
                    return m;
                })
                .single();
    }

    // ---------------------------------------------------------------------
    // Anticipos de clientes (dinero entregado por adelantado, a cuenta de pedidos).
    // ---------------------------------------------------------------------

    /** Devuelve el saldo de anticipo del cliente, o cero si no tiene cuenta. */
    public BigDecimal advanceBalance(long customerId) {
        return jdbc.sql("SELECT balance FROM customer_advance WHERE customer_id = :id")
                .param("id", customerId)
                .query(BigDecimal.class)
                .optional()
                .orElse(BigDecimal.ZERO);
    }

    /** Crea la cuenta de anticipo del cliente con saldo cero si aún no existe (idempotente). */
    private void ensureAdvanceAccount(long customerId) {
        jdbc.sql("""
                INSERT INTO customer_advance (customer_id, balance)
                VALUES (:id, 0)
                ON CONFLICT (customer_id) DO NOTHING
                """)
                .param("id", customerId)
                .update();
    }

    /**
     * Registra un depósito de anticipo (el cliente entrega dinero por adelantado). Aumenta el
     * saldo y registra el movimiento DEPOSIT.
     *
     * @return nuevo saldo de anticipo
     */
    @Transactional
    public BigDecimal depositAdvance(long customerId, BigDecimal amount, String method,
                                     String reference, String actor) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El monto del anticipo debe ser positivo");
        }
        ensureAdvanceAccount(customerId);
        BigDecimal newBalance = jdbc.sql("""
                UPDATE customer_advance SET balance = balance + :amt, updated_at = now()
                WHERE customer_id = :id RETURNING balance
                """)
                .param("amt", amount).param("id", customerId)
                .query(BigDecimal.class).single();
        recordAdvanceMovement(customerId, "DEPOSIT", amount, newBalance, method, null, reference, actor);
        return newBalance;
    }

    /**
     * Aplica parte del anticipo a una venta. Valida que haya saldo suficiente, disminuye el saldo
     * y registra el movimiento APPLY.
     *
     * @return nuevo saldo de anticipo
     */
    @Transactional
    public BigDecimal applyAdvance(long customerId, BigDecimal amount, Long saleId,
                                   String reference, String actor) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El monto a aplicar debe ser positivo");
        }
        BigDecimal balance = advanceBalance(customerId);
        if (balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "Saldo de anticipo insuficiente: se intentan aplicar " + amount
                    + " pero el saldo disponible es " + balance);
        }
        BigDecimal newBalance = jdbc.sql("""
                UPDATE customer_advance SET balance = balance - :amt, updated_at = now()
                WHERE customer_id = :id RETURNING balance
                """)
                .param("amt", amount).param("id", customerId)
                .query(BigDecimal.class).single();
        recordAdvanceMovement(customerId, "APPLY", amount, newBalance, null, saleId, reference, actor);
        return newBalance;
    }

    /** Devuelve (reembolsa) saldo de anticipo al cliente. Registra el movimiento REFUND. */
    @Transactional
    public BigDecimal refundAdvance(long customerId, BigDecimal amount, String reference, String actor) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El monto a devolver debe ser positivo");
        }
        BigDecimal balance = advanceBalance(customerId);
        if (balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException(
                    "No hay saldo suficiente para devolver: saldo " + balance);
        }
        BigDecimal newBalance = jdbc.sql("""
                UPDATE customer_advance SET balance = balance - :amt, updated_at = now()
                WHERE customer_id = :id RETURNING balance
                """)
                .param("amt", amount).param("id", customerId)
                .query(BigDecimal.class).single();
        recordAdvanceMovement(customerId, "REFUND", amount, newBalance, null, null, reference, actor);
        return newBalance;
    }

    private void recordAdvanceMovement(long customerId, String direction, BigDecimal amount,
                                       BigDecimal balanceAfter, String method, Long saleId,
                                       String reference, String actor) {
        jdbc.sql("""
                INSERT INTO customer_advance_movement
                    (customer_id, direction, amount, balance_after, method, sale_id, reference, actor)
                VALUES (:cust, :dir, :amt, :balance, :method, :sale, :ref, :actor)
                """)
                .param("cust", customerId).param("dir", direction).param("amt", amount)
                .param("balance", balanceAfter).param("method", method).param("sale", saleId)
                .param("ref", reference).param("actor", actor)
                .update();
    }

    /** Lista los movimientos de anticipo de un cliente (más recientes primero). */
    public java.util.List<java.util.Map<String, Object>> advanceMovements(long customerId, int limit) {
        int max = limit <= 0 ? 50 : Math.min(limit, 200);
        return jdbc.sql("""
                SELECT id, direction, amount, balance_after, method, sale_id, reference, actor, created_at
                FROM customer_advance_movement
                WHERE customer_id = :id
                ORDER BY created_at DESC
                LIMIT :max
                """)
                .param("id", customerId).param("max", max)
                .query((rs, n) -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("direction", rs.getString("direction"));
                    m.put("amount", rs.getBigDecimal("amount"));
                    m.put("balanceAfter", rs.getBigDecimal("balance_after"));
                    m.put("method", rs.getString("method"));
                    m.put("saleId", rs.getObject("sale_id"));
                    m.put("reference", rs.getString("reference"));
                    m.put("actor", rs.getString("actor"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();
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
        return addReceivable(customerId, saleId, amount, null, dueDate);
    }

    /**
     * Genera una cuenta por cobrar para el cliente, registrando la sucursal de origen.
     *
     * @param branchId sucursal donde se generó el crédito (puede ser null)
     */
    @Transactional
    public long addReceivable(long customerId, long saleId, BigDecimal amount, Long branchId,
                              LocalDate dueDate) {
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
                INSERT INTO account_receivable (customer_id, sale_id, amount, branch_id, due_date, status)
                VALUES (:cust, :sale, :amount, :branch, :due, 'OPEN')
                RETURNING id
                """)
                .param("cust", customerId).param("sale", saleId)
                .param("amount", amount).param("branch", branchId).param("due", dueDate)
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
