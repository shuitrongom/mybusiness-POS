package com.mybusinesssilva.inventory.application;

import com.mybusinesssilva.inventory.domain.model.MovementType;
import com.mybusinesssilva.inventory.domain.port.in.InventoryPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de la suite de INVENTARIO enterprise: documentos de entrada/salida con conceptos,
 * números de serie, lotes con caducidad, variantes por talla/color, conteo físico, calidad de
 * inventario (clasificación ABC) y etiquetas de código de barras.
 *
 * <p>Afecta las existencias a través de {@link InventoryPort} para no duplicar el motor de
 * inventario, y persiste los documentos/catálogos con {@link JdbcClient} sobre el schema del
 * tenant en curso (RLS lo aísla).
 */
@Service
public class InventoryOpsService {

    private final JdbcClient jdbc;
    private final InventoryPort inventoryPort;

    public InventoryOpsService(JdbcClient jdbc, InventoryPort inventoryPort) {
        this.jdbc = jdbc;
        this.inventoryPort = inventoryPort;
    }

    // =====================================================================
    // Catálogos: conceptos, tallas, colores, formatos de etiqueta
    // =====================================================================

    public List<Map<String, Object>> listConcepts(String direction) {
        return jdbc.sql("""
                SELECT id, name, direction FROM inventory_concept
                WHERE active = TRUE AND (CAST(:dir AS varchar) IS NULL OR direction = :dir)
                ORDER BY direction, name
                """)
                .param("dir", direction)
                .query((rs, n) -> row(Map.of(
                        "id", rs.getLong("id"), "name", rs.getString("name"),
                        "direction", rs.getString("direction"))))
                .list();
    }

    @Transactional
    public long createConcept(String name, String direction) {
        return jdbc.sql("INSERT INTO inventory_concept (name, direction) VALUES (:n, :d) RETURNING id")
                .param("n", name).param("d", direction).query(Long.class).single();
    }

    public List<Map<String, Object>> listSizes() {
        return jdbc.sql("SELECT id, code, label FROM size_catalog WHERE active = TRUE ORDER BY sort_order, code")
                .query((rs, n) -> row(Map.of("id", rs.getLong("id"), "code", rs.getString("code"),
                        "label", rs.getString("label"))))
                .list();
    }

    public List<Map<String, Object>> listColors() {
        return jdbc.sql("SELECT id, code, label, hex FROM color_catalog WHERE active = TRUE ORDER BY label")
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("code", rs.getString("code"));
                    m.put("label", rs.getString("label"));
                    m.put("hex", rs.getString("hex"));
                    return m;
                })
                .list();
    }

    @Transactional
    public long createSize(String code, String label) {
        return jdbc.sql("INSERT INTO size_catalog (code, label) VALUES (:c, :l) RETURNING id")
                .param("c", code).param("l", label).query(Long.class).single();
    }

    @Transactional
    public long createColor(String code, String label, String hex) {
        return jdbc.sql("INSERT INTO color_catalog (code, label, hex) VALUES (:c, :l, :h) RETURNING id")
                .param("c", code).param("l", label).param("h", hex).query(Long.class).single();
    }

    public List<Map<String, Object>> listLabelFormats() {
        return jdbc.sql("SELECT id, name, width_mm, height_mm, show_price, show_name, columns FROM label_format WHERE active = TRUE ORDER BY id")
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("name", rs.getString("name"));
                    m.put("widthMm", rs.getInt("width_mm"));
                    m.put("heightMm", rs.getInt("height_mm"));
                    m.put("showPrice", rs.getBoolean("show_price"));
                    m.put("showName", rs.getBoolean("show_name"));
                    m.put("columns", rs.getInt("columns"));
                    return m;
                })
                .list();
    }

    // =====================================================================
    // Alta rápida de producto
    // =====================================================================

    /**
     * Alta rápida de un artículo: crea un producto mínimo con clave/unidad SAT, costo y precio, y
     * su código de barras si se indica. Devuelve el id del producto.
     */
    @Transactional
    public long quickCreateProduct(String code, String description, BigDecimal cost, BigDecimal price,
                                   String satKey, String satUnit, String unit, boolean withoutVat) {
        BigDecimal taxRate = withoutVat ? BigDecimal.ZERO : new BigDecimal("0.16");
        long id = jdbc.sql("""
                INSERT INTO product (sku, name, unit, sat_prod_serv, sat_unit, price, cost, tax_rate, active)
                VALUES (:sku, :name, :unit, :satKey, :satUnit, :price, :cost, :tax, TRUE)
                RETURNING id
                """)
                .param("sku", code)
                .param("name", description)
                .param("unit", unit == null || unit.isBlank() ? "pieza" : unit)
                .param("satKey", satKey)
                .param("satUnit", satUnit)
                .param("price", price == null ? BigDecimal.ZERO : price)
                .param("cost", cost == null ? BigDecimal.ZERO : cost)
                .param("tax", taxRate)
                .query(Long.class).single();
        if (code != null && !code.isBlank()) {
            jdbc.sql("INSERT INTO product_barcode (product_id, barcode) VALUES (:p, :b) ON CONFLICT DO NOTHING")
                    .param("p", id).param("b", code).update();
        }
        return id;
    }

    // =====================================================================
    // Documentos de entrada / salida de inventario
    // =====================================================================

    /**
     * Registra un documento de entrada o salida de inventario con sus renglones, afectando las
     * existencias. Entrada suma (ADJUSTMENT +), salida resta (ADJUSTMENT -). Devuelve el id.
     */
    @Transactional
    public long registerDocument(String docType, Long conceptId, long branchId, String notes,
                                 List<DocLineInput> lines, String actor) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("El documento debe tener al menos un renglón");
        }
        String conceptName = conceptId == null ? null
                : jdbc.sql("SELECT name FROM inventory_concept WHERE id = :id")
                        .param("id", conceptId).query(String.class).optional().orElse(null);

        BigDecimal totalQty = lines.stream().map(DocLineInput::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalValue = lines.stream()
                .map(l -> l.unitCost().multiply(l.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long docId = jdbc.sql("""
                INSERT INTO inventory_document
                    (doc_type, concept_id, concept_name, branch_id, total_qty, total_value, notes, created_by)
                VALUES (:type, :concept, :cname, :branch, :qty, :value, :notes, :actor)
                RETURNING id
                """)
                .param("type", docType).param("concept", conceptId).param("cname", conceptName)
                .param("branch", branchId).param("qty", totalQty).param("value", totalValue)
                .param("notes", notes).param("actor", actor)
                .query(Long.class).single();
        jdbc.sql("UPDATE inventory_document SET folio = :f WHERE id = :id")
                .param("f", ("IN".equals(docType) ? "E-" : "S-") + docId).param("id", docId).update();

        for (DocLineInput l : lines) {
            jdbc.sql("""
                    INSERT INTO inventory_document_line
                        (document_id, product_id, description, quantity, unit_cost, line_total)
                    VALUES (:doc, :prod, :desc, :qty, :cost, :total)
                    """)
                    .param("doc", docId).param("prod", l.productId()).param("desc", l.description())
                    .param("qty", l.quantity()).param("cost", l.unitCost())
                    .param("total", l.unitCost().multiply(l.quantity()))
                    .update();
            BigDecimal delta = "IN".equals(docType) ? l.quantity().abs() : l.quantity().abs().negate();
            String ref = ("IN".equals(docType) ? "IN-DOC:" : "OUT-DOC:") + docId;
            // Usa el puerto con ADJUSTMENT (delta con signo) para reflejar entrada/salida.
            inventoryPort.applyMovement(l.productId(), branchId,
                    "IN".equals(docType) ? MovementType.RETURN : MovementType.SALE,
                    l.quantity().abs(), ref, actor);
            // El movimiento anterior ya aplicó el signo por tipo (RETURN suma, SALE resta).
        }
        return docId;
    }

    public List<Map<String, Object>> listDocuments(String docType, int limit) {
        int max = limit <= 0 ? 50 : Math.min(limit, 200);
        return jdbc.sql("""
                SELECT d.id, d.folio, d.doc_type, d.concept_name, d.branch_id, b.name AS branch_name,
                       d.total_qty, d.total_value, d.notes, d.created_by, d.created_at
                FROM inventory_document d LEFT JOIN branch b ON b.id = d.branch_id
                WHERE (CAST(:type AS varchar) IS NULL OR d.doc_type = :type)
                ORDER BY d.created_at DESC LIMIT :max
                """)
                .param("type", docType).param("max", max)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("folio", rs.getString("folio"));
                    m.put("docType", rs.getString("doc_type"));
                    m.put("conceptName", rs.getString("concept_name"));
                    m.put("branchName", rs.getString("branch_name"));
                    m.put("totalQty", rs.getBigDecimal("total_qty"));
                    m.put("totalValue", rs.getBigDecimal("total_value"));
                    m.put("notes", rs.getString("notes"));
                    m.put("createdBy", rs.getString("created_by"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();
    }

    // =====================================================================
    // Números de serie
    // =====================================================================

    /** Da de alta números de serie para un producto (lista explícita). */
    @Transactional
    public int addSerials(long productId, Long branchId, List<String> serials, String entryPort,
                          String pedimento, String documentRef) {
        int added = 0;
        for (String s : serials) {
            if (s == null || s.isBlank()) {
                continue;
            }
            int r = jdbc.sql("""
                    INSERT INTO product_serial (product_id, branch_id, serial, entry_port, pedimento, document_ref)
                    VALUES (:p, :b, :s, :port, :ped, :ref)
                    ON CONFLICT (serial) DO NOTHING
                    """)
                    .param("p", productId).param("b", branchId).param("s", s.trim())
                    .param("port", entryPort).param("ped", pedimento).param("ref", documentRef)
                    .update();
            added += r;
        }
        return added;
    }

    /**
     * Genera series por rango numérico (prefijo + número desde..hasta). Útil para captura masiva.
     */
    @Transactional
    public int addSerialRange(long productId, Long branchId, String prefix, long from, long to,
                              String entryPort, String pedimento, String documentRef) {
        if (to < from) {
            throw new IllegalArgumentException("El rango de series es inválido");
        }
        List<String> serials = new ArrayList<>();
        for (long i = from; i <= to && serials.size() <= 5000; i++) {
            serials.add((prefix == null ? "" : prefix) + i);
        }
        return addSerials(productId, branchId, serials, entryPort, pedimento, documentRef);
    }

    public List<Map<String, Object>> listSerials(long productId, String status) {
        return jdbc.sql("""
                SELECT id, serial, status, entry_port, pedimento, document_ref, created_at
                FROM product_serial
                WHERE product_id = :p AND (CAST(:st AS varchar) IS NULL OR status = :st)
                ORDER BY created_at DESC, id DESC
                """)
                .param("p", productId).param("st", status)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("serial", rs.getString("serial"));
                    m.put("status", rs.getString("status"));
                    m.put("entryPort", rs.getString("entry_port"));
                    m.put("pedimento", rs.getString("pedimento"));
                    m.put("documentRef", rs.getString("document_ref"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();
    }

    // =====================================================================
    // Lotes con caducidad
    // =====================================================================

    /** Captura un lote con caducidad. Suma la cantidad al lote y a la existencia. */
    @Transactional
    public long addLot(long productId, long branchId, String lotCode, LocalDate expiration,
                       BigDecimal quantity, String entryPort, String pedimento, String documentRef,
                       String actor) {
        long lotId = jdbc.sql("""
                INSERT INTO product_lot (product_id, branch_id, lot_code, expiration_date, quantity,
                                         entry_port, pedimento, document_ref)
                VALUES (:p, :b, :code, :exp, :qty, :port, :ped, :ref)
                RETURNING id
                """)
                .param("p", productId).param("b", branchId).param("code", lotCode)
                .param("exp", expiration).param("qty", quantity)
                .param("port", entryPort).param("ped", pedimento).param("ref", documentRef)
                .query(Long.class).single();
        // La captura de lote es una entrada de mercancía.
        inventoryPort.applyMovement(productId, branchId, MovementType.RETURN, quantity.abs(),
                "LOT:" + lotId, actor);
        return lotId;
    }

    public List<Map<String, Object>> listLots(Long productId, boolean onlyWithStock) {
        return jdbc.sql("""
                SELECT l.id, l.product_id, p.name AS product_name, l.branch_id, l.lot_code,
                       l.expiration_date, l.quantity, l.entry_port, l.pedimento
                FROM product_lot l JOIN product p ON p.id = l.product_id
                WHERE (CAST(:p AS bigint) IS NULL OR l.product_id = :p)
                  AND (:onlyStock = FALSE OR l.quantity > 0)
                ORDER BY l.expiration_date NULLS LAST, l.id
                """)
                .param("p", productId).param("onlyStock", onlyWithStock)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("productId", rs.getLong("product_id"));
                    m.put("productName", rs.getString("product_name"));
                    m.put("lotCode", rs.getString("lot_code"));
                    m.put("expirationDate", rs.getObject("expiration_date", LocalDate.class));
                    m.put("quantity", rs.getBigDecimal("quantity"));
                    m.put("entryPort", rs.getString("entry_port"));
                    m.put("pedimento", rs.getString("pedimento"));
                    return m;
                })
                .list();
    }

    // =====================================================================
    // Variantes: tallas y colores (generar códigos por modelo)
    // =====================================================================

    /**
     * Crea un modelo y genera un producto (variante) por cada combinación talla × color elegida.
     * Cada variante lleva su SKU (código del modelo + talla + color), su precio y su bandera de
     * variante. Devuelve cuántas variantes se generaron.
     */
    @Transactional
    public Map<String, Object> generateVariants(String modelCode, String modelName, String brand,
                                                BigDecimal cost, BigDecimal price1, BigDecimal price2,
                                                BigDecimal price3, Long categoryId,
                                                List<String> sizeCodes, List<String> colorCodes) {
        if (sizeCodes == null || sizeCodes.isEmpty() || colorCodes == null || colorCodes.isEmpty()) {
            throw new IllegalArgumentException("Elige al menos una talla y un color");
        }
        long modelId = jdbc.sql("""
                INSERT INTO product_model (code, name, brand, cost, price1, price2, price3, category_id)
                VALUES (:code, :name, :brand, :cost, :p1, :p2, :p3, :cat)
                ON CONFLICT (code) DO UPDATE SET name = :name, brand = :brand, cost = :cost,
                    price1 = :p1, price2 = :p2, price3 = :p3, category_id = :cat
                RETURNING id
                """)
                .param("code", modelCode).param("name", modelName).param("brand", brand)
                .param("cost", nz(cost)).param("p1", nz(price1)).param("p2", nz(price2))
                .param("p3", nz(price3)).param("cat", categoryId)
                .query(Long.class).single();

        int created = 0;
        for (String size : sizeCodes) {
            for (String color : colorCodes) {
                String sku = modelCode + "-" + size + "-" + color;
                String variantName = modelName + " " + size + " " + color;
                // Evita duplicar variantes ya existentes del mismo SKU.
                boolean exists = jdbc.sql("SELECT count(*) FROM product WHERE sku = :sku")
                        .param("sku", sku).query(Integer.class).single() > 0;
                if (exists) {
                    continue;
                }
                long productId = jdbc.sql("""
                        INSERT INTO product (sku, name, category_id, unit, price, price2, price3, cost,
                                             model_id, size_code, color_code, active)
                        VALUES (:sku, :name, :cat, 'pieza', :p1, :p2, :p3, :cost, :model, :size, :color, TRUE)
                        RETURNING id
                        """)
                        .param("sku", sku).param("name", variantName).param("cat", categoryId)
                        .param("p1", nz(price1)).param("p2", nz(price2)).param("p3", nz(price3))
                        .param("cost", nz(cost)).param("model", modelId).param("size", size).param("color", color)
                        .query(Long.class).single();
                jdbc.sql("INSERT INTO product_barcode (product_id, barcode) VALUES (:p, :b) ON CONFLICT DO NOTHING")
                        .param("p", productId).param("b", sku).update();
                created++;
            }
        }
        return Map.of("modelId", modelId, "variantsCreated", created);
    }

    // =====================================================================
    // Inventario físico (conteo)
    // =====================================================================

    /**
     * Inicia un conteo físico: crea el documento y precarga los productos del alcance (sucursal y,
     * opcionalmente, familia) con su existencia teórica actual. Devuelve el id del conteo.
     */
    @Transactional
    public long startPhysicalCount(long branchId, Long categoryId, String actor) {
        long countId = jdbc.sql("""
                INSERT INTO physical_count (branch_id, category_id, created_by, status)
                VALUES (:b, :cat, :actor, 'OPEN') RETURNING id
                """)
                .param("b", branchId).param("cat", categoryId).param("actor", actor)
                .query(Long.class).single();
        jdbc.sql("UPDATE physical_count SET folio = :f WHERE id = :id")
                .param("f", "IF-" + countId).param("id", countId).update();

        // Precarga los productos con existencia teórica de la sucursal (y familia si se indicó).
        jdbc.sql("""
                INSERT INTO physical_count_line (count_id, product_id, theoretical)
                SELECT :count, p.id, COALESCE(s.quantity, 0)
                FROM product p
                LEFT JOIN inventory_stock s ON s.product_id = p.id AND s.branch_id = :b
                WHERE p.active = TRUE
                  AND (CAST(:cat AS bigint) IS NULL OR p.category_id = :cat)
                ON CONFLICT (count_id, product_id) DO NOTHING
                """)
                .param("count", countId).param("b", branchId).param("cat", categoryId)
                .update();
        return countId;
    }

    /** Captura la cantidad contada de un producto en el conteo (por id de producto o SKU). */
    @Transactional
    public void captureCount(long countId, long productId, String marbete, BigDecimal counted) {
        jdbc.sql("""
                UPDATE physical_count_line
                SET counted = :counted, marbete = :marbete, difference = :counted - theoretical
                WHERE count_id = :count AND product_id = :prod
                """)
                .param("counted", counted).param("marbete", marbete)
                .param("count", countId).param("prod", productId)
                .update();
    }

    /**
     * Aplica el conteo: por cada renglón con diferencia, ajusta la existencia a lo contado
     * (movimiento ADJUSTMENT con la diferencia) y marca el conteo como aplicado.
     */
    @Transactional
    public Map<String, Object> applyPhysicalCount(long countId, String actor) {
        var lines = jdbc.sql("""
                SELECT product_id, difference
                FROM physical_count_line
                WHERE count_id = :count AND counted IS NOT NULL AND difference <> 0
                """)
                .param("count", countId)
                .query((rs, n) -> new Object[] { rs.getLong("product_id"), rs.getBigDecimal("difference") })
                .list();
        long branchId = jdbc.sql("SELECT branch_id FROM physical_count WHERE id = :id")
                .param("id", countId).query(Long.class).single();

        int adjusted = 0;
        for (Object[] l : lines) {
            BigDecimal diff = (BigDecimal) l[1];
            if (diff == null || diff.signum() == 0) {
                continue;
            }
            long productId = (Long) l[0];
            // Ajuste con signo: si contó de más, entra; si de menos, sale.
            inventoryPort.applyMovement(productId, branchId,
                    diff.signum() > 0 ? MovementType.RETURN : MovementType.SALE,
                    diff.abs(), "PHYSICAL_COUNT:" + countId, actor);
            adjusted++;
        }
        jdbc.sql("UPDATE physical_count SET status = 'APPLIED', applied_at = now() WHERE id = :id")
                .param("id", countId).update();
        return Map.of("adjusted", adjusted);
    }

    public List<Map<String, Object>> countLines(long countId, boolean onlyDifferences) {
        return jdbc.sql("""
                SELECT cl.product_id, p.name AS product_name, p.sku, cl.marbete,
                       cl.theoretical, cl.counted, cl.difference
                FROM physical_count_line cl JOIN product p ON p.id = cl.product_id
                WHERE cl.count_id = :count
                  AND (:onlyDiff = FALSE OR (cl.counted IS NOT NULL AND cl.difference <> 0))
                ORDER BY p.name
                """)
                .param("count", countId).param("onlyDiff", onlyDifferences)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("productId", rs.getLong("product_id"));
                    m.put("productName", rs.getString("product_name"));
                    m.put("sku", rs.getString("sku"));
                    m.put("marbete", rs.getString("marbete"));
                    m.put("theoretical", rs.getBigDecimal("theoretical"));
                    m.put("counted", rs.getBigDecimal("counted"));
                    m.put("difference", rs.getBigDecimal("difference"));
                    return m;
                })
                .list();
    }

    public List<Map<String, Object>> listCounts(int limit) {
        int max = limit <= 0 ? 30 : Math.min(limit, 100);
        return jdbc.sql("""
                SELECT pc.id, pc.folio, pc.branch_id, b.name AS branch_name, pc.status,
                       pc.created_by, pc.created_at, pc.applied_at
                FROM physical_count pc LEFT JOIN branch b ON b.id = pc.branch_id
                ORDER BY pc.created_at DESC LIMIT :max
                """)
                .param("max", max)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("folio", rs.getString("folio"));
                    m.put("branchName", rs.getString("branch_name"));
                    m.put("status", rs.getString("status"));
                    m.put("createdBy", rs.getString("created_by"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    m.put("appliedAt", rs.getObject("applied_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();
    }

    // =====================================================================
    // Calidad de inventario (clasificación ABC)
    // =====================================================================

    /**
     * Calcula la calidad del inventario clasificando cada producto por su valor y su venta en el
     * rango dado: Alto, Medio, Bajo, Lento, Nuevo (alta reciente) y Sin movimiento. Devuelve las
     * filas por clasificación con #items, valor de inventario, costo de ventas, venta y utilidad.
     */
    public List<Map<String, Object>> inventoryQuality(LocalDate from, LocalDate to) {
        // Ventas por producto en el rango (unidades y monto).
        var sales = jdbc.sql("""
                SELECT sl.product_id,
                       COALESCE(SUM(sl.quantity), 0) AS qty_sold,
                       COALESCE(SUM(sl.line_total), 0) AS sold_amount
                FROM sale_line sl JOIN sale s ON s.id = sl.sale_id
                WHERE s.status = 'COMPLETED' AND s.created_at::date BETWEEN :from AND :to
                GROUP BY sl.product_id
                """)
                .param("from", from).param("to", to)
                .query((rs, n) -> new Object[] {
                        rs.getLong("product_id"), rs.getBigDecimal("qty_sold"), rs.getBigDecimal("sold_amount") })
                .list();
        Map<Long, BigDecimal[]> salesByProduct = new LinkedHashMap<>();
        for (Object[] r : sales) {
            salesByProduct.put((Long) r[0], new BigDecimal[] { (BigDecimal) r[1], (BigDecimal) r[2] });
        }

        // Productos con su valor de inventario y costo.
        var products = jdbc.sql("""
                SELECT p.id, p.cost, p.created_at,
                       COALESCE((SELECT SUM(quantity) FROM inventory_stock WHERE product_id = p.id), 0) AS stock
                FROM product p WHERE p.active = TRUE
                """)
                .query((rs, n) -> new Object[] {
                        rs.getLong("id"), rs.getBigDecimal("cost"),
                        rs.getObject("created_at", java.time.OffsetDateTime.class),
                        rs.getBigDecimal("stock") })
                .list();

        // Acumuladores por clasificación.
        String[] classes = { "Alto", "Medio", "Bajo", "Lento", "Nuevo", "Sin movimiento" };
        Map<String, BigDecimal[]> acc = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String c : classes) {
            acc.put(c, new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO });
            counts.put(c, 0);
        }

        java.time.OffsetDateTime newThreshold = java.time.OffsetDateTime.now().minusDays(30);
        for (Object[] p : products) {
            long id = (Long) p[0];
            BigDecimal cost = (BigDecimal) p[1];
            java.time.OffsetDateTime createdAt = (java.time.OffsetDateTime) p[2];
            BigDecimal stock = (BigDecimal) p[3];
            BigDecimal invValue = stock.multiply(cost);
            BigDecimal[] sd = salesByProduct.get(id);
            BigDecimal qtySold = sd == null ? BigDecimal.ZERO : sd[0];
            BigDecimal soldAmount = sd == null ? BigDecimal.ZERO : sd[1];
            BigDecimal costOfSales = qtySold.multiply(cost);
            BigDecimal profit = soldAmount.subtract(costOfSales);

            String cls;
            if (createdAt != null && createdAt.isAfter(newThreshold)) {
                cls = "Nuevo";
            } else if (qtySold.signum() == 0) {
                cls = "Sin movimiento";
            } else if (soldAmount.compareTo(new BigDecimal("10000")) >= 0) {
                cls = "Alto";
            } else if (soldAmount.compareTo(new BigDecimal("3000")) >= 0) {
                cls = "Medio";
            } else if (soldAmount.compareTo(new BigDecimal("500")) >= 0) {
                cls = "Bajo";
            } else {
                cls = "Lento";
            }
            BigDecimal[] a = acc.get(cls);
            a[0] = a[0].add(invValue);
            a[1] = a[1].add(costOfSales);
            a[2] = a[2].add(soldAmount);
            a[3] = a[3].add(profit);
            counts.put(cls, counts.get(cls) + 1);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String c : classes) {
            BigDecimal[] a = acc.get(c);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("classification", c);
            m.put("items", counts.get(c));
            m.put("inventoryValue", a[0]);
            m.put("costOfSales", a[1]);
            m.put("sales", a[2]);
            m.put("profit", a[3]);
            result.add(m);
        }
        return result;
    }

    // =====================================================================
    // Etiquetas de código de barras
    // =====================================================================

    /** Devuelve los datos para imprimir etiquetas de una lista de productos con su cantidad. */
    public List<Map<String, Object>> labelData(List<LabelRequestItem> items) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LabelRequestItem it : items) {
            var data = jdbc.sql("""
                    SELECT p.id, p.name, p.sku, p.price,
                           (SELECT barcode FROM product_barcode WHERE product_id = p.id ORDER BY id LIMIT 1) AS barcode
                    FROM product p WHERE p.id = :id
                    """)
                    .param("id", it.productId())
                    .query((rs, n) -> {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("productId", rs.getLong("id"));
                        m.put("name", rs.getString("name"));
                        m.put("sku", rs.getString("sku"));
                        m.put("price", rs.getBigDecimal("price"));
                        m.put("barcode", rs.getString("barcode"));
                        m.put("copies", it.copies());
                        return m;
                    })
                    .optional();
            data.ifPresent(out::add);
        }
        return out;
    }

    // =====================================================================
    // Kardex enriquecido
    // =====================================================================

    /** Kardex de un producto con nombre legible del tipo de movimiento y sucursal. */
    public List<Map<String, Object>> kardex(long productId, int limit) {
        int max = limit <= 0 ? 100 : Math.min(limit, 500);
        return jdbc.sql("""
                SELECT m.id, m.movement_type, m.quantity, m.balance_after, m.reason, m.reference,
                       m.actor, m.created_at, b.name AS branch_name
                FROM inventory_movement m LEFT JOIN branch b ON b.id = m.branch_id
                WHERE m.product_id = :p
                ORDER BY m.created_at DESC, m.id DESC
                LIMIT :max
                """)
                .param("p", productId).param("max", max)
                .query((rs, n) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("type", rs.getString("movement_type"));
                    m.put("quantity", rs.getBigDecimal("quantity"));
                    m.put("balanceAfter", rs.getBigDecimal("balance_after"));
                    m.put("reason", rs.getString("reason"));
                    m.put("reference", rs.getString("reference"));
                    m.put("actor", rs.getString("actor"));
                    m.put("branchName", rs.getString("branch_name"));
                    m.put("createdAt", rs.getObject("created_at", java.time.OffsetDateTime.class));
                    return m;
                })
                .list();
    }

    // =====================================================================
    // StockApp: importación de conteo/entradas (Excel pegado o QR)
    // =====================================================================

    /**
     * Importa un conjunto de líneas capturadas con la StockApp (o pegadas de Excel / leídas por
     * QR) y las convierte en:
     * <ul>
     *   <li>{@code ENTRY}: una entrada de inventario (documento IN) que suma existencias.</li>
     *   <li>{@code PHYSICAL}: un inventario físico que ajusta las existencias a lo contado.</li>
     * </ul>
     * Cada línea se resuelve por id de producto o por SKU/código de barras.
     *
     * @param mode     ENTRY o PHYSICAL
     * @param branchId almacén destino
     * @param items    líneas (código o id, cantidad, costo)
     * @param actor    quién importa
     * @return resumen con creados/no encontrados y el id del documento/conteo generado
     */
    @Transactional
    public Map<String, Object> importStockApp(String mode, long branchId, List<StockAppItem> items,
                                              String actor) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("No hay líneas para importar");
        }
        List<DocLineInput> resolved = new ArrayList<>();
        List<Object[]> countPairs = new ArrayList<>();  // [productId, quantity]
        List<String> notFound = new ArrayList<>();

        for (StockAppItem it : items) {
            Long productId = it.productId();
            if (productId == null && it.code() != null && !it.code().isBlank()) {
                productId = resolveProductId(it.code().trim());
            }
            if (productId == null) {
                notFound.add(it.code());
                continue;
            }
            BigDecimal qty = it.quantity() == null ? BigDecimal.ZERO : it.quantity();
            BigDecimal cost = it.cost() == null ? BigDecimal.ZERO : it.cost();
            resolved.add(new DocLineInput(productId, null, qty, cost));
            countPairs.add(new Object[] { productId, qty });
        }
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException("Ninguna línea pudo resolverse a un producto existente");
        }

        long docId;
        if ("PHYSICAL".equals(mode)) {
            docId = startPhysicalCount(branchId, null, actor);
            for (Object[] pair : countPairs) {
                captureCount(docId, (Long) pair[0], "STOCKAPP", (BigDecimal) pair[1]);
            }
            applyPhysicalCount(docId, actor);
        } else {
            docId = registerDocument("IN", null, branchId,
                    "Importación StockApp", resolved, actor);
        }
        return Map.of("mode", mode, "documentId", docId,
                "imported", resolved.size(), "notFound", notFound);
    }

    /** Resuelve un producto por id numérico, SKU o código de barras. */
    private Long resolveProductId(String code) {
        // ¿Es un id numérico existente?
        if (code.matches("\\d+")) {
            Long byId = jdbc.sql("SELECT id FROM product WHERE id = :id")
                    .param("id", Long.parseLong(code)).query(Long.class).optional().orElse(null);
            if (byId != null) {
                return byId;
            }
        }
        Long bySku = jdbc.sql("SELECT id FROM product WHERE sku = :c LIMIT 1")
                .param("c", code).query(Long.class).optional().orElse(null);
        if (bySku != null) {
            return bySku;
        }
        return jdbc.sql("SELECT product_id FROM product_barcode WHERE barcode = :c LIMIT 1")
                .param("c", code).query(Long.class).optional().orElse(null);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private static Map<String, Object> row(Map<String, Object> m) {
        return new LinkedHashMap<>(m);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    // Records de entrada
    public record DocLineInput(long productId, String description, BigDecimal quantity, BigDecimal unitCost) {
    }

    public record LabelRequestItem(long productId, int copies) {
    }

    /** Línea importada desde la StockApp (por id o por código/SKU). */
    public record StockAppItem(Long productId, String code, BigDecimal quantity, BigDecimal cost) {
    }
}
