package com.mybusinesssilva.licensing.adapters.out.persistence;

import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.model.BusinessStatus;
import com.mybusinesssilva.licensing.domain.model.ConnectionKind;
import com.mybusinesssilva.licensing.domain.port.out.BusinessRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Implementación JDBC del repositorio de negocios sobre el schema global {@code admin}.
 */
@Repository
public class JdbcBusinessRepository implements BusinessRepository {

    private final JdbcClient jdbc;

    public JdbcBusinessRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Business insert(Business b) {
        Long id = jdbc.sql("""
                INSERT INTO admin.business
                    (name, rfc, business_line, schema_name, subdomain, status, connection_kind,
                     trial_months, trial_starts_at, trial_ends_at, purchased_at, plan_id)
                VALUES (:name, :rfc, :line, :schema, :subdomain, :status, :conn,
                        :trialMonths, :trialStarts, :trialEnds, :purchased, :planId)
                RETURNING id
                """)
                .param("name", b.getName())
                .param("rfc", b.getRfc())
                .param("line", b.getBusinessLine())
                .param("schema", b.getSchemaName())
                .param("subdomain", b.getSubdomain())
                .param("status", b.getStatus().name())
                .param("conn", b.getConnectionKind().name())
                .param("trialMonths", b.getTrialMonths())
                .param("trialStarts", toTimestamp(b.getTrialStartsAt()))
                .param("trialEnds", toTimestamp(b.getTrialEndsAt()))
                .param("purchased", toTimestamp(b.getPurchasedAt()))
                .param("planId", b.getPlanId())
                .query(Long.class)
                .single();
        b.setId(id);
        return b;
    }

    @Override
    public void update(Business b) {
        jdbc.sql("""
                UPDATE admin.business SET
                    name = :name, rfc = :rfc, business_line = :line, schema_name = :schema,
                    subdomain = :subdomain, status = :status, connection_kind = :conn,
                    trial_months = :trialMonths, trial_starts_at = :trialStarts,
                    trial_ends_at = :trialEnds, purchased_at = :purchased, plan_id = :planId,
                    updated_at = now()
                WHERE id = :id
                """)
                .param("id", b.getId())
                .param("name", b.getName())
                .param("rfc", b.getRfc())
                .param("line", b.getBusinessLine())
                .param("schema", b.getSchemaName())
                .param("subdomain", b.getSubdomain())
                .param("status", b.getStatus().name())
                .param("conn", b.getConnectionKind().name())
                .param("trialMonths", b.getTrialMonths())
                .param("trialStarts", toTimestamp(b.getTrialStartsAt()))
                .param("trialEnds", toTimestamp(b.getTrialEndsAt()))
                .param("purchased", toTimestamp(b.getPurchasedAt()))
                .param("planId", b.getPlanId())
                .update();
    }

    @Override
    public void delete(long businessId) {
        // Borra dependencias del schema admin antes del negocio (evita violación de FK).
        jdbc.sql("DELETE FROM admin.business_module WHERE business_id = :id")
                .param("id", businessId)
                .update();
        jdbc.sql("DELETE FROM admin.superadmin_sale WHERE business_id = :id")
                .param("id", businessId)
                .update();
        jdbc.sql("DELETE FROM admin.business WHERE id = :id")
                .param("id", businessId)
                .update();
    }

    @Override
    public Optional<Business> findById(long id) {
        return jdbc.sql("SELECT * FROM admin.business WHERE id = :id")
                .param("id", id)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public Optional<Business> findBySchemaName(String schemaName) {
        return jdbc.sql("SELECT * FROM admin.business WHERE schema_name = :schema")
                .param("schema", schemaName)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public List<Business> findAll() {
        return jdbc.sql("SELECT * FROM admin.business ORDER BY id")
                .query(this::mapRow)
                .list();
    }

    @Override
    public List<Business> findTrialsDueForExpiration() {
        return jdbc.sql("""
                SELECT * FROM admin.business
                WHERE status = 'TRIAL' AND trial_ends_at IS NOT NULL AND trial_ends_at <= now()
                """)
                .query(this::mapRow)
                .list();
    }

    private Business mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return Business.rehydrate(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("rfc"),
                rs.getString("business_line"),
                rs.getString("schema_name"),
                rs.getString("subdomain"),
                BusinessStatus.valueOf(rs.getString("status")),
                ConnectionKind.valueOf(rs.getString("connection_kind")),
                rs.getInt("trial_months"),
                toInstant(rs.getTimestamp("trial_starts_at")),
                toInstant(rs.getTimestamp("trial_ends_at")),
                toInstant(rs.getTimestamp("purchased_at")),
                (Long) rs.getObject("plan_id"));
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
