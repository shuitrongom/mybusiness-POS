package com.mybusinesssilva.purchasing.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Catálogo de impuestos REALES de México aplicables en compras, para no inventar tasas.
 *
 * <ul>
 *   <li><b>IVA 16%</b> — tasa general.</li>
 *   <li><b>IVA 8%</b> — región fronteriza norte/sur.</li>
 *   <li><b>IVA 0%</b> — alimentos básicos, medicinas, etc.</li>
 *   <li><b>EXENTO</b> — actos no objeto de IVA.</li>
 *   <li><b>IEPS</b> — impuesto especial (tasa variable según producto).</li>
 * </ul>
 *
 * Las retenciones (IVA retenido, ISR retenido) se manejan aparte a nivel de documento porque
 * dependen del tipo de operación (fletes, servicios, honorarios), no del producto.
 */
public final class MexicanTax {

    private MexicanTax() {
    }

    /** Una tasa de IVA de referencia. */
    public record VatRate(String code, String label, BigDecimal rate) {
    }

    public static final BigDecimal IVA_16 = new BigDecimal("0.16");
    public static final BigDecimal IVA_08 = new BigDecimal("0.08");
    public static final BigDecimal IVA_00 = BigDecimal.ZERO;

    /** Retención de IVA común (2/3 del IVA = 10.6667%) en servicios/fletes. */
    public static final BigDecimal IVA_RETENTION_FLETE = new BigDecimal("0.04");   // 4% autotransporte terrestre de carga
    /** Retención de ISR común en servicios profesionales/arrendamiento (10%). */
    public static final BigDecimal ISR_RETENTION = new BigDecimal("0.10");

    /** Catálogo de tasas de IVA que la UI puede ofrecer. */
    public static List<VatRate> vatRates() {
        return List.of(
                new VatRate("IVA16", "IVA 16%", IVA_16),
                new VatRate("IVA08", "IVA 8% (frontera)", IVA_08),
                new VatRate("IVA00", "IVA 0%", IVA_00),
                new VatRate("EXENTO", "Exento", IVA_00));
    }

    /**
     * Valida que una tasa de impuesto sea una permitida en México (evita tasas inventadas).
     * Acepta 0, 0.08 y 0.16 para IVA. El IEPS se valida por separado (tasa variable).
     *
     * @param rate tasa en fracción (0.16 = 16%)
     * @return la misma tasa si es válida
     */
    public static BigDecimal validateVat(BigDecimal rate) {
        if (rate == null) {
            return IVA_16;
        }
        if (rate.compareTo(IVA_16) == 0 || rate.compareTo(IVA_08) == 0
                || rate.compareTo(IVA_00) == 0) {
            return rate;
        }
        throw new IllegalArgumentException(
                "Tasa de IVA no válida en México: " + rate + " (permitidas: 0, 0.08, 0.16)");
    }
}
