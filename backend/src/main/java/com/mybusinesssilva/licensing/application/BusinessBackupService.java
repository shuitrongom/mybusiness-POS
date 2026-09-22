package com.mybusinesssilva.licensing.application;

import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.port.out.BusinessRepository;
import com.mybusinesssilva.platform.audit.AuditService;
import com.mybusinesssilva.platform.tenancy.TenantSchema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Genera un respaldo completo de los datos de un negocio (tenant) como un mapa serializable a JSON.
 *
 * <p>Recorre dinámicamente las tablas del schema del tenant (según {@code information_schema})
 * y vuelca sus filas. Al ser dinámico, el respaldo sigue siendo válido aunque el esquema del
 * tenant evolucione con nuevas migraciones.
 *
 * <p>Se ejecuta consultando directamente el schema por nombre calificado, sin pasar por la RLS
 * de la sesión, porque es una operación administrativa del Super Admin sobre datos que ya tiene
 * autorización de exportar.
 */
@Service
public class BusinessBackupService {

    private final DataSource dataSource;
    private final BusinessRepository businessRepository;
    private final AuditService auditService;

    public BusinessBackupService(DataSource dataSource,
                                 BusinessRepository businessRepository,
                                 AuditService auditService) {
        this.dataSource = dataSource;
        this.businessRepository = businessRepository;
        this.auditService = auditService;
    }

    /**
     * Construye el respaldo del negocio indicado.
     *
     * @param actor      Super Admin que solicita el respaldo (para auditoría)
     * @param businessId identificador del negocio
     * @return respaldo con metadatos del negocio y el contenido de sus tablas
     */
    public BusinessBackup export(String actor, long businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Negocio inexistente: " + businessId));

        String schema = business.getSchemaName();
        TenantSchema.validate(schema);

        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        List<String> tables = jdbc.query(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = ? AND table_type = 'BASE TABLE' "
                        + "ORDER BY table_name",
                (rs, rowNum) -> rs.getString("table_name"), schema);

        Map<String, Object> data = new LinkedHashMap<>();
        long totalRows = 0;
        for (String table : tables) {
            // Excluye la tabla de historial de Flyway del respaldo de datos de negocio.
            if ("flyway_schema_history".equals(table)) {
                continue;
            }
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM " + schema + "." + quoteIdent(table));
            data.put(table, rows);
            totalRows += rows.size();
        }

        Map<String, Object> business_ = new LinkedHashMap<>();
        business_.put("id", business.getId());
        business_.put("name", business.getName());
        business_.put("rfc", business.getRfc());
        business_.put("businessLine", business.getBusinessLine());
        business_.put("schemaName", schema);
        business_.put("status", business.getStatus().name());
        business_.put("planId", business.getPlanId());

        auditService.recordGlobal(actor, "BUSINESS_BACKUP", "business",
                String.valueOf(businessId), Map.of("tables", tables.size(), "rows", totalRows));

        return new BusinessBackup(
                schema, Instant.now().toString(), business_, data);
    }

    /** Cita un identificador de tabla para evitar problemas con nombres reservados. */
    private static String quoteIdent(String ident) {
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }

    /**
     * Respaldo de un negocio.
     *
     * @param schema     schema del tenant respaldado
     * @param exportedAt momento de generación (ISO-8601)
     * @param business   metadatos del negocio
     * @param tables     mapa tabla → lista de filas (cada fila es columna → valor)
     */
    public record BusinessBackup(
            String schema,
            String exportedAt,
            Map<String, Object> business,
            Map<String, Object> tables) {
    }

    /** Devuelve un nombre de archivo sugerido para descargar el respaldo. */
    public static String suggestedFilename(BusinessBackup backup) {
        return "respaldo-" + backup.schema() + ".json";
    }

    /** Lista simple de nombres para exponer sin cargar todo el contenido. */
    public List<String> tableNames(long businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new IllegalArgumentException("Negocio inexistente: " + businessId));
        String schema = business.getSchemaName();
        TenantSchema.validate(schema);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        return new ArrayList<>(jdbc.query(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY table_name",
                (rs, rowNum) -> rs.getString("table_name"), schema));
    }
}
