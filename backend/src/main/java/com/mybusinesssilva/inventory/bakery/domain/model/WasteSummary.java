package com.mybusinesssilva.inventory.bakery.domain.model;

import java.math.BigDecimal;

/**
 * Agregado de SOLO LECTURA de la merma vendible del tenant en curso (todas las
 * sucursales). Lo usa el corte prueba→real para informar cuánta merma se
 * conserva (se arrastra), sin tocarla.
 *
 * @param totalPieces  suma de piezas en merma de artículos activos
 * @param productCount número de artículos activos con existencia de merma &gt; 0
 */
public record WasteSummary(BigDecimal totalPieces, int productCount) {
}
