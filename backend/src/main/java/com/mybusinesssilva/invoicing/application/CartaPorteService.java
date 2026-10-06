package com.mybusinesssilva.invoicing.application;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catálogos del complemento Carta Porte 3.1 (autotransporte): permisos SICT, vehículos, remolques,
 * operadores y ubicaciones. Corresponde a la pantalla "Catálogos Carta Porte".
 *
 * <p>El complemento Carta Porte se adjunta a un CFDI de Ingreso (cuando se cobra el flete) o de
 * Traslado (mercancía propia) para amparar el traslado de mercancías en territorio nacional.
 * Aquí se administran los datos maestros; la emisión del CFDI con el complemento se hace desde
 * {@link InvoicingService} marcando {@code has_carta_porte}.
 */
@Service
public class CartaPorteService {

    private final JdbcClient jdbc;

    public CartaPorteService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------- Permisos ----------------
    public List<Map<String, Object>> listPermisos() {
        return jdbc.sql("SELECT id, tipo_permiso, numero, descripcion, active FROM cp_permiso WHERE active = TRUE ORDER BY id DESC")
                .query((rs, n) -> Map.<String, Object>of(
                        "id", rs.getLong("id"),
                        "tipoPermiso", nz(rs.getString("tipo_permiso")),
                        "numero", nz(rs.getString("numero")),
                        "descripcion", nz(rs.getString("descripcion"))))
                .list();
    }

    @Transactional
    public long addPermiso(String tipo, String numero, String descripcion) {
        return jdbc.sql("INSERT INTO cp_permiso (tipo_permiso, numero, descripcion) VALUES (:t, :n, :d) RETURNING id")
                .param("t", tipo).param("n", numero).param("d", descripcion)
                .query(Long.class).single();
    }

    // ---------------- Vehículos ----------------
    public List<Map<String, Object>> listVehiculos() {
        return jdbc.sql("SELECT id, config_vehic, placa, anio_modelo, aseguradora, poliza_seguro FROM cp_vehiculo WHERE active = TRUE ORDER BY id DESC")
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("configVehic", nz(rs.getString("config_vehic")));
                    m.put("placa", nz(rs.getString("placa")));
                    m.put("anioModelo", (Object) rs.getObject("anio_modelo"));
                    m.put("aseguradora", nz(rs.getString("aseguradora")));
                    m.put("polizaSeguro", nz(rs.getString("poliza_seguro")));
                    return m;
                })
                .list();
    }

    @Transactional
    public long addVehiculo(String config, String placa, Integer anio, String aseguradora, String poliza) {
        return jdbc.sql("""
                INSERT INTO cp_vehiculo (config_vehic, placa, anio_modelo, aseguradora, poliza_seguro)
                VALUES (:c, :p, :a, :s, :po) RETURNING id
                """)
                .param("c", config).param("p", placa).param("a", anio)
                .param("s", aseguradora).param("po", poliza)
                .query(Long.class).single();
    }

    // ---------------- Remolques ----------------
    public List<Map<String, Object>> listRemolques() {
        return jdbc.sql("SELECT id, subtipo, placa FROM cp_remolque WHERE active = TRUE ORDER BY id DESC")
                .query((rs, n) -> Map.<String, Object>of(
                        "id", rs.getLong("id"),
                        "subtipo", nz(rs.getString("subtipo")),
                        "placa", nz(rs.getString("placa"))))
                .list();
    }

    @Transactional
    public long addRemolque(String subtipo, String placa) {
        return jdbc.sql("INSERT INTO cp_remolque (subtipo, placa) VALUES (:s, :p) RETURNING id")
                .param("s", subtipo).param("p", placa).query(Long.class).single();
    }

    // ---------------- Operadores ----------------
    public List<Map<String, Object>> listOperadores() {
        return jdbc.sql("SELECT id, nombre, rfc, curp, num_licencia FROM cp_operador WHERE active = TRUE ORDER BY id DESC")
                .query((rs, n) -> Map.<String, Object>of(
                        "id", rs.getLong("id"),
                        "nombre", nz(rs.getString("nombre")),
                        "rfc", nz(rs.getString("rfc")),
                        "curp", nz(rs.getString("curp")),
                        "numLicencia", nz(rs.getString("num_licencia"))))
                .list();
    }

    @Transactional
    public long addOperador(String nombre, String rfc, String curp, String licencia) {
        return jdbc.sql("INSERT INTO cp_operador (nombre, rfc, curp, num_licencia) VALUES (:n, :r, :c, :l) RETURNING id")
                .param("n", nombre).param("r", rfc).param("c", curp).param("l", licencia)
                .query(Long.class).single();
    }

    // ---------------- Ubicaciones ----------------
    public List<Map<String, Object>> listUbicaciones() {
        return jdbc.sql("""
                SELECT id, tipo, nombre, rfc, calle, num_ext, colonia, municipio, estado, pais, cp
                FROM cp_ubicacion WHERE active = TRUE ORDER BY id DESC
                """)
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("tipo", nz(rs.getString("tipo")));
                    m.put("nombre", nz(rs.getString("nombre")));
                    m.put("rfc", nz(rs.getString("rfc")));
                    m.put("calle", nz(rs.getString("calle")));
                    m.put("numExt", nz(rs.getString("num_ext")));
                    m.put("colonia", nz(rs.getString("colonia")));
                    m.put("municipio", nz(rs.getString("municipio")));
                    m.put("estado", nz(rs.getString("estado")));
                    m.put("pais", nz(rs.getString("pais")));
                    m.put("cp", nz(rs.getString("cp")));
                    return m;
                })
                .list();
    }

    @Transactional
    public long addUbicacion(UbicacionData d) {
        return jdbc.sql("""
                INSERT INTO cp_ubicacion (tipo, nombre, rfc, calle, num_ext, colonia, municipio, estado, pais, cp)
                VALUES (:tipo, :nombre, :rfc, :calle, :ext, :col, :mun, :edo, :pais, :cp) RETURNING id
                """)
                .param("tipo", d.tipo() == null ? "Origen" : d.tipo())
                .param("nombre", d.nombre()).param("rfc", d.rfc()).param("calle", d.calle())
                .param("ext", d.numExt()).param("col", d.colonia()).param("mun", d.municipio())
                .param("edo", d.estado()).param("pais", d.pais() == null ? "MEX" : d.pais())
                .param("cp", d.cp())
                .query(Long.class).single();
    }

    @Transactional
    public void delete(String catalog, long id) {
        String table = switch (catalog) {
            case "permiso" -> "cp_permiso";
            case "vehiculo" -> "cp_vehiculo";
            case "remolque" -> "cp_remolque";
            case "operador" -> "cp_operador";
            case "ubicacion" -> "cp_ubicacion";
            default -> throw new IllegalArgumentException("Catálogo carta porte desconocido: " + catalog);
        };
        jdbc.sql("UPDATE " + table + " SET active = FALSE WHERE id = :id").param("id", id).update();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Datos de una ubicación de carta porte. */
    public record UbicacionData(String tipo, String nombre, String rfc, String calle,
                                String numExt, String colonia, String municipio, String estado,
                                String pais, String cp) {
    }
}
