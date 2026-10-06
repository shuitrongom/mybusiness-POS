package com.mybusinesssilva.purchasing.application;

import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.inventory.domain.port.in.InventoryPort;
import com.mybusinesssilva.purchasing.domain.model.MexicanTax;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de la suite de COMPRAS enterprise: proveedores, órdenes de compra, recepción de
 * mercancía con impuestos mexicanos, devoluciones de compra, cuentas por pagar y visitas.
 *
 * <p>Al recibir una compra: se registra con su desglose de impuestos (IVA/IEPS trasladados,
 * retenciones y donativo), se aumenta el inventario de cada renglón (vía {@link InventoryPort}),
 * se actualiza el costo del producto (última compra y promedio) y, si es a crédito, se genera la
 * cuenta por pagar. Todo en la misma transacción.
 */
@Service
public class PurchasingService {

    private final JdbcClient jdbc;
    private final InventoryPort inventoryPort;

    public PurchasingService(JdbcClient jdbc, InventoryPort inventoryPort) {
        this.jdbc = jdbc;
        this.inventoryPort = inventoryPort;
    }

    // =====================================================================
    // Proveedores
    // =====================================================================

    /** Alta simple de proveedor (retrocompatible). */
    @Transactional
    public long createSupplier(String name, String rfc, String phone, String email) {
        return jdbc.sql("""
                INSERT INTO supplier (name, rfc, phone, email) VALUES (:n, :r, :p, :e) RETURNING id
                """)
                .param("n", name).param("r", rfc).param("p", phone).param("e", email)
                .query(Long.class).single();
    }

    /** Alta/edición enterprise de proveedor. Si {@code id} es null, crea; si no, actualiza. */
    @Transactional
    public long saveSupplier(Long id, SupplierInput in) {
        if (id == null) {
            return jdbc.sql("""
                    INSERT INTO supplier
                        (external_code, name, rfc, phone, email, country, zip_code, street,
                         neighborhood, town, city, state, credit_days, credit_limit,
                         discount1, discount2, discount3, discount4, discount5, classification,
                         review_payment, affects_inventory_only, skip_payable, notes, image_url,
                         visit_periodicity, visit_days)
                    VALUES
                        (:code, :name, :rfc, :phone, :email, :country, :zip, :street,
                         :neigh, :town, :city, :state, :cdays, :climit,
                         :d1, :d2, :d3, :d4, :d5, :classification,
                         :review, :invOnly, :skipPay, :notes, :image,
                         :vperiod, :vdays)
                    RETURNING id
                    """)
                    .paramSource(supplierParams(in))
                    .query(Long.class).single();
        }
        jdbc.sql("""
                UPDATE supplier SET
                    external_code = :code, name = :name, rfc = :rfc, phone = :phone, email = :email,
                    country = :country, zip_code = :zip, street = :street, neighborhood = :neigh,
                    town = :town, city = :city, state = :state, credit_days = :cdays,
                    credit_limit = :climit, discount1 = :d1, discount2 = :d2, discount3 = :d3,
                    discount4 = :d4, discount5 = :d5, classification = :classification,
                    review_payment = :review, affects_inventory_only = :invOnly,
                    skip_payable = :skipPay, notes = :notes, image_url = :image,
                    visit_periodicity = :vperiod, visit_days = :vdays
                WHERE id = :id
                """)
                .paramSource(supplierParams(in))
                .param("id", id)
                .update();
        return id;
    }

    private Map<String, Object> supplierParams(SupplierInput in) {
        Map<String, Object> p = new java.util.HashMap<>();
        p.put("code", in.externalCode());
        p.put("name", in.name());
        p.put("rfc", in.rfc());
        p.put("phone", in.phone());
        p.put("email", in.email());
        p.put("country", in.country() == null ? "México" : in.country());
        p.put("zip", in.zipCode());
        p.put("street", in.street());
        p.put("neigh", in.neighborhood());
        p.put("town", in.town());
        p.put("city", in.city());
        p.put("state", in.state());
        p.put("cdays", in.creditDays());
        p.put("climit", in.creditLimit());
        p.put("d1", in.discount1());
        p.put("d2", in.discount2());
        p.put("d3", in.discount3());
        p.put("d4", in.discount4());
        p.put("d5", in.discount5());
        p.put("classification", in.classification() == null ? "GENERAL" : in.classification());
        p.put("review", in.reviewPayment());
        p.put("invOnly", in.affectsInventoryOnly());
        p.put("skipPay", in.skipPayable());
        p.put("notes", in.notes());
        p.put("image", in.imageUrl());
        p.put("vperiod", in.visitPeriodicity() == null ? "NONE" : in.visitPeriodicity());
        p.put("vdays", in.visitDays());
        return p;
    }

    /** Lista/busca proveedores activos. */
    public List<Map<String, Object>> listSuppliers(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        boolean has = !q.isBlank();
        return jdbc.sql("""
                SELECT id, external_code, name, rfc, phone, email, city, state, credit_limit,
                       credit_days, classification, active
                FROM supplier
                WHERE active = TRUE
                  AND (:has = FALSE OR lower(name) LIKE :like OR lower(COALESCE(rfc,'')) LIKE :like
                       OR lower(COALESCE(external_code,'')) LIKE :like)
                ORDER BY name
                """)
                .param("has", has).param("like", "%" + q + "%")
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("externalCode", rs.getString("external_code"));
                    m.put("name", rs.getString("name"));
                    m.put("rfc", rs.getString("rfc"));
                    m.put("phone", rs.getString("phone"));
                    m.put("email", rs.getString("email"));
                    m.put("city", rs.getString("city"));
                    m.put("state", rs.getString("state"));
                    m.put("creditLimit", rs.getBigDecimal("credit_limit"));
                    m.put("creditDays", rs.getInt("credit_days"));
                    m.put("classification", rs.getString("classification"));
                    m.put("active", rs.getBoolean("active"));
                    return m;
                })
                .list();
    }

    /** Detalle completo de un proveedor con sus contactos. */
    public Map<String, Object> getSupplier(long id) {
        Map<String, Object> s = jdbc.sql("SELECT * FROM supplier WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("externalCode", rs.getString("external_code"));
                    m.put("name", rs.getString("name"));
                    m.put("rfc", rs.getString("rfc"));
                    m.put("phone", rs.getString("phone"));
                    m.put("email", rs.getString("email"));
                    m.put("country", rs.getString("country"));
                    m.put("zipCode", rs.getString("zip_code"));
                    m.put("street", rs.getString("street"));
                    m.put("neighborhood", rs.getString("neighborhood"));
                    m.put("town", rs.getString("town"));
                    m.put("city", rs.getString("city"));
                    m.put("state", rs.getString("state"));
                    m.put("creditDays", rs.getInt("credit_days"));
                    m.put("creditLimit", rs.getBigDecimal("credit_limit"));
                    m.put("discount1", rs.getBigDecimal("discount1"));
                    m.put("discount2", rs.getBigDecimal("discount2"));
                    m.put("discount3", rs.getBigDecimal("discount3"));
                    m.put("discount4", rs.getBigDecimal("discount4"));
                    m.put("discount5", rs.getBigDecimal("discount5"));
                    m.put("classification", rs.getString("classification"));
                    m.put("reviewPayment", rs.getString("review_payment"));
                    m.put("affectsInventoryOnly", rs.getBoolean("affects_inventory_only"));
                    m.put("skipPayable", rs.getBoolean("skip_payable"));
                    m.put("notes", rs.getString("notes"));
                    m.put("imageUrl", rs.getString("image_url"));
                    m.put("visitPeriodicity", rs.getString("visit_periodicity"));
                    m.put("visitDays", rs.getString("visit_days"));
                    m.put("active", rs.getBoolean("active"));
                    return m;
                })
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("No existe el proveedor " + id));
        s.put("contacts", listContacts(id));
        return s;
    }

    /** Desactiva (borrado lógico) un proveedor. */
    @Transactional
    public void deactivateSupplier(long id) {
        jdbc.sql("UPDATE supplier SET active = FALSE WHERE id = :id").param("id", id).update();
    }

    /** Reemplaza los contactos de un proveedor. */
    @Transactional
    public void replaceContacts(long supplierId, List<ContactInput> contacts) {
        jdbc.sql("DELETE FROM supplier_contact WHERE supplier_id = :id").param("id", supplierId).update();
        if (contacts == null) {
            return;
        }
        for (ContactInput c : contacts) {
            jdbc.sql("""
                    INSERT INTO supplier_contact (supplier_id, name, role, phone, email)
                    VALUES (:sup, :name, :role, :phone, :email)
                    """)
                    .param("sup", supplierId).param("name", c.name()).param("role", c.role())
                    .param("phone", c.phone()).param("email", c.email())
                    .update();
        }
    }

    private List<Map<String, Object>> listContacts(long supplierId) {
        return jdbc.sql("SELECT id, name, role, phone, email FROM supplier_contact WHERE supplier_id = :id ORDER BY id")
                .param("id", supplierId)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("name", rs.getString("name"));
                    m.put("role", rs.getString("role"));
                    m.put("phone", rs.getString("phone"));
                    m.put("email", rs.getString("email"));
                    return m;
                })
                .list();
    }

    /** Importa proveedores en lote; devuelve cuántos se crearon y cuántos fallaron. */
    @Transactional
    public Map<String, Object> importSuppliers(List<SupplierInput> suppliers) {
        int created = 0;
        int failed = 0;
        for (SupplierInput s : suppliers) {
            try {
                saveSupplier(null, s);
                created++;
            } catch (RuntimeException ex) {
                failed++;
            }
        }
        return Map.of("created", created, "failed", failed, "total", suppliers.size());
    }

    // =====================================================================
    // Órdenes de compra y recepción
    // =====================================================================

    /** Recepción de compra simple (retrocompatible con el flujo anterior). */
    @Transactional
    public long receivePurchase(long supplierId, long branchId, String invoiceRef,
                                boolean onCredit, List<PurchaseLineInput> lines, String actor) {
        List<PurchaseLineFull> full = lines.stream()
                .map(l -> new PurchaseLineFull(l.productId(), null, l.quantity(), l.unitCost(),
                        BigDecimal.ZERO, BigDecimal.ZERO, MexicanTax.IVA_16, null))
                .toList();
        PurchaseInput in = new PurchaseInput(
                supplierId, branchId, invoiceRef, "MXN", BigDecimal.ONE, onCredit,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, full);
        return savePurchase("PURCHASE", "RECEIVED", in, actor);
    }

    /** Crea una ORDEN de compra (no afecta inventario ni CxP hasta recibirse). */
    @Transactional
    public long createOrder(PurchaseInput in, String actor) {
        return savePurchase("ORDER", "ORDERED", in, actor);
    }

    /** Registra una COMPRA recibida enterprise (con impuestos, retención y donativo). */
    @Transactional
    public long receivePurchaseFull(PurchaseInput in, String actor) {
        return savePurchase("PURCHASE", "RECEIVED", in, actor);
    }

    /**
     * Convierte una orden de compra en recepción: marca la orden RECEIVED, afecta inventario,
     * actualiza costos y genera cuenta por pagar si aplica.
     */
    @Transactional
    public void receiveOrder(long orderId, String actor) {
        String status = jdbc.sql("SELECT status FROM purchase WHERE id = :id AND doc_type = 'ORDER'")
                .param("id", orderId).query(String.class).optional()
                .orElseThrow(() -> new IllegalArgumentException("No existe la orden de compra " + orderId));
        if (!"ORDERED".equals(status)) {
            throw new IllegalArgumentException("La orden ya fue recibida o cancelada");
        }
        long branchId = jdbc.sql("SELECT branch_id FROM purchase WHERE id = :id").param("id", orderId)
                .query(Long.class).single();
        long supplierId = jdbc.sql("SELECT supplier_id FROM purchase WHERE id = :id").param("id", orderId)
                .query(Long.class).single();
        boolean onCredit = jdbc.sql("SELECT on_credit FROM purchase WHERE id = :id").param("id", orderId)
                .query(Boolean.class).single();
        BigDecimal total = jdbc.sql("SELECT total FROM purchase WHERE id = :id").param("id", orderId)
                .query(BigDecimal.class).single();

        var lines = jdbc.sql("SELECT product_id, quantity, unit_cost FROM purchase_line WHERE purchase_id = :id")
                .param("id", orderId)
                .query((rs, n) -> new Object[] {
                        rs.getLong("product_id"), rs.getBigDecimal("quantity"), rs.getBigDecimal("unit_cost") })
                .list();
        for (Object[] l : lines) {
            long productId = (Long) l[0];
            BigDecimal qty = (BigDecimal) l[1];
            BigDecimal cost = (BigDecimal) l[2];
            inventoryPort.applyMovement(productId, branchId, MovementType.PURCHASE, qty,
                    "PURCHASE:" + orderId, actor);
            updateProductCost(productId, cost, qty);
        }
        jdbc.sql("UPDATE purchase SET status = 'RECEIVED', doc_type = 'PURCHASE', received_at = now() WHERE id = :id")
                .param("id", orderId).update();
        if (onCredit) {
            createPayable(supplierId, orderId, branchId, total, null);
        }
    }

    /**
     * Núcleo de persistencia de una compra/orden. Calcula el desglose (subtotal, descuentos,
     * IVA/IEPS, retención, donativo, total), persiste cabecera y renglones, y —solo si es
     * recepción— afecta inventario, actualiza costos y genera cuenta por pagar.
     */
    private long savePurchase(String docType, String status, PurchaseInput in, String actor) {
        if (in.lines() == null || in.lines().isEmpty()) {
            throw new IllegalArgumentException("La compra debe tener al menos un renglón");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        for (PurchaseLineFull l : in.lines()) {
            BigDecimal base = l.unitCost().multiply(l.quantity())
                    .subtract(nz(l.discount())).subtract(nz(l.discountExtra()));
            if (base.signum() < 0) {
                base = BigDecimal.ZERO;
            }
            subtotal = subtotal.add(base);
            totalTax = totalTax.add(base.multiply(MexicanTax.validateVat(l.taxRate())));
        }
        BigDecimal globalDiscount = nz(in.discount());
        BigDecimal donation = nz(in.donation());
        BigDecimal retention = nz(in.retention());
        subtotal = subtotal.subtract(globalDiscount);
        if (subtotal.signum() < 0) {
            subtotal = BigDecimal.ZERO;
        }
        totalTax = totalTax.setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(totalTax).add(donation).subtract(retention)
                .setScale(2, RoundingMode.HALF_UP);

        long purchaseId = jdbc.sql("""
                INSERT INTO purchase
                    (supplier_id, branch_id, invoice_ref, doc_type, currency, exchange_rate,
                     subtotal, discount, discount_pct, tax, retention, donation, total, on_credit,
                     status, cfdi_uuid, notes, created_by, expected_date, received_at)
                VALUES
                    (:sup, :branch, :ref, :doc, :cur, :rate,
                     :subtotal, :disc, :discPct, :tax, :ret, :don, :total, :credit,
                     :status, :uuid, :notes, :actor, :expected, :receivedAt)
                RETURNING id
                """)
                .param("sup", in.supplierId()).param("branch", in.branchId())
                .param("ref", in.invoiceRef()).param("doc", docType)
                .param("cur", in.currency() == null ? "MXN" : in.currency())
                .param("rate", in.exchangeRate() == null ? BigDecimal.ONE : in.exchangeRate())
                .param("subtotal", subtotal).param("disc", globalDiscount)
                .param("discPct", nz(in.discountPct())).param("tax", totalTax)
                .param("ret", retention).param("don", donation).param("total", total)
                .param("credit", in.onCredit()).param("status", status)
                .param("uuid", in.cfdiUuid()).param("notes", in.notes()).param("actor", actor)
                .param("expected", in.expectedDate())
                .param("receivedAt", "RECEIVED".equals(status) ? java.time.OffsetDateTime.now() : null)
                .query(Long.class).single();

        jdbc.sql("UPDATE purchase SET folio = :folio WHERE id = :id")
                .param("folio", ("ORDER".equals(docType) ? "OC-" : "C-") + purchaseId)
                .param("id", purchaseId).update();

        for (PurchaseLineFull l : in.lines()) {
            BigDecimal base = l.unitCost().multiply(l.quantity())
                    .subtract(nz(l.discount())).subtract(nz(l.discountExtra()));
            if (base.signum() < 0) {
                base = BigDecimal.ZERO;
            }
            BigDecimal lineTax = base.multiply(MexicanTax.validateVat(l.taxRate()))
                    .setScale(2, RoundingMode.HALF_UP);
            jdbc.sql("""
                    INSERT INTO purchase_line
                        (purchase_id, product_id, description, quantity, unit_cost, discount,
                         discount_extra, tax_rate, tax_amount, line_total, expected_date)
                    VALUES (:pur, :prod, :desc, :qty, :cost, :disc, :discX, :rate, :taxAmt, :total, :expected)
                    """)
                    .param("pur", purchaseId).param("prod", l.productId())
                    .param("desc", l.description()).param("qty", l.quantity())
                    .param("cost", l.unitCost()).param("disc", nz(l.discount()))
                    .param("discX", nz(l.discountExtra())).param("rate", MexicanTax.validateVat(l.taxRate()))
                    .param("taxAmt", lineTax).param("total", base.add(lineTax))
                    .param("expected", l.expectedDate())
                    .update();

            // Solo la recepción afecta inventario y costo.
            if ("RECEIVED".equals(status)) {
                inventoryPort.applyMovement(l.productId(), in.branchId(), MovementType.PURCHASE,
                        l.quantity(), "PURCHASE:" + purchaseId, actor);
                updateProductCost(l.productId(), l.unitCost(), l.quantity());
            }
        }

        // Cuenta por pagar (solo recepción a crédito).
        if ("RECEIVED".equals(status) && in.onCredit()) {
            createPayable(in.supplierId(), purchaseId, in.branchId(), total, in.invoiceRef());
        }

        return purchaseId;
    }

    /**
     * Actualiza el costo del producto tras una compra: fija el costo de la última compra y
     * recalcula el costo promedio ponderado con la existencia previa.
     */
    private void updateProductCost(long productId, BigDecimal unitCost, BigDecimal incomingQty) {
        // Costo promedio ponderado aproximado: (avg_actual * existencia_previa + costo_nuevo * qty)
        // / (existencia_previa + qty). Si no hay existencia previa, el promedio es el costo nuevo.
        jdbc.sql("""
                UPDATE product SET
                    last_cost = :cost,
                    cost = :cost,
                    avg_cost = CASE
                        WHEN COALESCE((SELECT SUM(quantity) FROM inventory_stock WHERE product_id = :id), 0) <= 0
                            THEN :cost
                        ELSE ROUND(
                            ((avg_cost * COALESCE((SELECT SUM(quantity) FROM inventory_stock WHERE product_id = :id), 0))
                             + (:cost * :qty))
                            / NULLIF(COALESCE((SELECT SUM(quantity) FROM inventory_stock WHERE product_id = :id), 0) + :qty, 0), 2)
                    END
                WHERE id = :id
                """)
                .param("cost", unitCost).param("qty", incomingQty).param("id", productId)
                .update();
    }

    private void createPayable(long supplierId, long purchaseId, long branchId,
                               BigDecimal amount, String invoiceRef) {
        Integer creditDays = jdbc.sql("SELECT credit_days FROM supplier WHERE id = :id")
                .param("id", supplierId).query(Integer.class).optional().orElse(0);
        LocalDate due = creditDays != null && creditDays > 0 ? LocalDate.now().plusDays(creditDays) : null;
        jdbc.sql("""
                INSERT INTO account_payable
                    (supplier_id, purchase_id, branch_id, amount, invoice_ref, due_date, status)
                VALUES (:sup, :pur, :branch, :amount, :ref, :due, 'OPEN')
                """)
                .param("sup", supplierId).param("pur", purchaseId).param("branch", branchId)
                .param("amount", amount).param("ref", invoiceRef).param("due", due)
                .update();
    }

    /** Lista compras/órdenes con filtros de proveedor, tipo y rango de fechas. */
    public List<Map<String, Object>> listPurchases(String docType, Long supplierId,
                                                    LocalDate from, LocalDate to) {
        return jdbc.sql("""
                SELECT p.id, p.folio, p.doc_type, p.supplier_id, s.name AS supplier_name,
                       p.invoice_ref, p.currency, p.subtotal, p.tax, p.retention, p.donation,
                       p.total, p.on_credit, p.status, p.created_at, p.expected_date
                FROM purchase p JOIN supplier s ON s.id = p.supplier_id
                WHERE (CAST(:doc AS varchar) IS NULL OR p.doc_type = :doc)
                  AND (CAST(:sup AS bigint) IS NULL OR p.supplier_id = :sup)
                  AND (CAST(:from AS date) IS NULL OR p.created_at::date >= :from)
                  AND (CAST(:to AS date) IS NULL OR p.created_at::date <= :to)
                ORDER BY p.created_at DESC
                """)
                .param("doc", docType).param("sup", supplierId).param("from", from).param("to", to)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("folio", rs.getString("folio"));
                    m.put("docType", rs.getString("doc_type"));
                    m.put("supplierId", rs.getLong("supplier_id"));
                    m.put("supplierName", rs.getString("supplier_name"));
                    m.put("invoiceRef", rs.getString("invoice_ref"));
                    m.put("currency", rs.getString("currency"));
                    m.put("subtotal", rs.getBigDecimal("subtotal"));
                    m.put("tax", rs.getBigDecimal("tax"));
                    m.put("retention", rs.getBigDecimal("retention"));
                    m.put("donation", rs.getBigDecimal("donation"));
                    m.put("total", rs.getBigDecimal("total"));
                    m.put("onCredit", rs.getBoolean("on_credit"));
                    m.put("status", rs.getString("status"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    m.put("expectedDate", rs.getObject("expected_date", LocalDate.class));
                    return m;
                })
                .list();
    }

    // =====================================================================
    // Devoluciones de compra
    // =====================================================================

    /**
     * Registra una devolución de compra: da salida al inventario y persiste el documento. Puede
     * ligarse a una compra origen o ser independiente.
     */
    @Transactional
    public long registerPurchaseReturn(Long purchaseId, Long supplierId, long branchId,
                                       String reason, List<ReturnLineInput> items, String actor) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("La devolución debe tener al menos un producto");
        }
        BigDecimal total = items.stream()
                .map(i -> i.unitCost().multiply(i.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long returnId = jdbc.sql("""
                INSERT INTO purchase_return (purchase_id, supplier_id, branch_id, reason, total, processed_by)
                VALUES (:pur, :sup, :branch, :reason, :total, :actor)
                RETURNING id
                """)
                .param("pur", purchaseId).param("sup", supplierId).param("branch", branchId)
                .param("reason", reason).param("total", total).param("actor", actor)
                .query(Long.class).single();
        jdbc.sql("UPDATE purchase_return SET folio = :folio WHERE id = :id")
                .param("folio", "DC-" + returnId).param("id", returnId).update();

        for (ReturnLineInput i : items) {
            jdbc.sql("""
                    INSERT INTO purchase_return_line (return_id, product_id, description, quantity, unit_cost, line_total)
                    VALUES (:ret, :prod, :desc, :qty, :cost, :total)
                    """)
                    .param("ret", returnId).param("prod", i.productId())
                    .param("desc", i.description() == null ? "" : i.description())
                    .param("qty", i.quantity()).param("cost", i.unitCost())
                    .param("total", i.unitCost().multiply(i.quantity()))
                    .update();
            // Salida de inventario (devolvemos mercancía al proveedor): usa SALE para restar.
            inventoryPort.applyMovement(i.productId(), branchId, MovementType.SALE, i.quantity(),
                    "PURCHASE_RETURN:" + returnId, actor);
        }
        return returnId;
    }

    /** Lista las devoluciones de compra recientes. */
    public List<Map<String, Object>> listPurchaseReturns(int limit) {
        int max = limit <= 0 ? 50 : Math.min(limit, 200);
        return jdbc.sql("""
                SELECT r.id, r.folio, r.purchase_id, r.supplier_id, s.name AS supplier_name,
                       r.total, r.reason, r.processed_by, r.created_at
                FROM purchase_return r LEFT JOIN supplier s ON s.id = r.supplier_id
                ORDER BY r.created_at DESC LIMIT :max
                """)
                .param("max", max)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("folio", rs.getString("folio"));
                    m.put("purchaseId", rs.getObject("purchase_id"));
                    m.put("supplierId", rs.getObject("supplier_id"));
                    m.put("supplierName", rs.getString("supplier_name"));
                    m.put("total", rs.getBigDecimal("total"));
                    m.put("reason", rs.getString("reason"));
                    m.put("processedBy", rs.getString("processed_by"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();
    }

    // =====================================================================
    // Cuentas por pagar
    // =====================================================================

    /** Abona a una cuenta por pagar; la marca PAID si se salda. */
    @Transactional
    public void payPayable(long payableId, BigDecimal amount) {
        jdbc.sql("""
                UPDATE account_payable
                SET paid = paid + :amt,
                    status = CASE WHEN paid + :amt >= amount THEN 'PAID' ELSE 'OPEN' END
                WHERE id = :id
                """)
                .param("id", payableId).param("amt", amount)
                .update();
    }

    /** Lista cuentas por pagar, filtrables por proveedor y por "solo con saldo". */
    public List<Map<String, Object>> listPayables(Long supplierId, boolean onlyWithBalance) {
        return jdbc.sql("""
                SELECT ap.id, ap.supplier_id, s.name AS supplier_name, ap.purchase_id, ap.invoice_ref,
                       ap.amount, ap.paid, (ap.amount - ap.paid) AS balance, ap.status,
                       ap.due_date, ap.currency, ap.created_at
                FROM account_payable ap JOIN supplier s ON s.id = ap.supplier_id
                WHERE (CAST(:sup AS bigint) IS NULL OR ap.supplier_id = :sup)
                  AND (:onlyBalance = FALSE OR ap.status = 'OPEN')
                ORDER BY ap.status ASC, ap.due_date NULLS LAST, ap.created_at
                """)
                .param("sup", supplierId).param("onlyBalance", onlyWithBalance)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("supplierId", rs.getLong("supplier_id"));
                    m.put("supplierName", rs.getString("supplier_name"));
                    m.put("purchaseId", rs.getObject("purchase_id"));
                    m.put("invoiceRef", rs.getString("invoice_ref"));
                    m.put("amount", rs.getBigDecimal("amount"));
                    m.put("paid", rs.getBigDecimal("paid"));
                    m.put("balance", rs.getBigDecimal("balance"));
                    m.put("status", rs.getString("status"));
                    m.put("dueDate", rs.getObject("due_date", LocalDate.class));
                    m.put("currency", rs.getString("currency"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();
    }

    /** Resumen de cuentas por pagar: total pendiente y vencidas. */
    public Map<String, Object> payablesSummary() {
        return jdbc.sql("""
                SELECT COALESCE(SUM(CASE WHEN status='OPEN' THEN amount-paid ELSE 0 END),0) AS total_pending,
                       COUNT(*) FILTER (WHERE status='OPEN') AS open_count,
                       COUNT(*) FILTER (WHERE status='OPEN' AND due_date IS NOT NULL AND due_date < CURRENT_DATE) AS overdue_count
                FROM account_payable
                """)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("totalPending", rs.getBigDecimal("total_pending"));
                    m.put("openCount", rs.getInt("open_count"));
                    m.put("overdueCount", rs.getInt("overdue_count"));
                    return m;
                })
                .single();
    }

    // =====================================================================
    // Visitas de proveedor
    // =====================================================================

    /** Programa/actualiza el rol de visita (periodicidad) de un proveedor. */
    @Transactional
    public void setVisitRole(long supplierId, String periodicity, String days) {
        jdbc.sql("UPDATE supplier SET visit_periodicity = :p, visit_days = :d WHERE id = :id")
                .param("p", periodicity).param("d", days).param("id", supplierId)
                .update();
    }

    /** Agenda una visita de proveedor. */
    @Transactional
    public long scheduleVisit(long supplierId, LocalDate date, String time,
                              BigDecimal estimatedAmount, String notes) {
        return jdbc.sql("""
                INSERT INTO supplier_visit (supplier_id, visit_date, visit_time, estimated_amount, notes)
                VALUES (:sup, :date, :time, :est, :notes) RETURNING id
                """)
                .param("sup", supplierId).param("date", date).param("time", time)
                .param("est", estimatedAmount == null ? BigDecimal.ZERO : estimatedAmount)
                .param("notes", notes)
                .query(Long.class).single();
    }

    /** Marca una visita como realizada, con el monto de compra logrado. */
    @Transactional
    public void markVisited(long visitId, BigDecimal purchaseAmount) {
        jdbc.sql("UPDATE supplier_visit SET visited = TRUE, purchase_amount = :amt WHERE id = :id")
                .param("amt", purchaseAmount == null ? BigDecimal.ZERO : purchaseAmount)
                .param("id", visitId)
                .update();
    }

    /** Lista las visitas en un rango de fechas. */
    public List<Map<String, Object>> listVisits(LocalDate from, LocalDate to) {
        return jdbc.sql("""
                SELECT v.id, v.supplier_id, s.name AS supplier_name, v.visit_date, v.visit_time,
                       v.estimated_amount, v.purchase_amount, v.visited, v.notes
                FROM supplier_visit v JOIN supplier s ON s.id = v.supplier_id
                WHERE (CAST(:from AS date) IS NULL OR v.visit_date >= :from)
                  AND (CAST(:to AS date) IS NULL OR v.visit_date <= :to)
                ORDER BY v.visit_date, v.visit_time
                """)
                .param("from", from).param("to", to)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("supplierId", rs.getLong("supplier_id"));
                    m.put("supplierName", rs.getString("supplier_name"));
                    m.put("visitDate", rs.getObject("visit_date", LocalDate.class));
                    m.put("visitTime", rs.getString("visit_time"));
                    m.put("estimatedAmount", rs.getBigDecimal("estimated_amount"));
                    m.put("purchaseAmount", rs.getBigDecimal("purchase_amount"));
                    m.put("visited", rs.getBoolean("visited"));
                    m.put("notes", rs.getString("notes"));
                    return m;
                })
                .list();
    }

    // =====================================================================
    // Importación de CFDI XML de proveedor
    // =====================================================================

    /**
     * Analiza un CFDI 4.0 (XML) de un proveedor y devuelve sus datos para previsualizar la compra:
     * emisor (RFC/nombre), folio fiscal, y los conceptos (descripción, cantidad, valor unitario).
     * No persiste nada: el frontend confirma y luego llama a la recepción. Así el usuario revisa
     * antes de afectar inventario.
     *
     * @param xml contenido del comprobante
     * @return mapa con emisor, uuid, total y conceptos
     */
    public Map<String, Object> parseCfdi(String xml) {
        try {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            // Endurecer el parser contra XXE (entidades externas).
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var builder = factory.newDocumentBuilder();
            var doc = builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(xml)));

            var comprobante = doc.getDocumentElement();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", attr(comprobante, "Total"));
            result.put("subtotal", attr(comprobante, "SubTotal"));
            result.put("currency", attr(comprobante, "Moneda"));

            var emisores = doc.getElementsByTagName("cfdi:Emisor");
            if (emisores.getLength() == 0) {
                emisores = doc.getElementsByTagName("Emisor");
            }
            if (emisores.getLength() > 0) {
                var emisor = (org.w3c.dom.Element) emisores.item(0);
                result.put("supplierRfc", attr(emisor, "Rfc"));
                result.put("supplierName", attr(emisor, "Nombre"));
            }

            var complemento = doc.getElementsByTagName("tfd:TimbreFiscalDigital");
            if (complemento.getLength() > 0) {
                result.put("uuid", attr((org.w3c.dom.Element) complemento.item(0), "UUID"));
            }

            var conceptosNodes = doc.getElementsByTagName("cfdi:Concepto");
            if (conceptosNodes.getLength() == 0) {
                conceptosNodes = doc.getElementsByTagName("Concepto");
            }
            List<Map<String, Object>> conceptos = new java.util.ArrayList<>();
            for (int i = 0; i < conceptosNodes.getLength(); i++) {
                var c = (org.w3c.dom.Element) conceptosNodes.item(i);
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("description", attr(c, "Descripcion"));
                line.put("quantity", attr(c, "Cantidad"));
                line.put("unitCost", attr(c, "ValorUnitario"));
                line.put("satKey", attr(c, "ClaveProdServ"));
                conceptos.add(line);
            }
            result.put("concepts", conceptos);
            return result;
        } catch (Exception ex) {
            throw new IllegalArgumentException("No se pudo leer el CFDI XML: " + ex.getMessage());
        }
    }

    private static String attr(org.w3c.dom.Element el, String name) {
        String v = el.getAttribute(name);
        return v == null || v.isBlank() ? null : v;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    // =====================================================================
    // Records de entrada
    // =====================================================================

    /** Renglón de compra simple (retrocompatible). */
    public record PurchaseLineInput(long productId, BigDecimal quantity, BigDecimal unitCost) {
    }

    /** Renglón de compra enterprise. */
    public record PurchaseLineFull(
            long productId, String description, BigDecimal quantity, BigDecimal unitCost,
            BigDecimal discount, BigDecimal discountExtra, BigDecimal taxRate, LocalDate expectedDate) {
    }

    /** Cabecera de compra/orden enterprise. */
    public record PurchaseInput(
            long supplierId, long branchId, String invoiceRef, String currency, BigDecimal exchangeRate,
            boolean onCredit, BigDecimal discount, BigDecimal discountPct, BigDecimal donation,
            BigDecimal retention, String cfdiUuid, String notes, LocalDate expectedDate,
            List<PurchaseLineFull> lines) {
    }

    /** Alta/edición de proveedor. */
    public record SupplierInput(
            String externalCode, String name, String rfc, String phone, String email, String country,
            String zipCode, String street, String neighborhood, String town, String city, String state,
            int creditDays, BigDecimal creditLimit, BigDecimal discount1, BigDecimal discount2,
            BigDecimal discount3, BigDecimal discount4, BigDecimal discount5, String classification,
            String reviewPayment, boolean affectsInventoryOnly, boolean skipPayable, String notes,
            String imageUrl, String visitPeriodicity, String visitDays) {
    }

    /** Contacto de proveedor. */
    public record ContactInput(String name, String role, String phone, String email) {
    }

    /** Renglón de devolución de compra. */
    public record ReturnLineInput(long productId, String description, BigDecimal quantity, BigDecimal unitCost) {
    }
}
