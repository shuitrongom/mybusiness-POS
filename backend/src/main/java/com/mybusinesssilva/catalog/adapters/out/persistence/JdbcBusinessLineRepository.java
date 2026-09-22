package com.mybusinesssilva.catalog.adapters.out.persistence;

import com.mybusinesssilva.catalog.domain.model.BusinessLine;
import com.mybusinesssilva.catalog.domain.port.out.BusinessLineRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC del repositorio de giros sobre el schema global {@code admin}.
 */
@Repository
public class JdbcBusinessLineRepository implements BusinessLineRepository {

    private final JdbcClient jdbc;

    public JdbcBusinessLineRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<BusinessLine> findAll() {
        return jdbc.sql("SELECT code, name, description FROM admin.business_line ORDER BY name")
                .query(this::mapRow)
                .list();
    }

    @Override
    public Optional<BusinessLine> findByCode(String code) {
        return jdbc.sql("SELECT code, name, description FROM admin.business_line WHERE code = :code")
                .param("code", code)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public void insert(BusinessLine line) {
        jdbc.sql("""
                INSERT INTO admin.business_line (code, name, description)
                VALUES (:code, :name, :desc)
                """)
                .param("code", line.code())
                .param("name", line.name())
                .param("desc", line.description())
                .update();
    }

    @Override
    public void update(BusinessLine line) {
        jdbc.sql("""
                UPDATE admin.business_line SET name = :name, description = :desc WHERE code = :code
                """)
                .param("code", line.code())
                .param("name", line.name())
                .param("desc", line.description())
                .update();
    }

    @Override
    public void deleteByCode(String code) {
        jdbc.sql("DELETE FROM admin.business_line WHERE code = :code")
                .param("code", code)
                .update();
    }

    @Override
    public long countBusinessesUsing(String code) {
        return jdbc.sql("SELECT count(*) FROM admin.business WHERE business_line = :code")
                .param("code", code)
                .query(Long.class)
                .single();
    }

    private BusinessLine mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new BusinessLine(
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"));
    }
}
