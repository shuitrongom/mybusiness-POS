package com.mybusinesssilva.invoicing.domain.port.out;

import com.mybusinesssilva.invoicing.domain.model.CfdiConcept;
import com.mybusinesssilva.invoicing.domain.model.ReceiverInfo;
import java.util.List;

/**
 * Puerto de salida hacia el PAC (Proveedor Autorizado de Certificación) que timbra los CFDI
 * ante el SAT. Abstrae al proveedor concreto: se puede empezar con un PAC económico y cambiarlo
 * después implementando otro adaptador, sin tocar el dominio ni los casos de uso.
 */
public interface CfdiStampingPort {

    /**
     * Solicita el timbrado de un CFDI de ingreso (factura) al PAC.
     *
     * @param receiver datos fiscales del receptor
     * @param concepts conceptos de la factura
     * @return resultado del timbrado (UUID y XML si tuvo éxito, o error)
     */
    StampResult stampInvoice(ReceiverInfo receiver, List<CfdiConcept> concepts);

    /**
     * Solicita la cancelación de un CFDI previamente timbrado.
     *
     * @param uuid folio fiscal del CFDI a cancelar
     * @return resultado de la cancelación
     */
    CancelResult cancel(String uuid);

    /**
     * Resultado de un timbrado.
     *
     * @param success true si se timbró
     * @param uuid    folio fiscal asignado (si success)
     * @param xml     XML timbrado (si success)
     * @param error   mensaje de error (si no success)
     */
    record StampResult(boolean success, String uuid, String xml, String error) {

        public static StampResult ok(String uuid, String xml) {
            return new StampResult(true, uuid, xml, null);
        }

        public static StampResult failure(String error) {
            return new StampResult(false, null, null, error);
        }
    }

    /**
     * Resultado de una cancelación.
     *
     * @param success true si se canceló
     * @param error   mensaje de error (si no success)
     */
    record CancelResult(boolean success, String error) {

        public static CancelResult ok() {
            return new CancelResult(true, null);
        }

        public static CancelResult failure(String error) {
            return new CancelResult(false, error);
        }
    }
}
