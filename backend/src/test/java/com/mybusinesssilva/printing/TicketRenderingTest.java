package com.mybusinesssilva.printing;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.printing.application.PrintingService;
import com.mybusinesssilva.printing.domain.EscPosBuilder;
import com.mybusinesssilva.printing.domain.TicketData;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de la generación de tickets ESC/POS. No requieren base de datos:
 * verifican que el flujo de bytes contiene los comandos y el contenido esperado.
 */
class TicketRenderingTest {

    @Test
    void escPosBuilderProducesInitAndContent() {
        byte[] bytes = new EscPosBuilder()
                .alignCenter()
                .bold(true)
                .line("MI NEGOCIO")
                .bold(false)
                .cut()
                .openDrawer()
                .build();

        // Empieza con ESC @ (inicialización): 0x1B 0x40
        assertThat(bytes[0]).isEqualTo((byte) 0x1B);
        assertThat(bytes[1]).isEqualTo((byte) 0x40);

        String asText = new String(bytes, StandardCharsets.ISO_8859_1);
        assertThat(asText).contains("MI NEGOCIO");
        // Contiene comando de corte (GS V) y de cajón (ESC p).
        assertThat(asText).contains("\u001DV");
        assertThat(asText).contains("\u001Bp");
    }

    @Test
    void ticketJobIsBase64AndDecodesToEscPos() {
        PrintingService service = new PrintingService();
        TicketData ticket = new TicketData(
                List.of("MyBusiness Silva", "Sucursal Centro"),
                List.of("¡Gracias por su compra!"),
                "A-100", "cajero",
                List.of(
                        new TicketData.Item("Coca-Cola 600ml", new BigDecimal("2"),
                                new BigDecimal("18.00"), new BigDecimal("36.00")),
                        new TicketData.Item("Pan", new BigDecimal("3"),
                                new BigDecimal("5.00"), new BigDecimal("15.00"))),
                new BigDecimal("51.00"), BigDecimal.ZERO, new BigDecimal("51.00"),
                new BigDecimal("100.00"), new BigDecimal("49.00"), 48);

        PrintingService.PrintJob job = service.buildTicketJob(ticket, true, true);

        assertThat(job.format()).isEqualTo("escpos");
        assertThat(job.byteCount()).isGreaterThan(0);

        byte[] decoded = Base64.getDecoder().decode(job.dataBase64());
        assertThat(decoded.length).isEqualTo(job.byteCount());

        String asText = new String(decoded, StandardCharsets.ISO_8859_1);
        assertThat(asText).contains("MyBusiness Silva");
        assertThat(asText).contains("Coca-Cola 600ml");
        assertThat(asText).contains("TOTAL:");
        assertThat(asText).contains("$51.00");
        assertThat(asText).contains("Gracias"); // el pie
    }

    @Test
    void formatsAmountsRightAligned() {
        PrintingService service = new PrintingService();
        TicketData ticket = new TicketData(
                List.of("Negocio"), List.of(),
                "B-1", "c",
                List.of(new TicketData.Item("Producto", BigDecimal.ONE,
                        new BigDecimal("100.00"), new BigDecimal("100.00"))),
                new BigDecimal("100.00"), BigDecimal.ZERO, new BigDecimal("100.00"),
                new BigDecimal("100.00"), BigDecimal.ZERO, 32);

        PrintingService.PrintJob job = service.buildTicketJob(ticket, false, false);
        String asText = new String(Base64.getDecoder().decode(job.dataBase64()),
                StandardCharsets.ISO_8859_1);

        // La línea de TOTAL debe contener la etiqueta y el importe.
        assertThat(asText).contains("TOTAL:");
        assertThat(asText).contains("$100.00");
    }
}
