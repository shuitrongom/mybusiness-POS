package com.mybusinesssilva.catalog.adapters.out.persistence;

import com.mybusinesssilva.catalog.domain.model.MasterProduct;
import com.mybusinesssilva.catalog.domain.port.out.MasterProductRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC del catálogo maestro compartido, en el schema {@code admin}.
 *
 * <p>Nota: estas consultas apuntan explícitamente a {@code admin.master_product} porque el
 * catálogo maestro es global (no pertenece a ningún tenant).
 */
@Repository
public class JdbcMasterProductRepository implements MasterProductRepository {

    private final JdbcClient jdbc;

    public JdbcMasterProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<MasterProduct> findByBarcode(String barcode) {
        return jdbc.sql("SELECT * FROM admin.master_product WHERE barcode = :b")
                .param("b", barcode)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public List<MasterProduct> search(String text) {
        return jdbc.sql("""
                SELECT * FROM admin.master_product
                WHERE lower(name) LIKE lower(:q) ORDER BY times_seen DESC, name LIMIT 50
                """)
                .param("q", "%" + text + "%")
                .query(this::mapRow)
                .list();
    }

    @Override
    public MasterProduct contribute(MasterProduct product) {
        if (product.barcode() != null) {
            Optional<MasterProduct> existing = findByBarcode(product.barcode());
            if (existing.isPresent()) {
                jdbc.sql("""
                        UPDATE admin.master_product
                        SET times_seen = times_seen + 1, updated_at = now()
                        WHERE barcode = :b
                        """)
                        .param("b", product.barcode())
                        .update();
                return existing.get();
            }
        }
        Long id = jdbc.sql("""
                INSERT INTO admin.master_product
                    (barcode, name, brand, category, unit, sat_prod_serv, sat_unit, source)
                VALUES (:barcode, :name, :brand, :category, :unit, :satProd, :satUnit, 'USER')
                RETURNING id
                """)
                .param("barcode", product.barcode())
                .param("name", product.name())
                .param("brand", product.brand())
                .param("category", product.category())
                .param("unit", product.unit())
                .param("satProd", product.satProdServ())
                .param("satUnit", product.satUnit())
                .query(Long.class)
                .single();
        return new MasterProduct(id, product.barcode(), product.name(), product.brand(),
                product.category(), product.unit(), product.satProdServ(), product.satUnit());
    }

    private MasterProduct mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new MasterProduct(
                rs.getLong("id"),
                rs.getString("barcode"),
                rs.getString("name"),
                rs.getString("brand"),
                rs.getString("category"),
                rs.getString("unit"),
                rs.getString("sat_prod_serv"),
                rs.getString("sat_unit"));
    }
}
