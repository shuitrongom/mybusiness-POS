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
import jakarta.validation.constraints.Positive;
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
        private final com.mybusinesssilva.sales.domain.port.out.TestModeQueryPort testModeQueryPort;
        private final org.springframework.jdbc.core.simple.JdbcClient jdbc;

        public SaleController(SaleService saleService, SalesReturnService salesReturnService,
                        com.mybusinesssilva.sales.domain.port.out.SaleRepository saleRepository,
                        com.mybusinesssilva.sales.domain.port.out.TestModeQueryPort testModeQueryPort,
                        org.springframework.jdbc.core.simple.JdbcClient jdbc) {
                this.saleService = saleService;
                this.salesReturnService = salesReturnService;
                this.saleRepository = saleRepository;
                this.testModeQueryPort = testModeQueryPort;
                this.jdbc = jdbc;
        }

        /**
         * Resuelve el scope efectivo (modo prueba) para los reportes de este
         * controller: usa el pedido si viene, o el default por modo (both si el modo
         * prueba está ON, real si OFF), y lo devuelve como texto canónico.
         */
        private String resolveScope(String scope) {
                return com.mybusinesssilva.sales.application.ReportScope
                                .resolve(scope, testModeQueryPort.isTestModeActive())
                                .name().toLowerCase(java.util.Locale.ROOT);
        }

        /**
         * Detalle de una venta (cabecera + renglones) para buscar el ticket en
         * devoluciones.
         */
        // El id se restringe a dígitos ({id:\\d+}) para que esta ruta NO capture rutas
        // hermanas como /sales/recent o /sales/returns (antes "recent" se intentaba
        // convertir a long y producía un 422 "For input string: recent").
        @org.springframework.web.bind.annotation.GetMapping("/{id:\\d+}")
        public ResponseEntity<com.mybusinesssilva.sales.domain.port.out.SaleRepository.SaleDetail> detail(
                        @PathVariable long id) {
                return saleRepository.findDetail(id)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
        }

        /**
         * Últimas ventas COMPLETED devolvibles para el selector de devoluciones: la
         * pantalla las muestra al entrar sin que el cajero teclee nada (igual que el
         * buscador de productos). Ordenadas por fecha DESC y acotadas al tenant (RLS).
         * {@code q} es opcional (filtro server-side por folio o cliente); el front
         * filtra
         * en memoria y no lo usa al montar.
         */
        @org.springframework.web.bind.annotation.GetMapping("/recent")
        public List<java.util.Map<String, Object>> recentSales(
                        @RequestParam(value = "limit", required = false, defaultValue = "20") int limit,
                        @RequestParam(value = "q", required = false) String q,
                        @RequestParam(value = "scope", required = false) String scope) {
                String query = (q == null || q.isBlank()) ? null : q.trim();
                return saleRepository.recentSales(limit, query, resolveScope(scope)).stream()
                                .map(s -> {
                                        java.util.Map<String, Object> m = new java.util.HashMap<>();
                                        m.put("saleId", s.saleId());
                                        m.put("folio", s.folio());
                                        m.put("createdAt", s.createdAt());
                                        m.put("total", s.total());
                                        m.put("customerName", s.customerName());
                                        m.put("itemCount", s.itemCount());
                                        m.put("returnedCount", s.returnedCount());
                                        m.put("fullyReturned", s.fullyReturned());
                                        m.put("cashier", s.cashier());
                                        m.put("paymentMethods", s.paymentMethods());
                                        m.put("itemsPreview", s.itemsPreview());
                                        return m;
                                })
                                .toList();
        }

        /**
         * Lista de ventas COMPLETED para la pantalla de REIMPRESIÓN de tickets, con
         * filtros server-side (fecha día/rango, sucursal, método de pago, tipo de
         * venta) y búsqueda inteligente por folio/cajero/cliente/producto. El scope de
         * modo prueba lo resuelve el backend ({@code resolveScope(null)} → both si el
         * modo prueba está ON, real si OFF); el front NO envía {@code scope}.
         */
        @org.springframework.web.bind.annotation.GetMapping("/reprint")
        public List<java.util.Map<String, Object>> reprintList(
                        @RequestParam(value = "limit", required = false, defaultValue = "300") int limit,
                        @RequestParam(value = "q", required = false) String q,
                        @RequestParam(value = "from", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
                        @RequestParam(value = "to", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
                        @RequestParam(value = "branchId", required = false) Long branchId,
                        @RequestParam(value = "method", required = false) String method,
                        @RequestParam(value = "saleType", required = false) String saleType) {
                String query = (q == null || q.isBlank()) ? null : q.trim();
                String methodParam = (method == null || method.isBlank()) ? null : method.trim();
                String saleTypeParam = (saleType == null || saleType.isBlank()) ? null : saleType.trim();
                // scope=null: el front no lo manda; resolveScope resuelve el default por
                // test_mode (both si ON, real si OFF). No se hardcodea is_test=false.
                return saleRepository.reprintList(limit, query, from, to, branchId, methodParam,
                                saleTypeParam, resolveScope(null)).stream()
                                .map(s -> {
                                        java.util.Map<String, Object> m = new java.util.HashMap<>();
                                        m.put("saleId", s.saleId());
                                        m.put("folio", s.folio());
                                        m.put("createdAt", s.createdAt());
                                        m.put("cashier", s.cashier());
                                        m.put("branchName", s.branchName());
                                        m.put("total", s.total());
                                        m.put("customerName", s.customerName());
                                        m.put("paymentMethods", s.paymentMethods());
                                        m.put("saleType", s.saleType());
                                        m.put("itemCount", s.itemCount());
                                        m.put("itemsPreview", s.itemsPreview());
                                        return m;
                                })
                                .toList();
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

                // Los roles de gestión no registran ventas: las hace el personal de caja.
                // (NO es una guarda de autorización; asegura que la venta la haga una caja.)
                boolean adminLike = actor != null
                                && (actor.hasRole("OWNER") || actor.hasRole("ADMIN"));
                if (adminLike) {
                        throw new IllegalStateException(
                                        "Tu rol no registra ventas. Las ventas las realiza el personal de caja.");
                }

                if (request.shiftId() == null) {
                        throw new IllegalStateException(
                                        "Debes tener tu caja abierta para registrar ventas.");
                }

                List<Payment> payments = request.payments() == null ? List.of()
                                : request.payments().stream()
                                                .map(p -> new Payment(PaymentMethod.valueOf(p.method()), p.amount()))
                                                .toList();

                String salesperson = request.salesperson() == null || request.salesperson().isBlank()
                                ? cashier
                                : request.salesperson();
                short priceListId = request.priceListId() == null ? 1 : request.priceListId().shortValue();
                boolean onCredit = request.onCredit() != null && request.onCredit();

                // El controlador solo mapea DTO -> comando CRUDO; el servicio recomputa el
                // precio autoritativo (ALTA-1). El precio/descuento del front viaja como dato
                // crudo (el backend lo ignora en renglones de catálogo).
                SaleService.SaleCommand cmd = new SaleService.SaleCommand(
                                request.branchId(), request.cashRegisterId(), request.shiftId(), cashier,
                                salesperson, request.customerId(), priceListId, request.globalDiscount(),
                                request.note(), onCredit,
                                request.lines().stream().map(l -> new SaleService.RawLine(
                                                l.productId(), l.description(), l.quantity(), l.unitPrice(),
                                                l.discount(), l.unit(), Boolean.TRUE.equals(l.waste()))).toList(),
                                payments, request.idempotencyKey());

                // Ruteo merma vs normal sobre RawLine (los SaleLine aún no existen; se arman en
                // el servicio tras recomputar precio). El precio de merma lo aplica el backend
                // desde product.waste_price; el POS no lo teclea.
                boolean hasWaste = cmd.lines().stream().anyMatch(SaleService.RawLine::waste);
                SaleService.SaleResult result = hasWaste
                                ? saleService.registerSaleWithWaste(cmd)
                                : saleService.registerSale(cmd);
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
                        @RequestParam(value = "limit", required = false, defaultValue = "50") int limit,
                        @RequestParam(value = "scope", required = false) String scope) {
                return salesReturnService.listRecent(limit, resolveScope(scope));
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

        /**
         * Devolucion de productos. Exige saleId: toda devolución se valida y recomputa
         * contra su venta origen (no hay devolución libre sin ticket). El servicio
         * recomputa el precio desde el renglón vendido, así que {@code unitPrice} del
         * request ya no se usa para el total.
         */
        public record ReturnRequest(
                        @NotNull Long saleId,
                        Long branchId,
                        Long customerId,
                        String reason,
                        String refundMethod,
                        @NotEmpty List<ReturnItemRequest> items) {
        }

        /** Renglon de devolucion. La cantidad debe ser estrictamente positiva. */
        public record ReturnItemRequest(
                        @NotNull Long productId,
                        String description,
                        @NotNull @Positive BigDecimal quantity,
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
                        Boolean waste) {
        }

        /** Pago de venta. */
        public record PaymentRequest(
                        @NotNull String method,
                        @NotNull BigDecimal amount) {
        }
}
