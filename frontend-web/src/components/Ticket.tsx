import './ticket.css';

/** Configuración del ticket (coincide con TicketSettings del backend). */
export interface TicketSettings {
  businessName: string | null;
  address: string | null;
  phone: string | null;
  rfc: string | null;
  headerMessage: string | null;
  footerMessage: string | null;
  showLogo: boolean;
  showAddress: boolean;
  showPhone: boolean;
  showRfc: boolean;
  showCashier: boolean;
  showFolio: boolean;
  paperWidthMm: number;
  logoUrl: string | null;
}

export interface TicketLine {
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

/** Datos de la venta a mostrar en el ticket. */
export interface TicketData {
  folio: string;
  branchName: string;
  cashier?: string | null;
  dateTime: Date;
  lines: TicketLine[];
  total: number;
  methodLabel: string;
  received?: number;
  change?: number;
  offline?: boolean;
}

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

export const defaultTicketSettings: TicketSettings = {
  businessName: null, address: null, phone: null, rfc: null,
  headerMessage: null, footerMessage: '¡Gracias por su compra!',
  showLogo: true, showAddress: true, showPhone: true, showRfc: false,
  showCashier: true, showFolio: true, paperWidthMm: 80, logoUrl: null,
};

/**
 * Ticket de venta reutilizable, alimentado por la configuración del negocio y los datos de la
 * venta. Se usa en el POS (al cobrar), en la vista previa de configuración y en la reimpresión.
 * El elemento raíz tiene id="ticket-print" para el CSS de impresión (@media print).
 */
export function Ticket({ settings, data }: { settings: TicketSettings; data: TicketData }) {
  const itemCount = data.lines.reduce((s, l) => s + l.quantity, 0);
  const storeName = settings.businessName || data.branchName;

  return (
    <div className="ticket-paper" id="ticket-print"
      style={{ maxWidth: settings.paperWidthMm >= 80 ? 300 : 220 }}>
      <div className="ticket-brand">
        {settings.showLogo && settings.logoUrl && (
          <img src={settings.logoUrl} alt="Logo" className="ticket-logo" />
        )}
        <div className="ticket-store">{storeName}</div>
        {settings.headerMessage && <div className="ticket-tagline">{settings.headerMessage}</div>}
        {settings.showAddress && settings.address && <div className="ticket-info">{settings.address}</div>}
        {settings.showPhone && settings.phone && <div className="ticket-info">Tel. {settings.phone}</div>}
        {settings.showRfc && settings.rfc && <div className="ticket-info">RFC: {settings.rfc}</div>}
      </div>

      <div className="ticket-meta">
        {settings.showFolio && <span>Folio: {data.folio}</span>}
        <span>{data.dateTime.toLocaleString('es-MX', {
          day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
        })}</span>
      </div>
      {settings.showCashier && data.cashier && (
        <div className="ticket-meta"><span>Atendió: {data.cashier}</span><span>{data.branchName}</span></div>
      )}

      <div className="ticket-divider" />
      <div className="ticket-lines">
        {data.lines.map((l, i) => (
          <div key={i} className="ticket-line">
            <div className="ticket-line-desc">
              <span className="ticket-line-qty">{l.quantity}×</span> {l.description}
            </div>
            <div className="ticket-line-amt">{money(l.lineTotal)}</div>
          </div>
        ))}
      </div>
      <div className="ticket-divider" />

      <div className="ticket-totals">
        <div className="ticket-total-row ticket-grand">
          <span>Total ({itemCount} art.)</span><strong>{money(data.total)}</strong>
        </div>
        <div className="ticket-total-row">
          <span>{data.methodLabel}</span>
          <span>{money(data.received ?? data.total)}</span>
        </div>
        {data.change != null && data.change > 0 && (
          <div className="ticket-total-row"><span>Cambio</span><span>{money(data.change)}</span></div>
        )}
      </div>

      <div className="ticket-divider" />
      <div className="ticket-foot">
        {data.offline && <div className="ticket-offline">Pendiente de sincronizar</div>}
        {settings.footerMessage && <div>{settings.footerMessage}</div>}
        <div className="ticket-foot-small">Este comprobante no es una factura fiscal (CFDI).</div>
      </div>
    </div>
  );
}
