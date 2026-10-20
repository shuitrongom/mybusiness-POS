package com.mybusinesssilva.sales.domain.port.out;

/**
 * Puerto de LECTURA ligera del flag {@code test_mode} del negocio en curso.
 *
 * <p>
 * Lo consume {@code SaleService}/{@code ShiftService} para decidir, server-side,
 * si una venta/corte nace como PRUEBA ({@code is_test = true}). Es una consulta
 * mínima y DESACOPLADA de inventario (no toca stock ni catálogo); su
 * implementación lee {@code admin.business.test_mode} por el schema del tenant en
 * curso.
 */
public interface TestModeQueryPort {

    /**
     * @return {@code true} si el negocio del tenant en curso está en MODO PRUEBA
     *         (las ventas/cortes nuevos deben marcarse {@code is_test = true});
     *         {@code false} si está en operación real o no hay tenant/negocio.
     */
    boolean isTestModeActive();
}
