package com.mybusinesssilva.catalog.adapters.out.persistence;

import com.mybusinesssilva.catalog.domain.model.Product;
import com.mybusinesssilva.catalog.domain.port.out.ProductRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Implementación JDBC del repositorio de productos. Opera sobre el schema del
 * tenant en curso
 * (el {@code search_path} lo fija el DataSource consciente del tenant). Los
 * atributos dinámicos
 * del giro se serializan/deserializan como JSONB.
 */
@Repository
public class JdbcProductRepository implements ProductRepository {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public JdbcProductRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public Product insert(Product p) {
        var x = p.extras();
        Long id = jdbc.sql("""
                INSERT INTO product
                    (sku, name, category_id, unit, sold_by_weight, sat_prod_serv, sat_unit,
                     price, cost, active, attributes, image_url,
                     description, brand, supplier_id, tax_rate, ieps_rate,
                     price2, price3, price4, price5, waste_price, last_cost, avg_cost,
                     min_stock, max_stock, reorder_point,
                     for_sale, track_inventory, track_lots, allow_below_cost, blocked,
                     is_composite, on_sale, loyalty_points, is_bakery)
                VALUES (:sku, :name, :categoryId, :unit, :weight, :satProd, :satUnit,
                        :price, :cost, :active, CAST(:attrs AS jsonb), :imageUrl,
                        :description, :brand, :supplierId, :taxRate, :iepsRate,
                        :price2, :price3, :price4, :price5, :wastePrice, :lastCost, :avgCost,
                        :minStock, :maxStock, :reorderPoint,
                        :forSale, :trackInventory, :trackLots, :allowBelowCost, :blocked,
                        :isComposite, :onSale, :loyaltyPoints, :isBakery)
                RETURNING id
                """)
                .param("sku", p.sku())
                .param("name", p.name())
                .param("categoryId", p.categoryId())
                .param("unit", p.unit())
                .param("weight", p.soldByWeight())
                .param("satProd", p.satProdServ())
                .param("satUnit", p.satUnit())
                .param("price", p.price())
                .param("cost", p.cost())
                .param("active", p.active())
                .param("attrs", toJson(p.attributes()))
                .param("imageUrl", p.imageUrl())
                .param("description", x.description())
                .param("brand", x.brand())
                .param("supplierId", x.supplierId())
                .param("taxRate", x.taxRate())
                .param("iepsRate", x.iepsRate())
                .param("price2", x.price2())
                .param("price3", x.price3())
                .param("price4", x.price4())
                .param("price5", x.price5())
                .param("wastePrice", x.wastePrice())
                .param("lastCost", x.lastCost())
                .param("avgCost", x.avgCost())
                .param("minStock", x.minStock())
                .param("maxStock", x.maxStock())
                .param("reorderPoint", x.reorderPoint())
                .param("forSale", x.forSale())
                .param("trackInventory", x.trackInventory())
                .param("trackLots", x.trackLots())
                .param("allowBelowCost", x.allowBelowCost())
                .param("blocked", x.blocked())
                .param("isComposite", x.isComposite())
                .param("onSale", x.onSale())
                .param("loyaltyPoints", x.loyaltyPoints())
                .param("isBakery", x.isBakery())
                .query(Long.class)
                .single();

        insertBarcodes(id, p.barcodes());
        return findById(id).orElseThrow();
    }

    @Override
    public void update(Product p) {
        var x = p.extras();
        jdbc.sql("""
                UPDATE product SET
                    sku = :sku, name = :name, category_id = :categoryId, unit = :unit,
                    sold_by_weight = :weight, sat_prod_serv = :satProd, sat_unit = :satUnit,
                    price = :price, cost = :cost, active = :active,
                    attributes = CAST(:attrs AS jsonb), image_url = :imageUrl,
                    description = :description, brand = :brand, supplier_id = :supplierId,
                    tax_rate = :taxRate, ieps_rate = :iepsRate,
                    price2 = :price2, price3 = :price3, price4 = :price4, price5 = :price5,
                    waste_price = :wastePrice,
                    last_cost = :lastCost, avg_cost = :avgCost,
                    min_stock = :minStock, max_stock = :maxStock, reorder_point = :reorderPoint,
                    for_sale = :forSale, track_inventory = :trackInventory, track_lots = :trackLots,
                    allow_below_cost = :allowBelowCost, blocked = :blocked,
                    is_composite = :isComposite, on_sale = :onSale, loyalty_points = :loyaltyPoints,
                    is_bakery = :isBakery,
                    updated_at = now()
                WHERE id = :id
                """)
                .param("id", p.id())
                .param("sku", p.sku())
                .param("name", p.name())
                .param("categoryId", p.categoryId())
                .param("unit", p.unit())
                .param("weight", p.soldByWeight())
                .param("satProd", p.satProdServ())
                .param("satUnit", p.satUnit())
                .param("price", p.price())
                .param("cost", p.cost())
                .param("active", p.active())
                .param("attrs", toJson(p.attributes()))
                .param("imageUrl", p.imageUrl())
                .param("description", x.description())
                .param("brand", x.brand())
                .param("supplierId", x.supplierId())
                .param("taxRate", x.taxRate())
                .param("iepsRate", x.iepsRate())
                .param("price2", x.price2())
                .param("price3", x.price3())
                .param("price4", x.price4())
                .param("price5", x.price5())
                .param("wastePrice", x.wastePrice())
                .param("lastCost", x.lastCost())
                .param("avgCost", x.avgCost())
                .param("minStock", x.minStock())
                .param("maxStock", x.maxStock())
                .param("reorderPoint", x.reorderPoint())
                .param("forSale", x.forSale())
                .param("trackInventory", x.trackInventory())
                .param("trackLots", x.trackLots())
                .param("allowBelowCost", x.allowBelowCost())
                .param("blocked", x.blocked())
                .param("isComposite", x.isComposite())
                .param("onSale", x.onSale())
                .param("loyaltyPoints", x.loyaltyPoints())
                .param("isBakery", x.isBakery())
                .update();

        jdbc.sql("DELETE FROM product_barcode WHERE product_id = :id").param("id", p.id()).update();
        insertBarcodes(p.id(), p.barcodes());
    }

    /**
     * Tablas que referencian product(id) y representan HISTORIAL o uso real
     * (ventas, kardex,
     * conteos físicos, documentos, compras, devoluciones, panadería, series). Si el
     * producto
     * aparece en cualquiera de ellas, NO se borra físicamente: se desactiva
     * (soft-delete) para
     * preservar la trazabilidad. Esta lista evita el fallo de foreign key al
     * borrar: en vez de
     * ir limpiando tabla por tabla (parche que olvida alguna), se decide por
     * existencia de
     * referencia. Las dependencias ACCESORIAS (stock, lotes, códigos de barras) no
     * están aquí
     * porque no son historial y se eliminan con el producto cuando sí procede el
     * borrado físico.
     */
    private static final java.util.List<String> HISTORY_TABLES = java.util.List.of(
            "sale_line",
            "inventory_movement",
            "physical_count_line",
            "inventory_document_line",
            "purchase_line",
            "sales_return_line",
            "purchase_return_line",
            "bakery_production_line",
            "bakery_waste_movement",
            "bakery_waste_sale",
            "product_serial");

    @Override
    public boolean delete(long id) {
        // Regla de raíz: un producto con CUALQUIER historial/uso real se DESACTIVA, no
        // se borra
        // (borrarlo rompería la trazabilidad y chocaría con las foreign keys). Solo se
        // elimina
        // físicamente un producto "virgen" (creado por error, sin ninguna referencia de
        // historial).
        if (hasHistory(id)) {
            jdbc.sql("UPDATE product SET active = FALSE, updated_at = now() WHERE id = :id")
                    .param("id", id).update();
            return false; // desactivado (soft-delete)
        }
        // Sin historial: se elimina físicamente junto con sus dependencias ACCESORIAS
        // (no-historial).
        // promotion_product y customer_special_price tienen ON DELETE CASCADE, así que
        // caen solas.
        jdbc.sql("DELETE FROM inventory_stock WHERE product_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM product_lot WHERE product_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM product_barcode WHERE product_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM product WHERE id = :id").param("id", id).update();
        return true; // borrado físico
    }

    /**
     * @return true si el producto está referenciado en alguna tabla de historial
     *         (ver
     *         {@link #HISTORY_TABLES}). Se consulta cada tabla con EXISTS y se
     *         corta en la primera
     *         coincidencia; las tablas que no existan en el esquema del tenant se
     *         ignoran.
     */
    private boolean hasHistory(long id) {
        for (String table : HISTORY_TABLES) {
            // Defensivo: solo consulta la tabla si existe en el esquema actual del tenant
            // (to_regclass devuelve NULL si no existe). Evita romper si un tenant no tiene
            // alguna tabla (p. ej. módulos no instalados).
            Boolean exists = jdbc.sql("SELECT to_regclass(:qualified) IS NOT NULL")
                    .param("qualified", table)
                    .query(Boolean.class)
                    .single();
            if (!Boolean.TRUE.equals(exists)) {
                continue;
            }
            Boolean referenced = jdbc.sql(
                    "SELECT EXISTS (SELECT 1 FROM " + table + " WHERE product_id = :id)")
                    .param("id", id)
                    .query(Boolean.class)
                    .single();
            if (Boolean.TRUE.equals(referenced)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<Product> findById(long id) {
        return jdbc.sql("SELECT * FROM product WHERE id = :id")
                .param("id", id)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public Optional<Product> findByBarcode(String barcode) {
        return jdbc.sql("""
                SELECT p.* FROM product p
                JOIN product_barcode b ON b.product_id = p.id
                WHERE b.barcode = :barcode
                """)
                .param("barcode", barcode)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public List<Product> search(String text) {
        return jdbc.sql("SELECT * FROM product WHERE lower(name) LIKE lower(:q) ORDER BY name LIMIT 50")
                .param("q", "%" + text + "%")
                .query(this::mapRow)
                .list();
    }

    @Override
    public List<Product> findAll() {
        // Optimización N+1: en lugar de una consulta de códigos de barras POR producto
        // (lentísimo con la BD en la nube: ~1 viaje por producto), se traen TODOS los
        // códigos
        // en una sola consulta y se agrupan en memoria por product_id. Así la carga del
        // catálogo
        // (p. ej. la cuadrícula del punto de venta) pasa de N+1 consultas a solo 2.
        java.util.Map<Long, List<String>> barcodesByProduct = new java.util.HashMap<>();
        jdbc.sql("SELECT product_id, barcode FROM product_barcode ORDER BY product_id, id")
                .query((rs, n) -> {
                    barcodesByProduct
                            .computeIfAbsent(rs.getLong("product_id"), k -> new ArrayList<>())
                            .add(rs.getString("barcode"));
                    return null;
                })
                .list();

        return jdbc.sql("SELECT * FROM product ORDER BY name")
                .query((rs, rowNum) -> mapRowWithBarcodes(rs,
                        barcodesByProduct.getOrDefault(rs.getLong("id"), List.of())))
                .list();
    }

    @Override
    public BigDecimal totalStockAllBranches(long productId) {
        return jdbc.sql("SELECT COALESCE(SUM(quantity), 0) FROM inventory_stock WHERE product_id = :id")
                .param("id", productId)
                .query(BigDecimal.class)
                .single();
    }

    private void insertBarcodes(long productId, List<String> barcodes) {
        for (String code : barcodes) {
            jdbc.sql("INSERT INTO product_barcode (product_id, barcode) VALUES (:pid, :code)")
                    .param("pid", productId)
                    .param("code", code)
                    .update();
        }
    }

    /**
     * Mapea una fila de producto consultando sus códigos de barras individualmente.
     * Usado por lecturas de UN solo producto (findById, byBarcode, search). Para
     * listados
     * grandes usar {@link #findAll()}, que evita el N+1 cargando los barcodes en
     * bloque.
     */
    private Product mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        long id = rs.getLong("id");
        List<String> barcodes = jdbc.sql(
                "SELECT barcode FROM product_barcode WHERE product_id = :id ORDER BY id")
                .param("id", id)
                .query(String.class)
                .list();
        return mapRowWithBarcodes(rs, barcodes);
    }

    /**
     * Mapea una fila de producto usando una lista de códigos de barras ya cargada
     * (sin consultar
     * la BD). Permite a {@link #findAll()} resolver todos los barcodes en una sola
     * consulta previa.
     */
    private Product mapRowWithBarcodes(java.sql.ResultSet rs, List<String> barcodes)
            throws java.sql.SQLException {
        long id = rs.getLong("id");
        Product.ProductExtras extras = new Product.ProductExtras(
                rs.getString("description"),
                rs.getString("brand"),
                (Long) rs.getObject("supplier_id"),
                rs.getBigDecimal("tax_rate"),
                rs.getBigDecimal("ieps_rate"),
                rs.getBigDecimal("price2"),
                rs.getBigDecimal("price3"),
                rs.getBigDecimal("price4"),
                rs.getBigDecimal("price5"),
                rs.getBigDecimal("waste_price"),
                rs.getBigDecimal("last_cost"),
                rs.getBigDecimal("avg_cost"),
                rs.getBigDecimal("min_stock"),
                rs.getBigDecimal("max_stock"),
                rs.getBigDecimal("reorder_point"),
                rs.getBoolean("for_sale"),
                rs.getBoolean("track_inventory"),
                rs.getBoolean("track_lots"),
                rs.getBoolean("allow_below_cost"),
                rs.getBoolean("blocked"),
                rs.getBoolean("is_composite"),
                rs.getBoolean("on_sale"),
                rs.getBigDecimal("loyalty_points"),
                rs.getBoolean("is_bakery"));
        return new Product(
                id,
                rs.getString("sku"),
                rs.getString("name"),
                (Long) rs.getObject("category_id"),
                rs.getString("unit"),
                rs.getBoolean("sold_by_weight"),
                rs.getString("sat_prod_serv"),
                rs.getString("sat_unit"),
                rs.getBigDecimal("price"),
                rs.getBigDecimal("cost"),
                rs.getBoolean("active"),
                barcodes,
                fromJson(rs.getString("attributes")),
                rs.getString("image_url"),
                extras);
    }

    private String toJson(Map<String, Object> attrs) {
        return objectMapper.writeValueAsString(attrs == null ? Map.of() : attrs);
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(json, MAP_TYPE);
    }
}
