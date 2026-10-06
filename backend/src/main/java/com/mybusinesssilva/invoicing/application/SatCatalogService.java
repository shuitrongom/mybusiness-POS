package com.mybusinesssilva.invoicing.application;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectura de los catálogos oficiales del SAT (viven en el schema admin, globales) y asignación de
 * claves SAT a productos y a líneas del negocio.
 *
 * <p>Corresponde a las pantallas "Catálogo SAT Productos" y "Catálogo SAT Líneas": buscar la clave
 * c_ClaveProdServ / c_ClaveUnidad y asignarla a un artículo concreto o, en bloque, a una línea.
 */
@Service
public class SatCatalogService {

    private final JdbcClient jdbc;

    public SatCatalogService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Busca claves de producto/servicio por texto o por clave (máx. 50). */
    public List<Map<String, Object>> searchProdServ(String query) {
        String q = query == null ? "" : query.trim();
        return jdbc.sql("""
                SELECT clave, descripcion FROM admin.sat_prod_serv
                WHERE :q = '' OR clave LIKE :like OR lower(descripcion) LIKE lower(:likeText)
                ORDER BY clave LIMIT 50
                """)
                .param("q", q)
                .param("like", q + "%")
                .param("likeText", "%" + q + "%")
                .query((rs, n) -> Map.<String, Object>of(
                        "clave", rs.getString("clave"),
                        "descripcion", rs.getString("descripcion")))
                .list();
    }

    /** Busca claves de unidad por texto o clave (máx. 50). */
    public List<Map<String, Object>> searchUnit(String query) {
        String q = query == null ? "" : query.trim();
        return jdbc.sql("""
                SELECT clave, nombre FROM admin.sat_unit
                WHERE :q = '' OR clave LIKE :like OR lower(nombre) LIKE lower(:likeText)
                ORDER BY clave LIMIT 50
                """)
                .param("q", q)
                .param("like", q + "%")
                .param("likeText", "%" + q + "%")
                .query((rs, n) -> Map.<String, Object>of(
                        "clave", rs.getString("clave"),
                        "nombre", rs.getString("nombre")))
                .list();
    }

    /** Catálogo de régimen fiscal (opcionalmente filtrado por tipo de persona). */
    public List<Map<String, Object>> regimes() {
        return simpleList("SELECT clave, descripcion FROM admin.sat_regimen_fiscal ORDER BY clave");
    }

    /** Catálogo de uso de CFDI. */
    public List<Map<String, Object>> usosCfdi() {
        return simpleList("SELECT clave, descripcion FROM admin.sat_uso_cfdi ORDER BY clave");
    }

    /** Catálogo de forma de pago. */
    public List<Map<String, Object>> formasPago() {
        return simpleList("SELECT clave, descripcion FROM admin.sat_forma_pago ORDER BY clave");
    }

    /** Catálogo de método de pago (PUE/PPD). */
    public List<Map<String, Object>> metodosPago() {
        return simpleList("SELECT clave, descripcion FROM admin.sat_metodo_pago ORDER BY clave");
    }

    /** Catálogo de monedas. */
    public List<Map<String, Object>> monedas() {
        return simpleList("SELECT clave, descripcion FROM admin.sat_moneda ORDER BY clave");
    }

    private List<Map<String, Object>> simpleList(String sql) {
        return jdbc.sql(sql)
                .query((rs, n) -> Map.<String, Object>of(
                        "clave", rs.getString("clave"),
                        "descripcion", rs.getString("descripcion")))
                .list();
    }

    // -------- Asignación de claves SAT --------

    /** Asigna clave SAT (prod/serv + unidad + objeto de impuesto + retenciones) a un producto. */
    @Transactional
    public void assignToProduct(long productId, String satProdServ, String satUnit,
                                String taxObject, java.math.BigDecimal retIva, java.math.BigDecimal retIsr) {
        jdbc.sql("""
                UPDATE product SET sat_prod_serv = :ps, sat_unit = :u,
                       sat_tax_object = :obj, sat_ret_iva = :riva, sat_ret_isr = :risr,
                       updated_at = now()
                WHERE id = :id
                """)
                .param("ps", satProdServ)
                .param("u", satUnit)
                .param("obj", taxObject == null ? "02" : taxObject)
                .param("riva", retIva == null ? java.math.BigDecimal.ZERO : retIva)
                .param("risr", retIsr == null ? java.math.BigDecimal.ZERO : retIsr)
                .param("id", productId)
                .update();
    }

    /** Lista productos con su clave SAT (para la pantalla de catálogo adicional de artículo). */
    public List<Map<String, Object>> productsWithSat(String query) {
        String q = query == null ? "" : query.trim();
        return jdbc.sql("""
                SELECT id, name, sat_prod_serv, sat_unit, sat_tax_object, sat_ret_iva, sat_ret_isr
                FROM product
                WHERE active = TRUE AND (:q = '' OR lower(name) LIKE lower(:like))
                ORDER BY name LIMIT 100
                """)
                .param("q", q)
                .param("like", "%" + q + "%")
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("name", rs.getString("name"));
                    m.put("satProdServ", rs.getString("sat_prod_serv"));
                    m.put("satUnit", rs.getString("sat_unit"));
                    m.put("taxObject", rs.getString("sat_tax_object"));
                    m.put("retIva", rs.getBigDecimal("sat_ret_iva"));
                    m.put("retIsr", rs.getBigDecimal("sat_ret_isr"));
                    return m;
                })
                .list();
    }

    /** Asigna clave SAT a una LÍNEA (categoría) y la propaga a los productos de esa categoría. */
    @Transactional
    public int assignToLine(String lineName, String satProdServ, String satUnit, String taxObject) {
        jdbc.sql("""
                INSERT INTO line_sat_key (line_name, sat_prod_serv, sat_unit, tax_object)
                VALUES (:line, :ps, :u, :obj)
                ON CONFLICT (line_name) DO UPDATE
                    SET sat_prod_serv = EXCLUDED.sat_prod_serv, sat_unit = EXCLUDED.sat_unit,
                        tax_object = EXCLUDED.tax_object
                """)
                .param("line", lineName)
                .param("ps", satProdServ)
                .param("u", satUnit == null ? "H87" : satUnit)
                .param("obj", taxObject == null ? "02" : taxObject)
                .update();

        // Propaga a los productos cuya categoría coincide con la línea.
        return jdbc.sql("""
                UPDATE product SET sat_prod_serv = :ps, sat_unit = :u, sat_tax_object = :obj,
                       updated_at = now()
                WHERE category_id IN (SELECT id FROM category WHERE lower(name) = lower(:line))
                """)
                .param("ps", satProdServ)
                .param("u", satUnit == null ? "H87" : satUnit)
                .param("obj", taxObject == null ? "02" : taxObject)
                .param("line", lineName)
                .update();
    }

    /** Lista las líneas (categorías) con su clave SAT asignada, si la tienen. */
    public List<Map<String, Object>> linesWithSat() {
        return jdbc.sql("""
                SELECT c.name AS line_name, k.sat_prod_serv, k.sat_unit, k.tax_object
                FROM category c
                LEFT JOIN line_sat_key k ON lower(k.line_name) = lower(c.name)
                ORDER BY c.name
                """)
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("lineName", rs.getString("line_name"));
                    m.put("satProdServ", rs.getString("sat_prod_serv"));
                    m.put("satUnit", rs.getString("sat_unit"));
                    m.put("taxObject", rs.getString("tax_object"));
                    return m;
                })
                .list();
    }
}
