package com.mybusinesssilva.payments.domain.port.out;

import java.math.BigDecimal;

/**
 * Puerto de salida hacia el agregador de recargas y pago de servicios (TAECEL, Multiprepago, etc.).
 * Abstrae al proveedor concreto: se puede empezar con uno y cambiarlo implementando otro adaptador,
 * sin tocar el dominio ni los casos de uso.
 */
public interface RechargeProviderPort {

    /**
     * Procesa una recarga de tiempo aire.
     *
     * @param carrier   compañía telefónica
     * @param phone     número a recargar
     * @param amount    monto de la recarga
     * @return resultado de la operación
     */
    ProviderResult recharge(String carrier, String phone, BigDecimal amount);

    /**
     * Procesa el pago de un servicio.
     *
     * @param biller     emisor del servicio (CFE, Telmex, etc.)
     * @param reference  número de recibo o referencia
     * @param amount     monto a pagar
     * @return resultado de la operación
     */
    ProviderResult payService(String biller, String reference, BigDecimal amount);

    /**
     * Resultado de una operación con el agregador.
     *
     * @param success    true si la operación se completó
     * @param folio      folio del agregador (si success)
     * @param commission comisión ganada por el negocio (si success)
     * @param error      mensaje de error (si no success)
     */
    record ProviderResult(boolean success, String folio, BigDecimal commission, String error) {

        public static ProviderResult ok(String folio, BigDecimal commission) {
            return new ProviderResult(true, folio, commission, null);
        }

        public static ProviderResult failure(String error) {
            return new ProviderResult(false, null, BigDecimal.ZERO, error);
        }
    }
}
