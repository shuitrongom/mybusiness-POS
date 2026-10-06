import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'motion/react';
import { Printer, Search, ReceiptText, ChevronLeft, ChevronRight, Eye } from 'lucide-react';
import { api } from '@/lib/api';
import { fadeInUp, staggerContainer, staggerItem } from '@/lib/motion';
import { Ticket, defaultTicketSettings, type TicketSettings, type TicketData } from '@/components/Ticket';
import { SupervisorAuthModal } from '@/components/SupervisorAuthModal';
import { useSession } from '@/store/session';
import { decodeToken, isBusinessAdmin, hasPermission } from '@/lib/jwt';
import { toast } from '@/store/toast';
import { printTicketHtml, setPaperWidth } from '@/lib/thermalPrint';
import { saleTicketHtml } from '@/lib/ticketHtml';
import './dashboard.css';
import './ticket-settings.css';

interface SaleSummary {
  id: number; folio: string | null; total: number;
  cashier: string | null; branchName: string | null; createdAt: string;
}
interface SaleTicket {
  sale: SaleSummary;
  lines: { description: string; quantity: number; unitPrice: number; lineTotal: number }[];
  payments: { method: string; amount: number }[];
}

const money = (n: number) => new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const methodLabel = (m: string) =>
  m === 'CASH' ? 'Efectivo' : m === 'CARD' ? 'Tarjeta' : m === 'TRANSFER' ? 'Transferencia' : m;

const PAGE_SIZE = 12;

/**
 * Reimpresión de tickets (premium): lista paginada por hojas, buscador por folio/cajero, vista
 * previa del ticket y reimpresión que envía directo a la impresora de tickets instalada.
 */
export function TicketReprintPage() {
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [openId, setOpenId] = useState<number | null>(null);
  const [authOpen, setAuthOpen] = useState(false);

  const token = useSession((s) => s.token);
  const claims = decodeToken(token);
  // Reimprime sin autorización si es Dueño/Admin o su rol tiene el permiso. Configurable desde
  // Administración → Roles y permisos (Ventas → Reimprimir, o Impresión → Crear).
  const canReprintDirectly = isBusinessAdmin(claims)
    || hasPermission(claims, 'sales', 'REPRINT') || hasPermission(claims, 'printing', 'CREATE');

  const sales = useQuery({
    queryKey: ['ticket', 'recent-sales'],
    queryFn: async () => (await api.get<SaleSummary[]>('/tickets/sales?limit=300')).data,
  });
  const settings = useQuery({
    queryKey: ['ticket', 'settings'],
    queryFn: async () => (await api.get<TicketSettings>('/tickets/settings')).data,
  });
  useEffect(() => { if (settings.data) setPaperWidth(settings.data.paperWidthMm); }, [settings.data]);
  const detail = useQuery({
    queryKey: ['ticket', 'sale', openId],
    queryFn: async () => (await api.get<SaleTicket>(`/tickets/sales/${openId}`)).data,
    enabled: openId != null,
  });

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (sales.data ?? []).filter((s) =>
      q === '' || (s.folio ?? '').toLowerCase().includes(q) || (s.cashier ?? '').toLowerCase().includes(q));
  }, [sales.data, query]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const safePage = Math.min(page, pageCount - 1);
  const pageRows = filtered.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  const ticketData: TicketData | null = detail.data ? {
    folio: detail.data.sale.folio ?? `V-${detail.data.sale.id}`,
    branchName: detail.data.sale.branchName ?? 'Matriz',
    cashier: detail.data.sale.cashier,
    dateTime: new Date(detail.data.sale.createdAt),
    lines: detail.data.lines,
    total: detail.data.sale.total,
    methodLabel: detail.data.payments[0] ? methodLabel(detail.data.payments[0].method) : 'Pago',
    received: detail.data.payments.reduce((s, p) => s + p.amount, 0),
    change: 0,
  } : null;

  // Impresión térmica: sale al rollo (58/80mm) con el tamaño correcto, sin hoja gigante.
  const doPrint = () => {
    if (ticketData) printTicketHtml(saleTicketHtml(settings.data ?? defaultTicketSettings, ticketData));
  };
  const reprint = () => { if (canReprintDirectly) doPrint(); else setAuthOpen(true); };

  return (
    <div>
      <div className="sec-title"><ReceiptText size={20} /><h1 className="page-title">Reimpresión de tickets</h1></div>
      <p className="page-sub">Busca una venta y reimprime su comprobante en la impresora de tickets</p>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <div className="prod-list-head">
          <h3>Ventas recientes</h3>
          <div className="tk-search">
            <Search size={16} />
            <input value={query} onChange={(e) => { setQuery(e.target.value); setPage(0); }}
              placeholder="Buscar por folio o cajero…" />
          </div>
        </div>

        <table className="table">
          <thead>
            <tr><th>Folio</th><th>Fecha</th><th>Cajero</th><th>Sucursal</th><th className="ta-right">Total</th><th></th></tr>
          </thead>
          <motion.tbody variants={staggerContainer} initial="hidden" animate="visible">
            {pageRows.map((s) => (
              <motion.tr key={s.id} variants={staggerItem}>
                <td><strong>{s.folio ?? `V-${s.id}`}</strong></td>
                <td>{new Date(s.createdAt).toLocaleString('es-MX', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}</td>
                <td>{s.cashier ?? '—'}</td>
                <td>{s.branchName ?? '—'}</td>
                <td className="ta-right">{money(s.total)}</td>
                <td className="ta-right">
                  <div className="tk-row-actions">
                    <button className="btn-ghost tk-view-btn" onClick={() => setOpenId(s.id)}>
                      <Eye size={15} /> Ver
                    </button>
                    <button className="btn-accent tk-print-btn" onClick={() => { setOpenId(s.id); }}>
                      <Printer size={15} /> Imprimir
                    </button>
                  </div>
                </td>
              </motion.tr>
            ))}
            {pageRows.length === 0 && (
              <tr><td colSpan={6} className="empty">
                {sales.data?.length === 0 ? 'Aún no hay ventas.' : 'Sin resultados.'}
              </td></tr>
            )}
          </motion.tbody>
        </table>

        {/* Paginación por hojas */}
        {filtered.length > PAGE_SIZE && (
          <div className="tk-pager">
            <button className="btn-ghost" disabled={safePage === 0} onClick={() => setPage((p) => Math.max(p - 1, 0))}>
              <ChevronLeft size={16} /> Anterior
            </button>
            <span className="tk-pager-info">Hoja {safePage + 1} de {pageCount} · {filtered.length} tickets</span>
            <button className="btn-ghost" disabled={safePage >= pageCount - 1} onClick={() => setPage((p) => Math.min(p + 1, pageCount - 1))}>
              Siguiente <ChevronRight size={16} />
            </button>
          </div>
        )}
      </motion.div>

      <AnimatePresence>
        {openId != null && (
          <motion.div className="pos-modal-overlay" onClick={() => setOpenId(null)}
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
            <motion.div className="ticket-modal" onClick={(e) => e.stopPropagation()}
              initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }} exit={{ opacity: 0, scale: 0.97 }}>
              <div className="ticket-modal-head">
                <h3>Ticket {ticketData ? ticketData.folio : ''}</h3>
                <button className="modal-close" onClick={() => setOpenId(null)} aria-label="Cerrar">×</button>
              </div>
              <div className="ticket-modal-body">
                {detail.isLoading && <p className="empty">Cargando ticket…</p>}
                {ticketData && <Ticket settings={settings.data ?? defaultTicketSettings} data={ticketData} />}
              </div>
              <div className="ticket-actions">
                <button className="btn-ghost" onClick={() => setOpenId(null)}>Cerrar</button>
                <button className="pos-confirm" disabled={!ticketData} onClick={reprint}>
                  <Printer size={16} /> Reimprimir
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <SupervisorAuthModal
        open={authOpen}
        action="Reimprimir ticket"
        onClose={() => setAuthOpen(false)}
        onAuthorized={(by) => { setAuthOpen(false); toast.info('Autorizado', `Por ${by}.`); doPrint(); }}
      />
    </div>
  );
}
