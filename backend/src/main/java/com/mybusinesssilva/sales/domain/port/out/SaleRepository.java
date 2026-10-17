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
         * @param saleId     id de la venta
         * @param folio      folio
         * @param branchId   sucursal
         * @param customerId cliente (puede ser null)
         * @param status     estado
         * @param total      total de la venta
         * @param lines      renglones con producto, cantidad y precio
         */
        record SaleDetail(long saleId, String folio, long branchId, Long customerId, String status,
                        BigDecimal total, List<SaleDetailLine> lines) {
        }

        /**
         * Renglón detallado de una venta. {@code productId} es {@code null} para un
         * artículo libre.
         * {@code unit} es la unidad del renglón (pz, kg, ...); puede ser {@code null}.
         */
        record SaleDetailLine(Long productId, String description, BigDecimal quantity,
                        BigDecimal unitPrice, String unit) {
        }
}
