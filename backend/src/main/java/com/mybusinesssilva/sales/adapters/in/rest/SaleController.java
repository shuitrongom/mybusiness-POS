package com.mybusinesssilva.sales.adapters.in.rest;

import com.mybusinesssilva.sales.application.SaleService;
import com.mybusinesssilva.sales.application.SalesReturnService;
import com.mybusinesssilva.sales.domain.model.Payment;
import com.mybusinesssilva.sales.domain.model.PaymentMethod;
import com.mybusinesssilva.sales.domain.model.Sale;
import com.mybusinesssilva.sales.domain.model.SaleLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.mybusinesssilva.platform.security.AuthenticatedUser;

/**
 * Endpoints del punto de venta. Requieren el modulo sales habilitado.
 */
@RestController
@RequestMapping("/api/v1/sales")
@PreAuthorize("@moduleAccess.canUse('sales')")
public class SaleController {

        private final SaleService saleService;
        private final SalesReturnService salesReturnService;
        private final com.mybusinesssilva.sales.domain.port.out.SaleRepository saleRepository;
        private final org.springframework.jdbc.core.simple.JdbcClient jdbc;

        public SaleController(SaleService saleService, SalesReturnService salesReturnService,
                        com.mybusinesssilva.sales.domain.port.out.SaleRepository saleRepository,
                        org.springframework.jdbc.core.simple.JdbcClient jdbc) {
                this.saleService = saleService;
                this.salesReturnService = salesReturnService;
                this.saleRepository = saleRepository;
                this.jdbc = jdbc;
        }

        /**
         * Detalle de una venta (cabecera + renglones) para buscar el ticket en
         * devoluciones.
         */
        @org.springframework.web.bind.annotation.GetMapping("/{id}")
        public ResponseEntity<com.mybusinesssilva.sales.domain.port.out.SaleRepository.SaleDetail> detail(
                        @PathVariable long id) {
                return saleRepository.findDetail(id)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
        }

        /**
         * Lista las listas de precio activas del negocio para el selector del punto de
         * venta.
         */
        @org.springframework.web.bind.annotation.GetMapping("/price-lists")
        public List<java.util.Map<String, Object>> priceLists() {
                return jdbc.sql("SELECT id, name FROM price_list WHERE active = TRUE ORDER BY id")
                                .query((rs, n) -> java.util.Map.<String, Object>of(
                                                "id", rs.getInt("id"),
                                                "name", rs.getString("name")))
                                .list();
        }

        /**
         * Registra una venta. Acepta una clave de idempotencia para sincronizacion
         * offline.
         */
        @PostMapping
        public ResponseEntity<SaleService.SaleResult> register(
                        @AuthenticationPrincipal AuthenticatedUser actor,
                        @Valid @RequestBody CreateSaleRequest request) {

                String cashier = actor == null ? "unknown" : actor.subject();

                boolean adminLike = actor != null
                                && (actor.hasRole("OWNER") || actor.hasRole("ADMIN") || actor.hasRole("SUPERVISOR"));
                if (adminLike) {
                        throw new IllegalStateException(
                                        "Tu rol no registra ventas. Las ventas las realiza el personal de caja.");
                }

                if (request.shiftId() == null) {
                        throw new IllegalStateException(
                                        "Debes tener tu caja abierta para registrar ventas.");
                }

                List<SaleLine> lines = request.lines().stream()
                                .map(l -> new SaleLine(l.productId(), l.description(), l.quantity(),
                                                l.unitPrice(), l.discount(), l.unit()))
                                .toList();

                List<Payment> payments = request.payments() == null ? List.of()
                                : request.payments().stream()
                                                .map(p -> new Payment(PaymentMethod.valueOf(p.method()), p.amount()))
                                                .toList();

                String salesperson = request.salesperson() == null || request.salesperson().isBlank()
                                ? cashier
                                : request.salesperson();
                short priceListId = request.priceListId() == null ? 1 : request.priceListId().shortValue();
                boolean onCredit = request.onCredit() != null && request.onCredit();

                Sale sale = Sale.complete(request.branchId(), request.cashRegisterId(), request.shiftId(),
                                cashier, salesperson, request.customerId(), priceListId,
                                request.globalDiscount(), request.note(), onCredit,
                                lines, payments, request.idempotencyKey());

                // Renglones marcados como merma de panadería: wastePrice = unitPrice del
                // renglón,
                // entryPrice = entryPrice del renglón (puede ser null -> lo resuelve el
                // backend).
                List<com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase.WasteLineCommand> wasteLines = request
                                .lines().stream()
                                .filter(l -> l.productId() != null
                                                && Boolean.TRUE.equals(l.waste()))
                                .map(l -> new com.mybusinesssilva.inventory.bakery.domain.port.in.BakeryUseCase.WasteLineCommand(
                                                l.productId(), l.quantity(), l.entryPrice(),
                                                l.unitPrice()))
                                .toList();

                SaleService.SaleResult result = wasteLines.isEmpty()
                                ? saleService.registerSale(sale)
                                : saleService.registerSaleWithWaste(sale, wasteLines);
                HttpStatus status = result.duplicated() ? HttpStatus.OK : HttpStatus.CREATED;
                return ResponseEntity.status(status).body(result);
        }

        @PostMapping("/{id}/void")
        public ResponseEntity<Void> voidSale(
                        @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
                saleService.voidSale(id, actor == null ? "unknown" : actor.subject());
                return ResponseEntity.noContent().build();
        }

        /**
         * Registra una devolucion REAL: la persiste, valida, reingresa inventario y
         * reembolsa.
         */
        @PostMapping("/returns")
        public ResponseEntity<SalesReturnService.ReturnResult> registerReturn(
                        @AuthenticationPrincipal AuthenticatedUser actor,
                        @Valid @RequestBody ReturnRequest request) {
                List<SalesReturnService.ReturnItem> items = request.items().stream()
                                .map(i -> new SalesReturnService.ReturnItem(
                                                i.productId(), i.description(), i.quantity(),
                                                i.unitPrice() == null ? BigDecimal.ZERO : i.unitPrice()))
                                .toList();
                var command = new SalesReturnService.ReturnCommand(
                                request.saleId(), request.branchId() == null ? 0L : request.branchId(),
                                request.customerId(), request.reason(), request.refundMethod(), items);
                var result = salesReturnService.register(command, actor == null ? "unknown" : actor.subject());
                return ResponseEntity.status(HttpStatus.CREATED).body(result);
        }

        /** Historial de devoluciones recientes. */
        @org.springframework.web.bind.annotation.GetMapping("/returns")
        public List<java.util.Map<String, Object>> listReturns(
                        @RequestParam(value = "limit", required = false, defaultValue = "50") int limit) {
                return salesReturnService.listRecent(limit);
        }

        /** Registra una cotizacion o apartado (no cobra ni descuenta inventario). */
        @PostMapping("/quotes")
        public ResponseEntity<SaleService.SaleResult> registerQuote(
                        @AuthenticationPrincipal AuthenticatedUser actor,
                        @Valid @RequestBody CreateSaleRequest request) {
                String cashier = actor == null ? "unknown" : actor.subject();
                List<SaleLine> lines = request.lines().stream()
                                .map(l -> new SaleLine(l.productId(), l.description(), l.quantity(),
                                                l.unitPrice(), l.discount(), l.unit()))
                                .toList();
                Sale quote = Sale.quote(request.branchId(), cashier, request.customerId(), lines);
                return ResponseEntity.status(HttpStatus.CREATED).body(saleService.registerQuote(quote));
        }

        // --- DTOs ---

        /** Devolucion de productos. Si trae saleId, se valida contra esa venta. */
        public record ReturnRequest(
                        Long saleId,
                        Long branchId,
                        Long customerId,
                        String reason,
                        String refundMethod,
                        @NotEmpty List<ReturnItemRequest> items) {
        }

        /** Renglon de devolucion. */
        public record ReturnItemRequest(
                        @NotNull Long productId,
                        String description,
                        @NotNull BigDecimal quantity,
                        BigDecimal unitPrice) {
        }

        /** Alta de venta. Los campos comerciales son opcionales. */
        public record CreateSaleRequest(
                        @NotNull Long branchId,
                        Long cashRegisterId,
                        Long shiftId,
                        Long customerId,
                        String salesperson,
                        Integer priceListId,
                        BigDecimal globalDiscount,
                        String note,
                        Boolean onCredit,
                        @NotEmpty List<LineRequest> lines,
                        List<PaymentRequest> payments,
                        String idempotencyKey) {
        }

        /**
         * Renglon de venta. productId es opcional: null para un articulo libre.
         * unit es la unidad del renglon (pz, kg, ...); opcional, max. 16 caracteres.
         */
        public record LineRequest(
                        Long productId,
                        String description,
                        @NotNull BigDecimal quantity,
                        @NotNull BigDecimal unitPrice,
                        BigDecimal discount,
                        @Size(max = 16) String unit,
                        Boolean waste,
                        BigDecimal entryPrice) {
        }

        /** Pago de venta. */
        public record PaymentRequest(
                        @NotNull String method,
                        @NotNull BigDecimal amount) {
        }
}
