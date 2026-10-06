import './ticket.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

/** Una fila del comprobante: etiqueta a la izquierda, valor a la derecha. strong = destacada. */
export interface VoucherRow {
  label: string;
  value: string;
  strong?: boolean;
  danger?: boolean;
}

/** Datos de un comprobante genérico de respaldo (corte, movimiento, devolución, traspaso). */
export interface VoucherData {
  title: string;               // "CORTE DE CAJA Z", "RETIRO DE EFECTIVO", etc.
  storeName: string;
  folio?: string | null;
  dateTime: Date;
  cashier?: string | null;
  branchName?: string | null;
  rows: VoucherRow[];          // desglose principal
  items?: { description: string; quantity: number; amount?: number }[]; // conceptos (devolución/traspaso)
  note?: string | null;
  footer?: string | null;
  signature?: boolean;         // línea de firma (para respaldo físico)
}

/**
 * Comprobante genérico de respaldo, en formato de rollo térmico (mismo estilo que el ticket de
 * venta). Sirve para imprimir todo lo que mueve dinero o mercancía —cortes de caja, movimientos
 * de efectivo, devoluciones, traspasos— y así dejar respaldo físico contra pérdidas o malos
 * movimientos. Comparte id="ticket-print" para la impresión automática a la impresora de tickets.
 */
export function Voucher({ data }: { data: VoucherData }) {
  return (
    <div className="ticket-paper" id="ticket-print" style={{ maxWidth: 300 }}>
      <div className="ticket-brand">
        <div className="ticket-store">{data.storeName}</div>
        <div className="ticket-tagline">{data.title}</div>
      </div>

      <div className="ticket-meta">
        {data.folio && <span>Folio: {data.folio}</span>}
        <span>{data.dateTime.toLocaleString('es-MX', {
          day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
        })}</span>
      </div>
      {(data.cashier || data.branchName) && (
        <div className="ticket-meta">
          {data.cashier && <span>Responsable: {data.cashier}</span>}
          {data.branchName && <span>{data.branchName}</span>}
        </div>
      )}

      {data.items && data.items.length > 0 && (
        <>
          <div className="ticket-divider" />
          <div className="ticket-lines">
            {data.items.map((it, i) => (
              <div key={i} className="ticket-line">
                <div className="ticket-line-desc">
                  <span className="ticket-line-qty">{it.quantity}×</span> {it.description}
                </div>
                {it.amount != null && <div className="ticket-line-amt">{money(it.amount)}</div>}
              </div>
            ))}
          </div>
        </>
      )}

      <div className="ticket-divider" />
      <div className="ticket-totals">
        {data.rows.map((r, i) => (
          <div key={i} className={`ticket-total-row ${r.strong ? 'ticket-grand' : ''}`}>
            <span>{r.label}</span>
            {r.strong
              ? <strong style={r.danger ? { color: '#dc2626' } : undefined}>{r.value}</strong>
              : <span style={r.danger ? { color: '#dc2626' } : undefined}>{r.value}</span>}
          </div>
        ))}
      </div>

      {data.note && (
        <>
          <div className="ticket-divider" />
          <div className="ticket-info" style={{ whiteSpace: 'pre-wrap' }}>{data.note}</div>
        </>
      )}

      {data.signature && (
        <>
          <div className="ticket-divider" />
          <div style={{ marginTop: 28, textAlign: 'center' }}>
            <div style={{ borderTop: '1px solid #000', margin: '0 20px', paddingTop: 4 }}>Firma</div>
          </div>
        </>
      )}

      <div className="ticket-divider" />
      <div className="ticket-foot">
        {data.footer && <div>{data.footer}</div>}
        <div className="ticket-foot-small">Comprobante interno de respaldo · no es factura fiscal (CFDI).</div>
      </div>
    </div>
  );
}
