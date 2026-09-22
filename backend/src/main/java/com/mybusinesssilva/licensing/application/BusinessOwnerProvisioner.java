package com.mybusinesssilva.licensing.application;

import com.mybusinesssilva.platform.tenancy.TenantContext;
import java.security.SecureRandom;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea el usuario Dueño inicial dentro del schema de un negocio recién aprovisionado, y le asigna
 * el rol OWNER. Devuelve las credenciales generadas para que el Super Admin las entregue al cliente.
 *
 * <p>La contraseña se genera aleatoriamente y se almacena con hash Argon2id (nunca en claro).
 * El Super Admin la ve UNA sola vez, al momento de crear el negocio.
 */
@Component
public class BusinessOwnerProvisioner {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final JdbcClient jdbc;
    private final PasswordEncoder passwordEncoder;

    public BusinessOwnerProvisioner(JdbcClient jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Crea el usuario dueño en el schema del tenant indicado.
     *
     * <p>El usuario queda marcado con {@code must_change_password = true} para forzar el cambio
     * de la contraseña temporal en su primer ingreso, y se guarda su WhatsApp de contacto.
     *
     * @param schema   schema del tenant (por ejemplo {@code tenant_12})
     * @param email    correo del dueño (identificador de acceso)
     * @param fullName nombre del dueño
     * @param whatsapp WhatsApp de contacto del dueño (puede ser nulo)
     * @return las credenciales generadas (contraseña en claro, solo para mostrar una vez)
     */
    public OwnerCredentials createOwner(String schema, String email, String fullName, String whatsapp) {
        String password = generatePassword();

        // Preserva el tenant que hubiera en curso para restaurarlo al terminar.
        String previousTenant = TenantContext.getTenantId();
        // Fija el tenant para que las tablas del schema correcto se usen y la RLS aplique.
        // Nota: NO se usa @Transactional aquí a propósito. El proxy transaccional abre la
        // conexión al ENTRAR al método (antes de fijar el tenant), y esa conexión quedaría
        // apuntando al schema equivocado. Sin transacción, cada consulta obtiene la conexión
        // ya con el search_path del tenant aplicado por TenantAwareDataSource.
        TenantContext.setTenantId(schema);
        try {
            Long roleId = jdbc.sql("SELECT id FROM role WHERE code = 'OWNER'")
                    .query(Long.class)
                    .optional()
                    .orElse(null);

            jdbc.sql("""
                    INSERT INTO app_user
                        (email, password_hash, full_name, role_id, whatsapp, must_change_password)
                    VALUES (:email, :hash, :name, :role, :whatsapp, TRUE)
                    """)
                    .param("email", email)
                    .param("hash", passwordEncoder.encode(password))
                    .param("name", fullName)
                    .param("role", roleId)
                    .param("whatsapp", whatsapp)
                    .update();
        } finally {
            if (previousTenant != null) {
                TenantContext.setTenantId(previousTenant);
            } else {
                TenantContext.clear();
            }
        }

        return new OwnerCredentials(email, password);
    }

    /**
     * Crea la sucursal principal ("Matriz") del negocio si aún no existe ninguna. Un negocio
     * necesita al menos una sucursal para poder registrar ventas (el POS opera contra ella).
     *
     * @param schema schema del tenant
     */
    public void createDefaultBranch(String schema) {
        String previousTenant = TenantContext.getTenantId();
        TenantContext.setTenantId(schema);
        try {
            Long branchId = jdbc.sql("SELECT id FROM branch ORDER BY id LIMIT 1")
                    .query(Long.class)
                    .optional()
                    .orElse(null);
            if (branchId == null) {
                branchId = jdbc.sql(
                        "INSERT INTO branch (name, code, active) VALUES ('Matriz', 'MATRIZ', TRUE) RETURNING id")
                        .query(Long.class)
                        .single();
            }
            // Crea la caja registradora inicial de la sucursal para poder abrir turnos y cobrar.
            long registers = jdbc.sql("SELECT count(*) FROM cash_register WHERE branch_id = :b")
                    .param("b", branchId)
                    .query(Long.class)
                    .single();
            if (registers == 0) {
                jdbc.sql("INSERT INTO cash_register (branch_id, name, active) VALUES (:b, 'Caja 1', TRUE)")
                        .param("b", branchId)
                        .update();
            }
        } finally {
            if (previousTenant != null) {
                TenantContext.setTenantId(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    /**
     * Categorías del catálogo maestro que se siembran para cada giro. Así un negocio nuevo arranca
     * con productos frecuentes precargados (el dueño solo ajusta precios), en vez de un catálogo
     * vacío. Un giro sin mapeo recibe las categorías generales de abarrotes.
     */
    private static final java.util.Map<String, java.util.List<String>> LINE_CATEGORIES =
            java.util.Map.of(
                    "abarrotes", java.util.List.of("Bebidas", "Botanas", "Panadería", "Lácteos", "Abarrotes"),
                    "materias_primas", java.util.List.of("Materias primas", "Abarrotes"),
                    "panaderia", java.util.List.of("Panadería"),
                    "polleria", java.util.List.of("Pollería"));

    /**
     * Siembra el catálogo del negocio con productos del catálogo maestro que correspondan a su
     * giro. Crea las categorías necesarias en el tenant y los productos (precio 0, para ajustar),
     * con su unidad, claves SAT y código de barras. Solo siembra si el catálogo está vacío.
     *
     * @param schema       schema del tenant
     * @param businessLine giro del negocio
     */
    public void seedCatalogFromMaster(String schema, String businessLine) {
        java.util.List<String> categories = LINE_CATEGORIES.getOrDefault(
                businessLine == null ? "" : businessLine.toLowerCase(),
                LINE_CATEGORIES.get("abarrotes"));

        // Lee del catálogo maestro (schema admin) ANTES de fijar el tenant, para no cruzar contextos.
        java.util.List<MasterRow> masterRows = jdbc.sql("""
                SELECT name, category, unit, sat_prod_serv, sat_unit, barcode
                FROM admin.master_product
                WHERE category = ANY(:cats)
                ORDER BY category, name
                """)
                .param("cats", categories.toArray(new String[0]))
                .query((rs, n) -> new MasterRow(
                        rs.getString("name"), rs.getString("category"), rs.getString("unit"),
                        rs.getString("sat_prod_serv"), rs.getString("sat_unit"), rs.getString("barcode")))
                .list();

        if (masterRows.isEmpty()) {
            return;
        }

        String previousTenant = TenantContext.getTenantId();
        TenantContext.setTenantId(schema);
        try {
            long existing = jdbc.sql("SELECT count(*) FROM product").query(Long.class).single();
            if (existing > 0) {
                return; // No pisar un catálogo que ya tiene productos.
            }

            // Crea las categorías del tenant y guarda su id por nombre.
            java.util.Map<String, Long> categoryIds = new java.util.HashMap<>();
            for (String cat : categories) {
                Long id = jdbc.sql("""
                        INSERT INTO category (name) VALUES (:name)
                        ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
                        RETURNING id
                        """)
                        .param("name", cat)
                        .query(Long.class)
                        .single();
                categoryIds.put(cat, id);
            }

            for (MasterRow row : masterRows) {
                boolean byWeight = "kg".equalsIgnoreCase(row.unit()) || "litro".equalsIgnoreCase(row.unit());
                Long productId = jdbc.sql("""
                        INSERT INTO product
                            (name, category_id, unit, sold_by_weight, sat_prod_serv, sat_unit, price, cost, active)
                        VALUES (:name, :cat, :unit, :byWeight, :prodServ, :unitSat, 0, 0, TRUE)
                        RETURNING id
                        """)
                        .param("name", row.name())
                        .param("cat", categoryIds.get(row.category()))
                        .param("unit", row.unit())
                        .param("byWeight", byWeight)
                        .param("prodServ", row.satProdServ())
                        .param("unitSat", row.satUnit())
                        .query(Long.class)
                        .single();

                if (row.barcode() != null && !row.barcode().isBlank()) {
                    jdbc.sql("INSERT INTO product_barcode (product_id, barcode) VALUES (:pid, :bc) "
                            + "ON CONFLICT (barcode) DO NOTHING")
                            .param("pid", productId)
                            .param("bc", row.barcode())
                            .update();
                }
            }
        } finally {
            if (previousTenant != null) {
                TenantContext.setTenantId(previousTenant);
            } else {
                TenantContext.clear();
            }
        }
    }

    /** Fila del catálogo maestro usada al sembrar el catálogo de un negocio nuevo. */
    private record MasterRow(String name, String category, String unit,
                             String satProdServ, String satUnit, String barcode) {
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Credenciales del dueño generadas al crear el negocio.
     *
     * @param email    correo de acceso
     * @param password contraseña en claro (mostrar una sola vez; luego solo queda el hash)
     */
    public record OwnerCredentials(String email, String password) {
    }
}
