package com.mybusinesssilva.invoicing.adapters.out.pac;

import com.mybusinesssilva.invoicing.domain.model.CfdiConcept;
import com.mybusinesssilva.invoicing.domain.model.ReceiverInfo;
import com.mybusinesssilva.invoicing.domain.port.out.CfdiStampingPort;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador de PAC SIMULADO (sandbox). Genera un UUID y un XML de ejemplo para permitir probar
 * el flujo completo de facturación de extremo a extremo sin un contrato de PAC real.
 *
 * <p>Al contratar un PAC real (Finkok, Facturama, etc.), se implementa otro adaptador de
 * {@link CfdiStampingPort} apuntando a su API y se desactiva este por configuración
 * ({@code app.cfdi.pac=real}). El dominio y los casos de uso NO cambian.
 *
 * <p>Activo por defecto o cuando {@code app.cfdi.pac=simulated}.
 */
@Component
@ConditionalOnProperty(name = "app.cfdi.pac", havingValue = "simulated", matchIfMissing = true)
public class SimulatedPacAdapter implements CfdiStampingPort {

    @Override
    public StampResult stampInvoice(ReceiverInfo receiver, List<CfdiConcept> concepts) {
        if (concepts == null || concepts.isEmpty()) {
            return StampResult.failure("El CFDI debe tener al menos un concepto");
        }
        String uuid = UUID.randomUUID().toString().toUpperCase();
        String xml = buildSampleXml(uuid, receiver, concepts);
        return StampResult.ok(uuid, xml);
    }

    @Override
    public CancelResult cancel(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return CancelResult.failure("UUID requerido para cancelar");
        }
        return CancelResult.ok();
    }

    private String buildSampleXml(String uuid, ReceiverInfo receiver, List<CfdiConcept> concepts) {
        StringBuilder sb = new StringBuilder();
        sb.append("<cfdi:Comprobante Version=\"4.0\">");
        sb.append("<cfdi:Receptor Rfc=\"").append(receiver.rfc())
                .append("\" Nombre=\"").append(receiver.name())
                .append("\" DomicilioFiscalReceptor=\"").append(receiver.zip())
                .append("\" RegimenFiscalReceptor=\"").append(receiver.regime())
                .append("\" UsoCFDI=\"").append(receiver.cfdiUse()).append("\"/>");
        sb.append("<cfdi:Conceptos>");
        for (CfdiConcept c : concepts) {
            sb.append("<cfdi:Concepto ClaveProdServ=\"").append(c.satProdServ())
                    .append("\" Descripcion=\"").append(c.description())
                    .append("\" Importe=\"").append(c.amount()).append("\"/>");
        }
        sb.append("</cfdi:Conceptos>");
        sb.append("<tfd:TimbreFiscalDigital UUID=\"").append(uuid).append("\"/>");
        sb.append("</cfdi:Comprobante>");
        return sb.toString();
    }
}
