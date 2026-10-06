package com.mybusinesssilva.customers.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cliente del negocio (CRM). Reúne los datos comerciales y los datos fiscales que exige la
 * facturación CFDI 4.0 (razón social, régimen fiscal, uso de CFDI y código postal del domicilio
 * fiscal). Puede tener varias direcciones (fiscal, de envío, sucursales del cliente).
 *
 * @param id             identificador (nulo hasta persistir)
 * @param name           nombre comercial
 * @param legalName      razón social / nombre fiscal exacto (CFDI 4.0)
 * @param personType     tipo de persona: {@code FISICA} o {@code MORAL}
 * @param rfc            RFC
 * @param taxRegime      régimen fiscal (clave SAT c_RegimenFiscal)
 * @param cfdiUse        uso de CFDI por defecto (clave SAT c_UsoCFDI)
 * @param zipCode        código postal del domicilio fiscal (CFDI 4.0)
 * @param phone          teléfono fijo
 * @param mobile         celular
 * @param email          correo
 * @param contactName    nombre del contacto principal
 * @param salesperson    vendedor asignado
 * @param creditLimit    límite de crédito autorizado
 * @param creditUsed     crédito utilizado (solo lectura desde persistencia)
 * @param creditDays     plazo de crédito en días
 * @param defaultPriceList lista de precio por defecto del cliente (1..5)
 * @param classification segmento comercial (VIP, mayorista, etc.)
 * @param externalCode   código del cliente interno del negocio
 * @param notes          notas
 * @param imageUrl       foto/logo (URL o data URL)
 * @param active         si está activo
 * @param addresses      direcciones asociadas
 */
public record Customer(
        Long id,
        String name,
        String legalName,
        String personType,
        String rfc,
        String taxRegime,
        String cfdiUse,
        String zipCode,
        String phone,
        String mobile,
        String email,
        String contactName,
        String salesperson,
        BigDecimal creditLimit,
        BigDecimal creditUsed,
        int creditDays,
        int defaultPriceList,
        String classification,
        String externalCode,
        String notes,
        String imageUrl,
        boolean active,
        List<CustomerAddress> addresses) {

    public Customer {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("El nombre del cliente es obligatorio");
        }
        personType = (personType == null || personType.isBlank()) ? "FISICA" : personType;
        cfdiUse = (cfdiUse == null || cfdiUse.isBlank()) ? "G03" : cfdiUse;
        creditLimit = creditLimit == null ? BigDecimal.ZERO : creditLimit;
        creditUsed = creditUsed == null ? BigDecimal.ZERO : creditUsed;
        defaultPriceList = defaultPriceList <= 0 ? 1 : defaultPriceList;
        addresses = addresses == null ? List.of() : List.copyOf(addresses);
    }

    /**
     * Dirección de un cliente.
     *
     * @param id           identificador (nulo hasta persistir)
     * @param kind         tipo: {@code FISCAL}, {@code SHIPPING} o {@code BRANCH}
     * @param label        alias de la dirección
     * @param street       calle
     * @param extNumber    número exterior
     * @param intNumber    número interior
     * @param neighborhood colonia
     * @param city         ciudad/municipio
     * @param state        estado
     * @param zipCode      código postal
     * @param country      país
     * @param reference    referencias de ubicación
     * @param isDefault    si es la dirección por defecto de su tipo
     */
    public record CustomerAddress(
            Long id,
            String kind,
            String label,
            String street,
            String extNumber,
            String intNumber,
            String neighborhood,
            String city,
            String state,
            String zipCode,
            String country,
            String reference,
            boolean isDefault) {

        public CustomerAddress {
            kind = (kind == null || kind.isBlank()) ? "SHIPPING" : kind;
            country = (country == null || country.isBlank()) ? "México" : country;
        }
    }
}
