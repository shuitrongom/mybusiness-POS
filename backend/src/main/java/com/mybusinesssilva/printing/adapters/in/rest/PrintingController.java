package com.mybusinesssilva.printing.adapters.in.rest;

import com.mybusinesssilva.printing.application.PrintingService;
import com.mybusinesssilva.printing.domain.TicketData;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de impresión. Devuelven el trabajo ESC/POS en base64 para el agente local.
 * Requieren el módulo {@code printing} habilitado.
 */
@RestController
@RequestMapping("/api/v1/printing")
@PreAuthorize("@moduleAccess.canUse('printing')")
public class PrintingController {

    private final PrintingService printingService;

    public PrintingController(PrintingService printingService) {
        this.printingService = printingService;
    }

    /**
     * Genera el ESC/POS de un ticket. El front lo pasa al agente de impresión local.
     */
    @PostMapping("/ticket")
    public PrintingService.PrintJob ticket(@Valid @RequestBody TicketRequest request) {
        List<TicketData.Item> items = request.items().stream()
                .map(i -> new TicketData.Item(i.description(), i.quantity(), i.unitPrice(), i.lineTotal()))
                .toList();

        TicketData ticket = new TicketData(
                request.headerLines() == null ? List.of() : request.headerLines(),
                request.footerLines() == null ? List.of() : request.footerLines(),
                request.folio(), request.cashier(), items,
                request.subtotal(), request.discount(), request.total(),
                request.paid(), request.change(),
                request.paperWidth() == 0 ? 48 : request.paperWidth());

        return printingService.buildTicketJob(ticket, request.openDrawer(), request.autoCut());
    }

    /**
     * Ticket de prueba de impresión (para configurar/verificar la impresora).
     */
    @PostMapping("/test")
    public PrintingService.PrintJob test() {
        TicketData ticket = new TicketData(
                List.of("MyBusiness Silva", "Prueba de impresion"),
                List.of("Impresora configurada correctamente"),
                "TEST-0001", "sistema",
                List.of(new TicketData.Item("Producto de prueba", BigDecimal.ONE,
                        new BigDecimal("10.00"), new BigDecimal("10.00"))),
                new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("10.00"),
                new BigDecimal("10.00"), BigDecimal.ZERO, 48);
        return printingService.buildTicketJob(ticket, false, true);
    }

    /** Datos para generar un ticket. */
    public record TicketRequest(
            List<String> headerLines,
            List<String> footerLines,
            String folio,
            String cashier,
            @NotNull List<ItemRequest> items,
            BigDecimal subtotal,
            BigDecimal discount,
            BigDecimal total,
            BigDecimal paid,
            BigDecimal change,
            int paperWidth,
            boolean openDrawer,
            boolean autoCut) {
    }

    /** Renglón del ticket. */
    public record ItemRequest(
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {
    }
}
