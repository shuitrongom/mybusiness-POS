package com.mybusinesssilva.invoicing.domain.model;

/**
 * Datos fiscales del receptor requeridos por CFDI 4.0.
 *
 * @param rfc      RFC del receptor
 * @param name     nombre o razón social exactamente como en la constancia fiscal
 * @param zip      código postal del domicilio fiscal
 * @param regime   régimen fiscal del receptor (clave SAT)
 * @param cfdiUse  uso del CFDI (clave SAT: G01, G03, S01, etc.)
 */
public record ReceiverInfo(
        String rfc,
        String name,
        String zip,
        String regime,
        String cfdiUse) {

    public ReceiverInfo {
        if (rfc == null || rfc.isBlank()) {
            throw new IllegalArgumentException("El RFC del receptor es obligatorio");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del receptor es obligatorio");
        }
        if (zip == null || zip.isBlank()) {
            throw new IllegalArgumentException("El código postal del receptor es obligatorio");
        }
        if (regime == null || regime.isBlank()) {
            throw new IllegalArgumentException("El régimen fiscal del receptor es obligatorio");
        }
        if (cfdiUse == null || cfdiUse.isBlank()) {
            throw new IllegalArgumentException("El uso del CFDI es obligatorio");
        }
    }
}
