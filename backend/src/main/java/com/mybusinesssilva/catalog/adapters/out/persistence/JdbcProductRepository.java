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
 * Implementación JDBC del repositorio de productos. Opera sobre el schema del tenant en curso
 * (el {@code search_path} lo fija el DataSource consciente del tenant). Los atributos dinámicos
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
                     price2, price3, price4, price5, last_cost, avg_cost,
                     min_stock, max_stock, reorder_point,
                     for_sale, track_inventory, track_lots, allow_below_cost, blocked,
                     is_composite, on_sale, loyalty_points)
                VALUES (:sku, :name, :categoryId, :unit, :weight, :satProd, :satUnit,
                        :price, :cost, :active, CAST(:attrs AS jsonb), :imageUrl,
                        :description, :brand, :supplierId, :taxRate, :iepsRate,
                        :price2, :price3, :price4, :price5, :lastCost, :avgCost,
                        :minStock, :maxStock, :reorderPoint,
                        :forSale, :trackInventory, :trackLots, :allowBelowCost, :blocked,
                        :isComposite, :onSale, :loyaltyPoints)
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
                    last_cost = :lastCost, avg_cost = :avgCost,
                    min_stock = :minStock, max_stock = :maxStock, reorder_point = :reorderPoint,
                    for_sale = :forSale, track_inventory = :trackInventory, track_lots = :trackLots,
                    allow_below_cost = :allowBelowCost, blocked = :blocked,
                    is_composite = :isComposite, on_sale = :onSale, loyalty_points = :loyaltyPoints,
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
                .update();

        jdbc.sql("DELETE FROM product_barcode WHERE product_id = :id").param("id", p.id()).update();
        insertBarcodes(p.id(), p.barcodes());
    }

    @Override
    public boolean delete(long id) {
        // Si el producto tiene ventas, no se borra físicamente (rompería el histórico): se desactiva.
        long soldTimes = jdbc.sql("SELECT count(*) FROM sale_line WHERE product_id = :id")
                .param("id", id).query(Long.class).single();
        if (soldTimes > 0) {
            jdbc.sql("UPDATE product SET active = FALSE, updated_at = now() WHERE id = :id")
                    .param("id", id).update();
            return false;
        }
        // Sin ventas: se elimina físicamente junto con sus dependencias directas.
        jdbc.sql("DELETE FROM inventory_movement WHERE product_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM inventory_stock WHERE product_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM product_lot WHERE product_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM product_barcode WHERE product_id = :id").param("id", id).update();
        jdbc.sql("DELETE FROM product WHERE id = :id").param("id", id).update();
        return true;
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
        return jdbc.sql("SELECT * FROM product ORDER BY name")
                .query(this::mapRow)
                .list();
    }

    private void insertBarcodes(long productId, List<String> barcodes) {
        for (String code : barcodes) {
            jdbc.sql("INSERT INTO product_barcode (product_id, barcode) VALUES (:pid, :code)")
                    .param("pid", productId)
                    .param("code", code)
                    .update();
        }
    }

    private Product mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        long id = rs.getLong("id");
        List<String> barcodes = jdbc.sql(
                        "SELECT barcode FROM product_barcode WHERE product_id = :id ORDER BY id")
                .param("id", id)
                .query(String.class)
                .list();
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
                rs.getBigDecimal("loyalty_points"));
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
