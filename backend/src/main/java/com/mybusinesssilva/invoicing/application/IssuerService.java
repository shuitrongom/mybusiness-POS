package com.mybusinesssilva.invoicing.application;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datos fiscales del EMISOR (singleton por tenant) y sus series/folios.
 *
 * <p>Corresponde a la pantalla "Datos para factura Electrónica": nombre/razón social, RFC,
 * domicilio, régimen fiscal, series por tipo de comprobante, modo prueba y configuración del PAC.
 * Además administra las series y entrega el siguiente folio de forma consecutiva.
 */
@Service
public class IssuerService {

    private final JdbcClient jdbc;

    public IssuerService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Datos fiscales del emisor (fila única). */
    public Map<String, Object> getIssuer() {
        return jdbc.sql("""
                SELECT id, legal_name, rfc, tax_regime, street, ext_number, int_number,
                       neighborhood, locality, municipality, state, zip_code,
                       series_invoice, series_credit, series_payroll, series_payment, series_transfer,
                       csd_cer_number, test_mode, pac_provider, pac_user, pac_ws_url, decimals
                FROM cfdi_issuer LIMIT 1
                """)
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("legalName", rs.getString("legal_name"));
                    m.put("rfc", rs.getString("rfc"));
                    m.put("taxRegime", rs.getString("tax_regime"));
                    m.put("street", rs.getString("street"));
                    m.put("extNumber", rs.getString("ext_number"));
                    m.put("intNumber", rs.getString("int_number"));
                    m.put("neighborhood", rs.getString("neighborhood"));
                    m.put("locality", rs.getString("locality"));
                    m.put("municipality", rs.getString("municipality"));
                    m.put("state", rs.getString("state"));
                    m.put("zipCode", rs.getString("zip_code"));
                    m.put("seriesInvoice", rs.getString("series_invoice"));
                    m.put("seriesCredit", rs.getString("series_credit"));
                    m.put("seriesPayroll", rs.getString("series_payroll"));
                    m.put("seriesPayment", rs.getString("series_payment"));
                    m.put("seriesTransfer", rs.getString("series_transfer"));
                    m.put("csdCerNumber", rs.getString("csd_cer_number"));
                    m.put("testMode", rs.getBoolean("test_mode"));
                    m.put("pacProvider", rs.getString("pac_provider"));
                    m.put("pacUser", rs.getString("pac_user"));
                    m.put("pacWsUrl", rs.getString("pac_ws_url"));
                    m.put("decimals", rs.getInt("decimals"));
                    return m;
                })
                .optional()
                .orElseGet(java.util.LinkedHashMap::new);
    }

    /** Guarda (actualiza) los datos del emisor. */
    @Transactional
    public void saveIssuer(IssuerData d) {
        jdbc.sql("""
                UPDATE cfdi_issuer SET
                    legal_name = :legalName, rfc = :rfc, tax_regime = :taxRegime,
                    street = :street, ext_number = :extNumber, int_number = :intNumber,
                    neighborhood = :neighborhood, locality = :locality, municipality = :municipality,
                    state = :state, zip_code = :zipCode,
                    series_invoice = :seriesInvoice, series_credit = :seriesCredit,
                    series_payroll = :seriesPayroll, series_payment = :seriesPayment,
                    series_transfer = :seriesTransfer,
                    test_mode = :testMode, pac_provider = :pacProvider, pac_user = :pacUser,
                    pac_ws_url = :pacWsUrl, decimals = :decimals, updated_at = now()
                """)
                .param("legalName", d.legalName())
                .param("rfc", d.rfc())
                .param("taxRegime", d.taxRegime())
                .param("street", d.street())
                .param("extNumber", d.extNumber())
                .param("intNumber", d.intNumber())
                .param("neighborhood", d.neighborhood())
                .param("locality", d.locality())
                .param("municipality", d.municipality())
                .param("state", d.state())
                .param("zipCode", d.zipCode())
                .param("seriesInvoice", d.seriesInvoice())
                .param("seriesCredit", d.seriesCredit())
                .param("seriesPayroll", d.seriesPayroll())
                .param("seriesPayment", d.seriesPayment())
                .param("seriesTransfer", d.seriesTransfer())
                .param("testMode", d.testMode() == null ? Boolean.TRUE : d.testMode())
                .param("pacProvider", d.pacProvider())
                .param("pacUser", d.pacUser())
                .param("pacWsUrl", d.pacWsUrl())
                .param("decimals", d.decimals() == null ? 2 : d.decimals())
                .update();
    }

    /** Lista las series configuradas. */
    public List<Map<String, Object>> listSeries() {
        return jdbc.sql("""
                SELECT id, doc_type, series, last_folio, active
                FROM cfdi_series ORDER BY doc_type, series
                """)
                .query((rs, n) -> {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", rs.getLong("id"));
                    m.put("docType", rs.getString("doc_type"));
                    m.put("series", rs.getString("series"));
                    m.put("lastFolio", rs.getLong("last_folio"));
                    m.put("active", rs.getBoolean("active"));
                    return m;
                })
                .list();
    }

    /** Crea o actualiza una serie. */
    @Transactional
    public void saveSeries(String docType, String series, long lastFolio) {
        jdbc.sql("""
                INSERT INTO cfdi_series (doc_type, series, last_folio)
                VALUES (:type, :series, :folio)
                ON CONFLICT (doc_type, series) DO UPDATE SET last_folio = EXCLUDED.last_folio
                """)
                .param("type", docType)
                .param("series", series)
                .param("folio", lastFolio)
                .update();
    }

    /**
     * Entrega el siguiente folio consecutivo de una serie/tipo, incrementándolo de forma atómica.
     * Si la serie no existe, la crea empezando en 1.
     */
    @Transactional
    public long nextFolio(String docType, String series) {
        Long folio = jdbc.sql("""
                UPDATE cfdi_series SET last_folio = last_folio + 1
                WHERE doc_type = :type AND series = :series
                RETURNING last_folio
                """)
                .param("type", docType)
                .param("series", series)
                .query(Long.class)
                .optional()
                .orElse(null);
        if (folio == null) {
            jdbc.sql("INSERT INTO cfdi_series (doc_type, series, last_folio) VALUES (:type, :series, 1)")
                    .param("type", docType).param("series", series).update();
            return 1L;
        }
        return folio;
    }

    /** Datos fiscales del emisor recibidos del cliente. */
    public record IssuerData(
            String legalName, String rfc, String taxRegime,
            String street, String extNumber, String intNumber, String neighborhood,
            String locality, String municipality, String state, String zipCode,
            String seriesInvoice, String seriesCredit, String seriesPayroll,
            String seriesPayment, String seriesTransfer,
            Boolean testMode, String pacProvider, String pacUser, String pacWsUrl, Integer decimals) {
    }
}
