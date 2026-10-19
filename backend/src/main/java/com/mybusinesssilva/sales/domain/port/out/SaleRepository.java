package com.mybusinesssilva.sales.domain.port.out;

import com.mybusinesssilva.sales.domain.model.Sale;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para la persistencia de ventas (schema del tenant).
 */
public interface SaleRepository {

        Sale insert(Sale sale);

        boolean existsById(long id);

        /**
         * Busca una venta por su clave de idempotencia (para deduplicar sincronización
         * offline).
         */
        Optional<Long> findIdByIdempotencyKey(String idempotencyKey);

        void markVoided(long saleId);

        /**
         * @return el estado actual de la venta (COMPLETED, VOIDED, QUOTE) o vacío si no
         *         existe.
         */
        Optional<String> statusOf(long saleId);

        /** @return el estado del turno (OPEN/CLOSED) indicado, o vacío si no existe. */
        Optional<String> shiftStatus(long shiftId);

        /** @return la sucursal de la venta. */
        Optional<Long> branchOf(long saleId);

        /**
         * @return los renglones de una venta (para reponer inventario en
         *         cancelaciones/devoluciones).
         */
        List<SaleLineRow> linesOf(long saleId);

        /**
         * @return el detalle de una venta para devoluciones (cabecera + renglones con
         *         precio), o vacío.
         */
        Optional<SaleDetail> findDetail(long saleId);

        /**
         * @param productId producto
         * @return cantidad ya devuelta de ese producto para la venta indicada (para no
         *         exceder lo vendido)
         */
        BigDecimal returnedQuantity(long saleId, long productId);

        /**
         * Últimas ventas COMPLETED devolvibles, para el selector de devoluciones (que
         * muestra las ventas recientes sin que el cajero teclee nada, igual que el
         * buscador de productos). Ordenadas por {@code created_at} DESC y acotadas al
         * tenant en curso (RLS, igual que el historial de devoluciones; no se filtra
         * por
         * sucursal manualmente — punto de extensión si en multi-sucursal se quiere
         * acotar).
         *
         * @param limit tope de ventas a devolver (se normaliza: &le;0 → 20, tope 50)
         * @param q     texto libre opcional ({@code null} = sin filtro); filtra por
         *              folio
         *              o nombre de cliente (ILIKE). Nullable → se compara con CAST.
         */
        List<RecentSale> recentSales(int limit, String q);

        /**
         * Resumen de una venta reciente para el selector de devoluciones.
         *
         * @param saleId         id de la venta
         * @param folio          folio (puede ser {@code null} en datos antiguos; el
         *                       front
         *                       cae a {@code "V-"+saleId})
         * @param createdAt      fecha/hora de la venta (puede ser {@code null})
         * @param total          total de la venta
         * @param customerName   nombre del cliente ({@code null} en venta de mostrador)
         * @param itemCount      número de renglones de la venta
         * @param returnedCount  número de productos de la venta con alguna devolución
         *                       previa (para el chip "con devoluciones")
         * @param fullyReturned  {@code true} si no queda ningún renglón de producto por
         *                       devolver (toda la venta ya se saldó)
         * @param cashier        usuario de caja que registró la venta ({@code null} en
         *                       datos antiguos sin cajero)
         * @param paymentMethods CSV de métodos de pago distintos de la venta (p.ej.
         *                       {@code "CASH,CARD"}); {@code null} si no tiene pagos
         *                       (venta a crédito sin pago registrado)
         * @param itemsPreview   descripciones de los primeros renglones unidas con
         *                       {@code " · "} (vista previa de artículos); {@code null}
         *                       si no hay renglones
         */
        record RecentSale(long saleId, String folio, java.time.OffsetDateTime createdAt,
                        BigDecimal total, String customerName, int itemCount, int returnedCount,
                        boolean fullyReturned, String cashier, String paymentMethods,
                        String itemsPreview) {
        }

        /**
         * Proyección de un renglón para reposición de inventario.
         *
         * @param productId producto; {@code null} para un artículo libre (no mueve
         *                  inventario)
         * @param quantity  cantidad vendida
         * @param waste     {@code true} si el renglón fue una venta de merma (al
         *                  cancelar se devuelve
         *                  a la existencia de merma, no al stock normal)
         */
        record SaleLineRow(Long productId, BigDecimal quantity, boolean waste) {
        }

        /**
         * Detalle de una venta para el flujo de devoluciones.
         *
         * @param saleId       id de la venta
         * @param folio        folio
         * @param branchId     sucursal
         * @param customerId   cliente (puede ser null)
         * @param status       estado
         * @param total        total de la venta
         * @param lines        renglones con producto, cantidad y precio
         * @param createdAt    fecha/hora en que se registró la venta original (puede
         *                     ser {@code null} en datos antiguos)
         * @param customerName nombre del cliente de la venta (puede ser {@code null} si
         *                     fue venta de mostrador)
         */
        record SaleDetail(long saleId, String folio, long branchId, Long customerId, String status,
                        BigDecimal total, List<SaleDetailLine> lines,
                        java.time.OffsetDateTime createdAt, String customerName) {
        }

        /**
         * Renglón detallado de una venta. {@code productId} es {@code null} para un
         * artículo libre.
         * {@code unit} es la unidad del renglón (pz, kg, ...); puede ser {@code null}.
         *
         * @param productName     nombre ACTUAL del producto en catálogo (puede ser
         *                        {@code null} para artículo libre); el front cae a
         *                        {@code description} cuando falta
         * @param active          {@code true} si el producto sigue activo en catálogo
         *                        (no soft-deleted); {@code true} por defecto para
         *                        artículo libre
         * @param alreadyReturned cantidad ya devuelta de este producto para la venta
         *                        (para mostrar cuánto queda por devolver)
         */
        record SaleDetailLine(Long productId, String description, BigDecimal quantity,
                        BigDecimal unitPrice, String unit, String productName, boolean active,
                        BigDecimal alreadyReturned) {
        }
}
