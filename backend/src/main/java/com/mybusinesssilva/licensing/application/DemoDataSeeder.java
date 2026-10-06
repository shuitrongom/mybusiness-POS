package com.mybusinesssilva.licensing.application;

import com.mybusinesssilva.platform.tenancy.TenantContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Siembra datos de DEMOSTRACIÓN realistas y coherentes en el schema de un negocio, para que el
 * dueño vea el sistema "vivo" en todos los módulos: sucursales, usuarios, inventario, compras,
 * clientes con monedero, ventas de los últimos días (con turnos, pagos y kardex), cortes de caja,
 * recargas / pago de servicios y facturación CFDI.
 *
 * <p>Diseño de raíz:
 * <ul>
 *   <li><b>RLS-safe</b>: fija {@link TenantContext} al schema antes de tocar tablas del tenant y lo
 *       restaura en {@code finally} (mismo patrón que {@link BusinessOwnerProvisioner}).</li>
 *   <li><b>Idempotente</b>: si el negocio ya tiene ventas, no vuelve a sembrar.</li>
 *   <li><b>Coherente</b>: las compras aumentan inventario y las ventas lo descuentan, dejando el
 *       kardex ({@code inventory_movement}) cuadrado con la existencia final.</li>
 * </ul>
 *
 * <p>No corre solo: lo invoca un endpoint/servicio de licenciamiento a petición del Super Admin.
 */
@Component
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** IVA general de México, usado para desglosar impuestos en ventas facturables. */
    private static final BigDecimal IVA = new BigDecimal("0.16");

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(JdbcClient jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Siembra los datos demo en el schema del tenant indicado.
     *
     * @param schema schema del negocio (por ejemplo {@code tenant_12})
     * @return resumen de lo sembrado; si ya existían ventas, devuelve un resumen vacío
     */
    public DemoSummary seed(String schema) {
        String previousTenant = TenantContext.getTenantId();
        TenantContext.setTenantId(schema);
        try {
            long sales = jdbc.sql("SELECT count(*) FROM sale").query(Long.class).single();
            if (sales > 0) {
                log.info("El negocio {} ya tiene ventas; se omite la siembra demo.", schema);
                return DemoSummary.empty();
            }

            List<Long> branchIds = seedBranches();
            seedUsers();
            List<Product> products = priceAndStockProducts(branchIds);
            if (products.isEmpty()) {
                log.warn("El negocio {} no tiene productos en el catálogo; no se puede sembrar demo.", schema);
                return DemoSummary.empty();
            }
            int purchases = seedPurchases(branchIds, products);
            List<Long> customerIds = seedCustomers();
            SalesResult salesResult = seedSalesAndShifts(branchIds, products, customerIds);
            int operations = seedPaymentOperations(branchIds.get(0));
            int invoices = seedInvoices(salesResult.invoiceableSaleIds());

            DemoSummary summary = new DemoSummary(
                    branchIds.size(), products.size(), purchases, customerIds.size(),
                    salesResult.saleCount(), salesResult.shiftCount(), operations, invoices);
            log.info("Datos demo sembrados en {}: {}", schema, summary);
            return summary;
        } finally {
            if (previousTenant != null) {
                TenantContext.setTenantId(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    // ---------------------------------------------------------------- Sucursales

    /** Crea (si faltan) las sucursales demo y devuelve sus ids. Reutiliza la Matriz existente. */
    private List<Long> seedBranches() {
        List<Long> ids = new ArrayList<>();
        Long matriz = jdbc.sql("SELECT id FROM branch ORDER BY id LIMIT 1")
                .query(Long.class).optional().orElse(null);
        if (matriz == null) {
            matriz = jdbc.sql("INSERT INTO branch (name, code, active) VALUES ('Matriz','MATRIZ',TRUE) RETURNING id")
                    .query(Long.class).single();
        }
        ids.add(matriz);

        Long norte = jdbc.sql("INSERT INTO branch (name, code, active) VALUES ('Sucursal Norte','NORTE',TRUE) RETURNING id")
                .query(Long.class).single();
        ids.add(norte);

        // Cada sucursal necesita al menos una caja registradora.
        for (Long branchId : ids) {
            long registers = jdbc.sql("SELECT count(*) FROM cash_register WHERE branch_id = :b")
                    .param("b", branchId).query(Long.class).single();
            if (registers == 0) {
                jdbc.sql("INSERT INTO cash_register (branch_id, name, active) VALUES (:b,'Caja 1',TRUE)")
                        .param("b", branchId).update();
            }
        }
        return ids;
    }

    // ---------------------------------------------------------------- Usuarios

    /** Crea usuarios operativos demo (admin, supervisor, cajeros) con su rol asignado. */
    private void seedUsers() {
        createUserIfAbsent("admin@demo.com", "Administrador Demo", "ADMIN");
        createUserIfAbsent("supervisor@demo.com", "Supervisor Demo", "SUPERVISOR");
        createUserIfAbsent("cajero1@demo.com", "María López", "CASHIER");
        createUserIfAbsent("cajero2@demo.com", "Juan Pérez", "CASHIER");
    }

    private void createUserIfAbsent(String email, String fullName, String roleCode) {
        long exists = jdbc.sql("SELECT count(*) FROM app_user WHERE email = :e")
                .param("e", email).query(Long.class).single();
        if (exists > 0) {
            return;
        }
        Long roleId = jdbc.sql("SELECT id FROM role WHERE code = :c")
                .param("c", roleCode).query(Long.class).optional().orElse(null);
        jdbc.sql("""
                INSERT INTO app_user (email, password_hash, full_name, role_id, active)
                VALUES (:email, :hash, :name, :role, TRUE)
                """)
                .param("email", email)
                .param("hash", passwordEncoder.encode("Demo1234!"))
                .param("name", fullName)
                .param("role", roleId)
                .update();
    }

    // ---------------------------------------------------------------- Productos: precio, costo y stock

    /**
     * Asigna precio y costo realistas a los productos que aún no los tienen (precio 0) y crea la
     * existencia inicial en cada sucursal. Devuelve la lista de productos ya con precio para usarlos
     * en compras y ventas.
     */
    private List<Product> priceAndStockProducts(List<Long> branchIds) {
        List<Product> products = jdbc.sql(
                "SELECT id, name, unit, sold_by_weight, price, cost FROM product WHERE active = TRUE ORDER BY id")
                .query((rs, n) -> new Product(
                        rs.getLong("id"), rs.getString("name"), rs.getString("unit"),
                        rs.getBoolean("sold_by_weight"),
                        rs.getBigDecimal("price"), rs.getBigDecimal("cost")))
                .list();

        List<Product> priced = new ArrayList<>();
        for (Product p : products) {
            BigDecimal price = p.price();
            BigDecimal cost = p.cost();
            if (price == null || price.signum() == 0) {
                // Precio de venta demo entre $8 y $180; costo ~65% del precio.
                double base = p.soldByWeight()
                        ? ThreadLocalRandom.current().nextDouble(25, 220)
                        : ThreadLocalRandom.current().nextDouble(8, 180);
                price = money(base);
                cost = money(base * 0.65);
                jdbc.sql("UPDATE product SET price = :p, cost = :c, updated_at = now() WHERE id = :id")
                        .param("p", price).param("c", cost).param("id", p.id())
                        .update();
            }
            Product pricedProduct = new Product(p.id(), p.name(), p.unit(), p.soldByWeight(), price, cost);
            priced.add(pricedProduct);

            // Existencia inicial por sucursal.
            for (Long branchId : branchIds) {
                double qty = p.soldByWeight()
                        ? ThreadLocalRandom.current().nextDouble(15, 120)
                        : ThreadLocalRandom.current().nextInt(20, 200);
                BigDecimal quantity = qty3(qty);
                jdbc.sql("""
                        INSERT INTO inventory_stock (product_id, branch_id, quantity, min_quantity, updated_at)
                        VALUES (:pid, :bid, :qty, :minq, now())
                        ON CONFLICT (product_id, branch_id) DO UPDATE SET quantity = EXCLUDED.quantity
                        """)
                        .param("pid", p.id()).param("bid", branchId)
                        .param("qty", quantity).param("minq", qty3(p.soldByWeight() ? 10 : 15))
                        .update();
                // Movimiento de kardex inicial (ajuste de inventario de apertura).
                jdbc.sql("""
                        INSERT INTO inventory_movement
                            (product_id, branch_id, movement_type, quantity, balance_after, reason, actor, created_at)
                        VALUES (:pid, :bid, 'ADJUSTMENT', :qty, :qty, 'Inventario inicial demo', 'sistema', now())
                        """)
                        .param("pid", p.id()).param("bid", branchId).param("qty", quantity)
                        .update();
            }
        }
        return priced;
    }

    // ---------------------------------------------------------------- Compras

    /** Crea proveedores y algunas compras recibidas que aumentan el inventario (kardex PURCHASE). */
    private int seedPurchases(List<Long> branchIds, List<Product> products) {
        List<String> supplierNames = List.of(
                "Distribuidora del Centro", "Abastos La Merced", "Proveedora Nacional", "Insumos del Norte");
        List<Long> supplierIds = new ArrayList<>();
        for (String name : supplierNames) {
            Long id = jdbc.sql("""
                    INSERT INTO supplier (name, rfc, phone, email, active)
                    VALUES (:n, :rfc, :tel, :mail, TRUE) RETURNING id
                    """)
                    .param("n", name)
                    .param("rfc", "XAXX010101000")
                    .param("tel", randomPhone())
                    .param("mail", "ventas@" + slug(name) + ".mx")
                    .query(Long.class).single();
            supplierIds.add(id);
        }

        int purchaseCount = 0;
        Long branchId = branchIds.get(0);
        for (int i = 0; i < 8; i++) {
            Long supplierId = supplierIds.get(ThreadLocalRandom.current().nextInt(supplierIds.size()));
            boolean onCredit = i % 3 == 0;
            OffsetDateTime when = daysAgo(ThreadLocalRandom.current().nextInt(5, 40));

            Long purchaseId = jdbc.sql("""
                    INSERT INTO purchase (supplier_id, branch_id, invoice_ref, total, on_credit, status, created_at)
                    VALUES (:sid, :bid, :ref, 0, :credit, 'RECEIVED', :ts) RETURNING id
                    """)
                    .param("sid", supplierId).param("bid", branchId)
                    .param("ref", "FAC-" + (1000 + i))
                    .param("credit", onCredit).param("ts", when)
                    .query(Long.class).single();

            BigDecimal total = BigDecimal.ZERO;
            int lines = ThreadLocalRandom.current().nextInt(3, 8);
            for (int l = 0; l < lines; l++) {
                Product p = products.get(ThreadLocalRandom.current().nextInt(products.size()));
                BigDecimal qty = qty3(ThreadLocalRandom.current().nextInt(10, 60));
                BigDecimal unitCost = p.cost() != null && p.cost().signum() > 0 ? p.cost() : money(10);
                BigDecimal lineTotal = unitCost.multiply(qty).setScale(2, RoundingMode.HALF_UP);
                total = total.add(lineTotal);

                jdbc.sql("""
                        INSERT INTO purchase_line (purchase_id, product_id, quantity, unit_cost, line_total)
                        VALUES (:pur, :prod, :qty, :cost, :lt)
                        """)
                        .param("pur", purchaseId).param("prod", p.id())
                        .param("qty", qty).param("cost", unitCost).param("lt", lineTotal)
                        .update();

                applyStock(p.id(), branchId, qty, "PURCHASE", "Compra FAC-" + (1000 + i), when);
            }
            jdbc.sql("UPDATE purchase SET total = :t WHERE id = :id")
                    .param("t", total).param("id", purchaseId).update();

            if (onCredit) {
                jdbc.sql("""
                        INSERT INTO account_payable (supplier_id, purchase_id, amount, paid, status, due_date, created_at)
                        VALUES (:sid, :pid, :amt, 0, 'OPEN', :due, :ts)
                        """)
                        .param("sid", supplierId).param("pid", purchaseId)
                        .param("amt", total).param("due", when.plusDays(30).toLocalDate())
                        .param("ts", when).update();
            }
            purchaseCount++;
        }
        return purchaseCount;
    }

    // ---------------------------------------------------------------- Clientes

    /** Crea clientes demo, algunos con crédito y monedero de lealtad con saldo. */
    private List<Long> seedCustomers() {
        List<String> names = List.of(
                "Público en general", "Ana Ramírez", "Carlos Hernández", "Tienda Doña Lupe",
                "Restaurante El Sazón", "José Martínez", "Farmacia San Rafael", "Guadalupe Torres");
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            boolean withCredit = i % 3 == 1;
            BigDecimal creditLimit = withCredit ? money(ThreadLocalRandom.current().nextInt(2000, 10000)) : BigDecimal.ZERO;
            Long id = jdbc.sql("""
                    INSERT INTO customer (name, rfc, phone, email, credit_limit, credit_used, active)
                    VALUES (:n, :rfc, :tel, :mail, :cl, 0, TRUE) RETURNING id
                    """)
                    .param("n", name)
                    .param("rfc", i == 0 ? null : "XAXX010101000")
                    .param("tel", i == 0 ? null : randomPhone())
                    .param("mail", i == 0 ? null : slug(name) + "@correo.mx")
                    .param("cl", creditLimit)
                    .query(Long.class).single();
            ids.add(id);

            // Monedero de lealtad con saldo para algunos clientes (no el público general).
            if (i > 0 && i % 2 == 0) {
                BigDecimal balance = money(ThreadLocalRandom.current().nextInt(50, 800));
                jdbc.sql("INSERT INTO loyalty_account (customer_id, balance, updated_at) VALUES (:c, :b, now())")
                        .param("c", id).param("b", balance).update();
                jdbc.sql("""
                        INSERT INTO loyalty_movement (customer_id, direction, amount, balance_after, reference, created_at)
                        VALUES (:c, 'EARN', :a, :b, 'Acumulación demo', now())
                        """)
                        .param("c", id).param("a", balance).param("b", balance).update();
            }
        }
        return ids;
    }

    // ---------------------------------------------------------------- Ventas + turnos + cortes

    /**
     * Crea turnos de caja de los últimos días con sus ventas (líneas, pagos, kardex SALE) y sus
     * movimientos de efectivo, y cierra los turnos con su arqueo. Devuelve el conteo y las ventas
     * candidatas a factura.
     */
    private SalesResult seedSalesAndShifts(List<Long> branchIds, List<Product> products, List<Long> customerIds) {
        Long branchId = branchIds.get(0);
        Long registerId = jdbc.sql("SELECT id FROM cash_register WHERE branch_id = :b ORDER BY id LIMIT 1")
                .param("b", branchId).query(Long.class).single();

        List<String> cashiers = List.of("María López", "Juan Pérez");
        List<Long> invoiceableSaleIds = new ArrayList<>();
        int saleCount = 0;
        int shiftCount = 0;

        // Un turno por día durante los últimos 13 días, incluyendo HOY (day=0), para que el
        // resumen del día en el panel muestre datos y no ceros.
        for (int day = 12; day >= 0; day--) {
            OffsetDateTime openedAt = daysAgo(day).withHour(8).withMinute(0);
            String cashier = cashiers.get(Math.abs(day) % cashiers.size());
            BigDecimal openingFloat = money(1000);

            Long shiftId = jdbc.sql("""
                    INSERT INTO shift (cash_register_id, opened_by, opening_float, opened_at, status)
                    VALUES (:reg, :by, :float, :ts, 'OPEN') RETURNING id
                    """)
                    .param("reg", registerId).param("by", cashier)
                    .param("float", openingFloat).param("ts", openedAt)
                    .query(Long.class).single();
            shiftCount++;

            BigDecimal cashCollected = BigDecimal.ZERO;
            int salesInShift = ThreadLocalRandom.current().nextInt(6, 16);
            for (int s = 0; s < salesInShift; s++) {
                OffsetDateTime saleTime = openedAt.plusMinutes(ThreadLocalRandom.current().nextInt(30, 540));
                SaleDraft draft = buildSale(products);
                Long customerId = customerIds.get(ThreadLocalRandom.current().nextInt(customerIds.size()));

                Long saleId = jdbc.sql("""
                        INSERT INTO sale
                            (folio, branch_id, cash_register_id, shift_id, cashier, customer_id,
                             subtotal, discount, tax, total, status, idempotency_key, created_at)
                        VALUES (:folio, :bid, :reg, :shift, :cashier, :cust,
                                :sub, :disc, :tax, :total, 'COMPLETED', :idem, :ts)
                        RETURNING id
                        """)
                        .param("folio", "V-" + day + "-" + (s + 1))
                        .param("bid", branchId).param("reg", registerId).param("shift", shiftId)
                        .param("cashier", cashier).param("cust", customerId)
                        .param("sub", draft.subtotal()).param("disc", draft.discount())
                        .param("tax", draft.tax()).param("total", draft.total())
                        .param("idem", "demo-" + shiftId + "-" + s)
                        .param("ts", saleTime)
                        .query(Long.class).single();
                saleCount++;

                for (SaleLine line : draft.lines()) {
                    jdbc.sql("""
                            INSERT INTO sale_line
                                (sale_id, product_id, description, quantity, unit_price, discount, line_total)
                            VALUES (:sid, :pid, :desc, :qty, :price, 0, :lt)
                            """)
                            .param("sid", saleId).param("pid", line.productId())
                            .param("desc", line.description()).param("qty", line.quantity())
                            .param("price", line.unitPrice()).param("lt", line.lineTotal())
                            .update();
                    applyStock(line.productId(), branchId, line.quantity().negate(),
                            "SALE", "Venta V-" + day + "-" + (s + 1), saleTime);
                }

                // Método de pago: mayoría efectivo, algunas tarjeta/transferencia.
                String method = switch (ThreadLocalRandom.current().nextInt(10)) {
                    case 0, 1, 2 -> "CARD";
                    case 3 -> "TRANSFER";
                    default -> "CASH";
                };
                jdbc.sql("INSERT INTO sale_payment (sale_id, method, amount) VALUES (:sid, :m, :amt)")
                        .param("sid", saleId).param("m", method).param("amt", draft.total())
                        .update();
                if ("CASH".equals(method)) {
                    cashCollected = cashCollected.add(draft.total());
                }

                // Algunas ventas grandes se marcan como candidatas a factura.
                if (draft.total().compareTo(money(300)) > 0 && invoiceableSaleIds.size() < 15) {
                    invoiceableSaleIds.add(saleId);
                }
            }

            // Un retiro de efectivo a media jornada.
            BigDecimal withdrawal = money(500);
            jdbc.sql("""
                    INSERT INTO cash_movement (shift_id, direction, amount, reason, actor, created_at)
                    VALUES (:sid, 'OUT', :amt, 'Retiro parcial a caja fuerte', :actor, :ts)
                    """)
                    .param("sid", shiftId).param("amt", withdrawal)
                    .param("actor", cashier).param("ts", openedAt.plusHours(5))
                    .update();

            // Cierre del turno con arqueo (efectivo esperado = fondo + efectivo - retiro).
            OffsetDateTime closedAt = openedAt.plusHours(9);
            BigDecimal expectedCash = openingFloat.add(cashCollected).subtract(withdrawal);
            // Pequeña diferencia de arqueo aleatoria para realismo (+/- $20).
            BigDecimal counted = expectedCash.add(money(ThreadLocalRandom.current().nextInt(-20, 21)));
            jdbc.sql("""
                    UPDATE shift SET closed_by = :by, counted_cash = :counted, closed_at = :ts, status = 'CLOSED'
                    WHERE id = :id
                    """)
                    .param("by", cashier).param("counted", counted).param("ts", closedAt).param("id", shiftId)
                    .update();
        }
        return new SalesResult(saleCount, shiftCount, invoiceableSaleIds);
    }

    /** Construye una venta demo con 1-5 renglones a partir de productos con precio. */
    private SaleDraft buildSale(List<Product> products) {
        int lineCount = ThreadLocalRandom.current().nextInt(1, 6);
        List<SaleLine> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        for (int i = 0; i < lineCount; i++) {
            Product p = products.get(ThreadLocalRandom.current().nextInt(products.size()));
            BigDecimal qty = p.soldByWeight()
                    ? qty3(ThreadLocalRandom.current().nextDouble(0.25, 3.5))
                    : qty3(ThreadLocalRandom.current().nextInt(1, 6));
            BigDecimal price = p.price() != null && p.price().signum() > 0 ? p.price() : money(15);
            BigDecimal lineTotal = price.multiply(qty).setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(lineTotal);
            lines.add(new SaleLine(p.id(), p.name(), qty, price, lineTotal));
        }
        // El precio ya incluye IVA (precio al público); desglosamos para el campo tax.
        BigDecimal total = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal base = total.divide(BigDecimal.ONE.add(IVA), 2, RoundingMode.HALF_UP);
        BigDecimal tax = total.subtract(base);
        return new SaleDraft(base, BigDecimal.ZERO, tax, total, lines);
    }

    // ---------------------------------------------------------------- Recargas y pago de servicios

    /** Crea operaciones demo de tiempo aire y pago de servicios con su comisión. */
    private int seedPaymentOperations(Long branchId) {
        record Op(String type, String carrier, String ref) {}
        List<Op> catalog = List.of(
                new Op("RECHARGE", "Telcel", randomPhone()),
                new Op("RECHARGE", "Movistar", randomPhone()),
                new Op("RECHARGE", "AT&T", randomPhone()),
                new Op("RECHARGE", "Bait", randomPhone()),
                new Op("SERVICE", "CFE", "RECIBO-" + ThreadLocalRandom.current().nextInt(100000, 999999)),
                new Op("SERVICE", "Telmex", "RECIBO-" + ThreadLocalRandom.current().nextInt(100000, 999999)),
                new Op("SERVICE", "Agua SIAPA", "RECIBO-" + ThreadLocalRandom.current().nextInt(100000, 999999)),
                new Op("SERVICE", "Gas Natural", "RECIBO-" + ThreadLocalRandom.current().nextInt(100000, 999999)));

        int count = 0;
        for (int i = 0; i < 20; i++) {
            Op op = catalog.get(ThreadLocalRandom.current().nextInt(catalog.size()));
            BigDecimal amount = op.type().equals("RECHARGE")
                    ? money(new int[]{20, 30, 50, 100, 150, 200}[ThreadLocalRandom.current().nextInt(6)])
                    : money(ThreadLocalRandom.current().nextInt(150, 1200));
            // Comisión: 3% recargas, 2% servicios (redondeada).
            BigDecimal commission = amount.multiply(op.type().equals("RECHARGE") ? new BigDecimal("0.03") : new BigDecimal("0.02"))
                    .setScale(2, RoundingMode.HALF_UP);
            jdbc.sql("""
                    INSERT INTO payment_operation
                        (op_type, carrier, reference, amount, commission, status, provider_folio, branch_id, cashier, created_at)
                    VALUES (:type, :carrier, :ref, :amt, :comm, 'SUCCESS', :folio, :bid, 'María López', :ts)
                    """)
                    .param("type", op.type()).param("carrier", op.carrier()).param("ref", op.ref())
                    .param("amt", amount).param("comm", commission)
                    .param("folio", "OP" + ThreadLocalRandom.current().nextInt(1000000, 9999999))
                    .param("bid", branchId)
                    .param("ts", daysAgo(ThreadLocalRandom.current().nextInt(1, 12)))
                    .update();
            count++;
        }
        return count;
    }

    // ---------------------------------------------------------------- Facturación CFDI

    /** Genera facturas CFDI timbradas (demo) para algunas ventas grandes. */
    private int seedInvoices(List<Long> saleIds) {
        int count = 0;
        for (Long saleId : saleIds) {
            var sale = jdbc.sql("SELECT subtotal, tax, total, created_at FROM sale WHERE id = :id")
                    .param("id", saleId)
                    .query((rs, n) -> new Object[]{
                            rs.getBigDecimal("subtotal"), rs.getBigDecimal("tax"),
                            rs.getBigDecimal("total"), rs.getObject("created_at", OffsetDateTime.class)})
                    .optional().orElse(null);
            if (sale == null) {
                continue;
            }
            BigDecimal subtotal = (BigDecimal) sale[0];
            BigDecimal tax = (BigDecimal) sale[1];
            BigDecimal total = (BigDecimal) sale[2];
            OffsetDateTime when = (OffsetDateTime) sale[3];

            jdbc.sql("""
                    INSERT INTO cfdi
                        (sale_id, kind, receiver_rfc, receiver_name, receiver_zip, receiver_regime,
                         cfdi_use, subtotal, tax, total, status, uuid, idempotency_key, created_at, stamped_at)
                    VALUES (:sid, 'INVOICE', :rfc, :name, :zip, '601', 'G03',
                            :sub, :tax, :total, 'STAMPED', :uuid, :idem, :ts, :ts)
                    """)
                    .param("sid", saleId)
                    .param("rfc", "XAXX010101000")
                    .param("name", "PUBLICO EN GENERAL")
                    .param("zip", "44100")
                    .param("sub", subtotal).param("tax", tax).param("total", total)
                    .param("uuid", java.util.UUID.randomUUID().toString().toUpperCase())
                    .param("idem", "cfdi-demo-" + saleId)
                    .param("ts", when)
                    .update();
            count++;
        }
        return count;
    }

    // ---------------------------------------------------------------- Utilidades de inventario

    /**
     * Aplica un delta de existencia a un producto en una sucursal y registra el kardex.
     * Un delta positivo entra (compra/ajuste), negativo sale (venta).
     */
    private void applyStock(Long productId, Long branchId, BigDecimal delta,
                            String movementType, String reference, OffsetDateTime when) {
        BigDecimal balance = jdbc.sql("""
                INSERT INTO inventory_stock (product_id, branch_id, quantity, min_quantity, updated_at)
                VALUES (:pid, :bid, :delta, 0, now())
                ON CONFLICT (product_id, branch_id)
                DO UPDATE SET quantity = inventory_stock.quantity + EXCLUDED.quantity, updated_at = now()
                RETURNING quantity
                """)
                .param("pid", productId).param("bid", branchId).param("delta", delta)
                .query(BigDecimal.class).single();

        jdbc.sql("""
                INSERT INTO inventory_movement
                    (product_id, branch_id, movement_type, quantity, balance_after, reason, reference, actor, created_at)
                VALUES (:pid, :bid, :type, :qty, :bal, :reason, :ref, 'sistema', :ts)
                """)
                .param("pid", productId).param("bid", branchId).param("type", movementType)
                .param("qty", delta).param("bal", balance)
                .param("reason", movementType.equals("PURCHASE") ? "Recepción de compra" : "Salida por venta")
                .param("ref", reference).param("ts", when)
                .update();
    }

    // ---------------------------------------------------------------- Helpers

    private static BigDecimal money(double value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(int value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal qty3(double value) {
        return new BigDecimal(value).setScale(3, RoundingMode.HALF_UP);
    }

    private static OffsetDateTime daysAgo(int days) {
        return OffsetDateTime.now().minusDays(days);
    }

    private static String randomPhone() {
        return "33" + ThreadLocalRandom.current().nextInt(10000000, 99999999);
    }

    private static String slug(String name) {
        return java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    // ---------------------------------------------------------------- Records internos

    private record Product(Long id, String name, String unit, boolean soldByWeight,
                           BigDecimal price, BigDecimal cost) {}

    private record SaleLine(Long productId, String description, BigDecimal quantity,
                            BigDecimal unitPrice, BigDecimal lineTotal) {}

    private record SaleDraft(BigDecimal subtotal, BigDecimal discount, BigDecimal tax,
                             BigDecimal total, List<SaleLine> lines) {}

    private record SalesResult(int saleCount, int shiftCount, List<Long> invoiceableSaleIds) {}

    /** Resumen de la siembra demo. */
    public record DemoSummary(int branches, int products, int purchases, int customers,
                              int sales, int shifts, int operations, int invoices) {
        static DemoSummary empty() {
            return new DemoSummary(0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
