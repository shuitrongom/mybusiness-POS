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

    /**
     * Restaura un negocio a partir de un respaldo JSON, creándolo como un negocio NUEVO (no pisa
     * datos existentes). Registra el negocio con los metadatos del respaldo, aprovisiona su schema
     * (Flyway) y vuelca las filas de cada tabla del respaldo.
     *
     * <p>Se ejecuta con permisos administrativos sobre el schema recién creado. Para cada tabla,
     * se insertan las filas tal cual venían en el respaldo (incluidas sus llaves), y al terminar se
     * ajustan las secuencias de identidad para que los próximos IDs no colisionen.
     *
     * @param actor        Super Admin que restaura (para auditoría)
     * @param backup       contenido del respaldo (mapa deserializado del JSON)
     * @param registrar    servicio para registrar el negocio en el schema admin
     * @param provisioner  servicio que aprovisiona el schema del tenant (Flyway)
     * @return el negocio restaurado (nuevo)
     */
    public Business restore(String actor, Map<String, Object> backup,
                            RestoreRegistrar registrar, RestoreProvisioner provisioner) {
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) backup.get("business");
        if (meta == null) {
            throw new IllegalArgumentException("El archivo de respaldo no tiene datos del negocio.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> tables = (Map<String, Object>) backup.get("tables");
        if (tables == null) {
            throw new IllegalArgumentException("El archivo de respaldo no tiene tablas de datos.");
        }

        String name = String.valueOf(meta.getOrDefault("name", "Negocio restaurado"));
        String rfc = meta.get("rfc") == null ? null : String.valueOf(meta.get("rfc"));
        String businessLine = meta.get("businessLine") == null ? "abarrotes"
                : String.valueOf(meta.get("businessLine"));
        Long planId = meta.get("planId") == null ? null
                : Long.valueOf(String.valueOf(meta.get("planId")));

        // 1) Registra el negocio nuevo (schema admin) y aprovisiona su schema de datos (Flyway).
        Business restored = registrar.registerForRestore(actor, name, rfc, businessLine, planId);
        String schema = restored.getSchemaName();
        TenantSchema.validate(schema);
        provisioner.provisionSchema(schema);

        // 2) Vuelca las filas del respaldo tabla por tabla.
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        long inserted = insertRows(jdbc, schema, tables);

        auditService.recordGlobal(actor, "BUSINESS_RESTORED", "business",
                String.valueOf(restored.getId()),
                Map.of("schema", schema, "rows", inserted));

        return restored;
    }

    /**
     * Inserta las filas del respaldo en el schema destino. Fija {@code app.current_tenant} en la
     * sesión (dentro de una única conexión) para satisfacer la RLS, y ajusta las secuencias de
     * identidad al final. Devuelve el total de filas insertadas.
     */
    private long insertRows(JdbcTemplate jdbc, String schema, Map<String, Object> tables) {
        String tenantId = schema; // la RLS compara tenant_id::text con app.current_tenant
        return jdbc.execute((java.sql.Connection conn) -> {
            long total = 0;
            try (java.sql.Statement st = conn.createStatement()) {
                st.execute("SET search_path TO " + schema + ", admin");
                st.execute("SET app.current_tenant = '" + tenantId.replace("'", "''") + "'");
            }
            for (Map.Entry<String, Object> entry : tables.entrySet()) {
                String table = entry.getKey();
                if ("flyway_schema_history".equals(table)) {
                    continue;
                }
                if (!(entry.getValue() instanceof List<?> rows) || rows.isEmpty()) {
                    continue;
                }
                for (Object rowObj : rows) {
                    if (!(rowObj instanceof Map<?, ?> row) || row.isEmpty()) {
                        continue;
                    }
                    List<String> cols = new ArrayList<>();
                    List<Object> vals = new ArrayList<>();
                    for (Map.Entry<?, ?> c : row.entrySet()) {
                        cols.add(String.valueOf(c.getKey()));
                        vals.add(c.getValue());
                    }
                    String colList = cols.stream().map(BusinessBackupService::quoteIdent)
                            .reduce((a, b) -> a + ", " + b).orElse("");
                    String placeholders = cols.stream().map(c -> "?")
                            .reduce((a, b) -> a + ", " + b).orElse("");
                    String sql = "INSERT INTO " + schema + "." + quoteIdent(table)
                            + " (" + colList + ") VALUES (" + placeholders + ") ON CONFLICT DO NOTHING";
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                        for (int i = 0; i < vals.size(); i++) {
                            ps.setObject(i + 1, vals.get(i));
                        }
                        ps.executeUpdate();
                        total++;
                    } catch (java.sql.SQLException ex) {
                        // Una fila incompatible (columna que ya no existe, etc.) no aborta todo el
                        // proceso: se omite y se continúa con el resto.
                    }
                }
                // Ajusta la secuencia de identidad de la tabla (si tiene columna id serial).
                try (java.sql.Statement st = conn.createStatement()) {
                    st.execute("SELECT setval(pg_get_serial_sequence('" + schema + "." + table
                            + "', 'id'), COALESCE((SELECT MAX(id) FROM " + schema + "."
                            + quoteIdent(table) + "), 1))");
                } catch (java.sql.SQLException ignore) {
                    // La tabla puede no tener columna id serial; se ignora.
                }
            }
            return total;
        });
    }

    /** Puerto mínimo para registrar el negocio nuevo durante la restauración. */
    public interface RestoreRegistrar {
        Business registerForRestore(String actor, String name, String rfc,
                                    String businessLine, Long planId);
    }

    /** Puerto mínimo para aprovisionar el schema del tenant durante la restauración. */
    public interface RestoreProvisioner {
        String provisionSchema(String schema);
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
