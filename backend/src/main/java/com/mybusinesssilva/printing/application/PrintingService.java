package com.mybusinesssilva.printing.application;

import com.mybusinesssilva.printing.domain.TicketData;
import com.mybusinesssilva.printing.domain.TicketRenderer;
import java.util.Base64;
import org.springframework.stereotype.Service;

/**
 * Casos de uso de impresión.
 *
 * <p>Genera el flujo de comandos ESC/POS de un ticket y lo entrega codificado en base64. El
 * agente de impresión local (app ligera en la PC del cajero) recibe ese base64, lo decodifica y
 * lo envía a la impresora térmica por USB/red/Bluetooth, de forma silenciosa.
 *
 * <p>Este enfoque permite que el POS viva en la nube y aun así imprima en la impresora local,
 * y es compatible con impresoras de marca y genéricas/chinas que entienden ESC/POS.
 */
@Service
public class PrintingService {

    private final TicketRenderer renderer = new TicketRenderer();

    /**
     * Genera el ticket como ESC/POS y lo devuelve en base64 para el agente local.
     *
     * @param ticket     datos del ticket
     * @param openDrawer abrir el cajón de dinero al final
     * @param autoCut    cortar el papel al final
     */
    public PrintJob buildTicketJob(TicketData ticket, boolean openDrawer, boolean autoCut) {
        byte[] escpos = renderer.render(ticket, openDrawer, autoCut);
        String base64 = Base64.getEncoder().encodeToString(escpos);
        return new PrintJob("escpos", base64, escpos.length);
    }

    /**
     * Trabajo de impresión listo para el agente local.
     *
     * @param format   formato del contenido (siempre {@code escpos} por ahora)
     * @param dataBase64 comandos ESC/POS codificados en base64
     * @param byteCount número de bytes originales
     */
    public record PrintJob(String format, String dataBase64, int byteCount) {
    }
}
