package com.mybusinesssilva.ticketing.adapters.in.rest;

import com.mybusinesssilva.ticketing.application.TicketService;
import com.mybusinesssilva.ticketing.application.TicketService.TicketSettings;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints del ticket de venta: configuración personalizable del negocio y consulta de ventas
 * para reimpresión. Requieren el módulo {@code sales} habilitado (mismo que el punto de venta).
 */
@RestController
@RequestMapping("/api/v1/tickets")
@PreAuthorize("@moduleAccess.canUse('sales')")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /** Configuración actual del ticket. */
    @GetMapping("/settings")
    public TicketSettings getSettings() {
        return ticketService.getSettings();
    }

    /** Guarda la configuración del ticket. Solo dueño/administrador. */
    @PutMapping("/settings")
    @PreAuthorize("@moduleAccess.canManageSettings()")
    public TicketSettings saveSettings(@RequestBody TicketSettings settings) {
        return ticketService.saveSettings(settings);
    }

    /** Ventas recientes para buscar y reimprimir. */
    @GetMapping("/sales")
    public List<TicketService.SaleSummary> recentSales(
            @RequestParam(name = "limit", defaultValue = "50") @Min(1) int limit) {
        return ticketService.recentSales(limit);
    }

    /** Detalle de una venta para reimprimir su ticket. */
    @GetMapping("/sales/{id}")
    public ResponseEntity<TicketService.SaleTicket> saleTicket(@PathVariable long id) {
        TicketService.SaleTicket ticket = ticketService.getSaleTicket(id);
        return ticket == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(ticket);
    }
}
