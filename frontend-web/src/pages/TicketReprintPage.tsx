import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion, AnimatePresence } from 'motion/react';
import {
  Printer, Search, ReceiptText, ChevronLeft, ChevronRight, Eye, Hash, User,
  Store, ShoppingBag, CreditCard, FlaskConical, Calendar,
} from 'lucide-react';
import { api } from '@/lib/api';
import { fadeInUp, staggerContainer, staggerItem } from '@/lib/motion';
import { Ticket, defaultTicketSettings, type TicketSettings, type TicketData } from '@/components/Ticket';
import { InfoBanner } from '@/components/InfoBanner';
import { useTestMode } from '@/lib/testMode';
import { avatarColor, avatarInitials, fmtDate, payMethods } from '@/lib/saleVisuals';
import { toast } from '@/store/toast';
import { setPaperWidth } from '@/lib/thermalPrint';
import { printSale } from '@/lib/printRouter';
import './dashboard.css';
import './pos.css';
import './ticket-reprint.css';

// Venta del listado de reimpresión (contrato de GET /sales/reprint).
interface ReprintSale {
  saleId: number;
  folio: string | null;
  createdAt: string;
  cashier: string | null;
  branchName: string | null;
  total: number;
  customerName: string | null;
  paymentMethods: string | null;
  saleType: string;
  itemCount: number;
  itemsPreview: string | null;
}
// Detalle de una venta (GET /tickets/sales/{id}) con cliente y amount_paid (FEATURE 1).
interface SaleSummary {
  id: number; folio: string | null; total: number;
  cashier: string | null; branchName: string | null; createdAt: string;
  customerName: string | null; amountPaid: number | null;
}
interface SaleTicket {
  sale: SaleSummary;
  lines: { description: string; quantity: number; unitPrice: number; lineTotal: number }[];
  payments: { method: string; amount: number }[];
}
interface Branch { id: number; name: string; active: boolean; }

const money = (n: number) => new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const num = (n: number) => new Intl.NumberFormat('es-MX', { maximumFractionDigits: 3 }).format(n || 0);
const methodLabel = (m: string) =>
  m === 'CASH' ? 'Efectivo' : m === 'CARD' ? 'Tarjeta' : m === 'TRANSFER' ? 'Transferencia'
    : m === 'VOUCHER' ? 'Vale' : m;

const PAGE_SIZE = 12;

// Grid COMPARTIDO cabecera/filas (alineación por construcción, nunca a mano).
// Venta(folio+avatar) · Cliente · Cajero · Sucursal · Artículos · Pago · Tipo · Total · Fecha · Acciones.
const REPRINT_COLS =
  'minmax(150px, 1fr) minmax(120px, 0.9fr) minmax(100px, 0.7fr) minmax(100px, 0.7fr) '
  + 'minmax(150px, 1.1fr) minmax(110px, 0.8fr) 92px 120px 118px 190px';

// Fecha local (es-MX, zona del navegador) a YYYY-MM-DD para los <input type="date"> y atajos.
function toISODate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/**
 * Reimpresión de tickets (premium y robusta): busca ventas por folio/cajero/cliente/pan, filtra por
 * sucursal/método/tipo y por fecha (día o rango con atajos), muestra cada venta como fila-tarjeta con
 * cliente, resumen de artículos y chips, y reimprime el ticket por la impresora instalada. El scope
 * de modo prueba lo decide el backend por test_mode (no hay selector real/prueba aquí).
 */
export function TicketReprintPage() {
  const [query, setQuery] = useState('');
  const [debouncedQ, setDebouncedQ] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [branchId, setBranchId] = useState('');
  const [method, setMethod] = useState('');
  const [saleType, setSaleType] = useState('');
  const [page, setPage] = useState(0);
  const [openId, setOpenId] = useState<number | null>(null);
  const { active: testModeActive } = useTestMode();

  // Debounce del buscador (~300 ms) para no saturar el backend al teclear.
  useEffect(() => {
    const t = setTimeout(() => setDebouncedQ(query.trim()), 300);
    return () => clearTimeout(t);
  }, [query]);
  // Al cambiar cualquier filtro, vuelve a la primera hoja.
  useEffect(() => { setPage(0); }, [debouncedQ, from, to, branchId, method, saleType]);

  const branches = useQuery({
    queryKey: ['branches'],
    queryFn: async () => (await api.get<Branch[]>('/branches')).data,
  });
  const activeBranches = useMemo(() => (branches.data ?? []).filter((b) => b.active), [branches.data]);

  const sales = useQuery({
    queryKey: ['reprint', { q: debouncedQ, from, to, branchId, method, saleType }],
    queryFn: async () => (await api.get<ReprintSale[]>('/sales/reprint', {
      params: {
        q: debouncedQ || undefined, from: from || undefined, to: to || undefined,
        branchId: branchId || undefined, method: method || undefined,
        saleType: saleType || undefined, limit: 300,
      },
    })).data,
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

  const rows = sales.data ?? [];
  const pageCount = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  const safePage = Math.min(page, pageCount - 1);
  const pageRows = rows.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);

  // ¿Hay algún filtro activo? (para diferenciar el estado vacío).
  const hasFilters = !!(debouncedQ || from || to || branchId || method || saleType);

  const ticketData: TicketData | null = detail.data ? {
    folio: detail.data.sale.folio ?? `V-${detail.data.sale.id}`,
    branchName: detail.data.sale.branchName ?? 'Matriz',
    cashier: detail.data.sale.cashier,
    dateTime: new Date(detail.data.sale.createdAt),
    lines: detail.data.lines,
    total: detail.data.sale.total,
    methodLabel: detail.data.payments[0] ? methodLabel(detail.data.payments[0].method) : 'Pago',
    // Recibido/cambio desde amount_paid (para que el reimpreso coincida con el original);
    // cae a Σ pagos / 0 si amount_paid es null (históricos).
    received: detail.data.sale.amountPaid ?? detail.data.payments.reduce((s, p) => s + p.amount, 0),
    change: Math.max(0, (detail.data.sale.amountPaid ?? 0) - detail.data.sale.total),
    customerName: detail.data.sale.customerName,
  } : null;

  // Reimpresión térmica directa por el agente (sin diálogo). Avisa el resultado por toast.
  const reprint = () => {
    if (!ticketData) return;
    void printSale(settings.data ?? defaultTicketSettings, ticketData).then((r) => {
      if (r === 'printed') toast.success('Ticket reimpreso', 'Salió por la impresora de tickets.');
      else toast.error('No se pudo reimprimir', 'No se detectó impresora. Verifica que esté conectada y encendida.');
    });
  };

  // Atajos de fecha: Hoy, Ayer, Esta semana (lunes→hoy). Setean from/to en zona local.
  const today = new Date();
  const todayIso = toISODate(today);
  const setToday = () => { setFrom(todayIso); setTo(todayIso); };
  const setYesterday = () => {
    const y = new Date(); y.setDate(y.getDate() - 1);
    const iso = toISODate(y); setFrom(iso); setTo(iso);
  };
  const setThisWeek = () => {
    const d = new Date();
    const dow = (d.getDay() + 6) % 7; // 0 = lunes
    const monday = new Date(d); monday.setDate(d.getDate() - dow);
    setFrom(toISODate(monday)); setTo(todayIso);
  };
  const clearDates = () => { setFrom(''); setTo(''); };
  const yesterdayIso = (() => { const y = new Date(); y.setDate(y.getDate() - 1); return toISODate(y); })();
  const shortcutActive = from && to && from === to
    ? (to === todayIso ? 'today' : to === yesterdayIso ? 'yesterday' : '')
    : '';

  return (
    <div>
      <div className="sec-title"><ReceiptText size={20} /><h1 className="page-title">Reimpresión de tickets</h1></div>
      <p className="page-sub">Busca una venta y reimprime su comprobante en la impresora de tickets</p>

      <div style={{ marginTop: 'var(--space-3)' }}>
        <InfoBanner icon={<ReceiptText size={18} />} tone="sky" title="Reimpresión de tickets">
          Busca una venta por folio, cajero, cliente o pan, filtra por sucursal, método, tipo o fecha,
          y reimprime su ticket tal cual salió. El ticket muestra el nombre del cliente o “Venta a
          público general”.
        </InfoBanner>
      </div>

      {testModeActive && (
        <div style={{ marginTop: 'var(--space-3)' }}>
          <InfoBanner icon={<FlaskConical size={18} />} tone="amber" title="MODO PRUEBA">
            Mientras el modo prueba esté activo, también aparecen los tickets de práctica para que los
            reimprimas. Al apagarlo, solo quedarán las ventas reales.
          </InfoBanner>
        </div>
      )}

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <div className="rep-controls">
          <div className="rep-search-field">
            <Search size={16} />
            <input value={query} onChange={(e) => setQuery(e.target.value)}
              placeholder="Buscar por folio, cajero, cliente o pan…" autoFocus />
          </div>

          <div className="rep-filters">
            {activeBranches.length > 1 && (
              <label className="rep-filter">
                <span>Sucursal</span>
                <select value={branchId} onChange={(e) => setBranchId(e.target.value)}>
                  <option value="">Todas</option>
                  {activeBranches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
                </select>
              </label>
            )}
            <label className="rep-filter">
              <span>Método</span>
              <select value={method} onChange={(e) => setMethod(e.target.value)}>
                <option value="">Todos</option>
                <option value="CASH">Efectivo</option>
                <option value="CARD">Tarjeta</option>
                <option value="TRANSFER">Transferencia</option>
                <option value="VOUCHER">Vale</option>
              </select>
            </label>
            <label className="rep-filter">
              <span>Tipo</span>
              <select value={saleType} onChange={(e) => setSaleType(e.target.value)}>
                <option value="">Todos</option>
                <option value="NORMAL">Normal</option>
                <option value="SPECIAL">Especial</option>
              </select>
            </label>
            <label className="rep-filter">
              <span>Desde</span>
              <input type="date" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} />
            </label>
            <label className="rep-filter">
              <span>Hasta</span>
              <input type="date" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} />
            </label>
            <div className="rep-shortcuts">
              <button type="button" className={`rep-chip-btn ${shortcutActive === 'today' ? 'is-active' : ''}`} onClick={setToday}>Hoy</button>
              <button type="button" className={`rep-chip-btn ${shortcutActive === 'yesterday' ? 'is-active' : ''}`} onClick={setYesterday}>Ayer</button>
              <button type="button" className="rep-chip-btn" onClick={setThisWeek}>Esta semana</button>
              {(from || to) && <button type="button" className="rep-chip-btn" onClick={clearDates}>Limpiar</button>}
            </div>
          </div>
        </div>

        <div className="rep-list">
          {pageRows.length > 0 && (
            <div className="rep-head" style={{ gridTemplateColumns: REPRINT_COLS }}>
              <span>Venta</span>
              <span>Cliente</span>
              <span>Cajero</span>
              <span>Sucursal</span>
              <span>Artículos</span>
              <span>Pago</span>
              <span>Tipo</span>
              <span className="rep-h-right">Total</span>
              <span className="rep-h-right">Fecha</span>
              <span className="rep-h-right">Acciones</span>
            </div>
          )}
          <motion.div variants={staggerContainer} initial="hidden" animate="visible"
            style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
            {pageRows.map((s) => {
              const label = s.folio ?? `V-${s.saleId}`;
              const avatarName = s.customerName || label;
              const when = fmtDate(s.createdAt);
              const pays = payMethods(s.paymentMethods);
              // Preview de artículos: si el texto es largo o hay muchos renglones, cae a "N artículos".
              const preview = s.itemsPreview && s.itemsPreview.length <= 48 && s.itemCount <= 3
                ? s.itemsPreview
                : `${num(s.itemCount)} ${s.itemCount === 1 ? 'artículo' : 'artículos'}`;
              const isSpecial = s.saleType === 'SPECIAL';
              return (
                <motion.div key={s.saleId} variants={staggerItem} className="rep-row"
                  style={{ gridTemplateColumns: REPRINT_COLS }}>
                  <span className="rep-sale">
                    <span className="rep-avatar" style={{ background: avatarColor(avatarName) }}>
                      {avatarInitials(avatarName)}
                    </span>
                    <span className="rep-folio"><Hash size={12} /> {label}</span>
                  </span>
                  <span className={`rep-customer ${s.customerName ? '' : 'is-none'}`} title={s.customerName || ''}>
                    <User size={13} /> {s.customerName || 'Público general'}
                  </span>
                  <span className={`rep-cashier ${s.cashier ? '' : 'is-none'}`}>
                    <User size={13} /> {s.cashier || '—'}
                  </span>
                  <span className={`rep-branch ${s.branchName ? '' : 'is-none'}`}>
                    <Store size={13} /> {s.branchName || '—'}
                  </span>
                  <span className="rep-items" title={s.itemsPreview || ''}>
                    <ShoppingBag size={13} /> {preview}
                  </span>
                  <span className="rep-pays">
                    {pays.length > 0 ? pays.map((p, i) => (
                      <span key={i} className={`rep-chip-pay ${p.tone}`}>
                        <CreditCard size={11} /> {p.label}
                      </span>
                    )) : <span className="rep-pays-none">—</span>}
                  </span>
                  <span className={`rep-chip-type ${isSpecial ? 'is-special' : 'is-normal'}`}>
                    {isSpecial ? 'Especial' : 'Normal'}
                  </span>
                  <span className="rep-total">{money(s.total)}</span>
                  <span className="rep-when">
                    <span className="rep-when-date">{when.date}</span>
                    {when.time && <span className="rep-when-time">{when.time}</span>}
                  </span>
                  <span className="rep-actions">
                    <button className="btn-ghost" onClick={() => setOpenId(s.saleId)}>
                      <Eye size={15} /> Ver
                    </button>
                    <button className="btn-accent" onClick={() => setOpenId(s.saleId)}>
                      <Printer size={15} /> Imprimir
                    </button>
                  </span>
                </motion.div>
              );
            })}
          </motion.div>

          {pageRows.length === 0 && !sales.isLoading && (
            hasFilters ? (
              <div className="rep-empty">
                <span className="rep-empty-icon"><Search size={24} /></span>
                <strong>Ninguna venta coincide con estos filtros</strong>
                <p>Ajusta la fecha, cambia el método o el tipo, o limpia la búsqueda para ver más tickets.</p>
              </div>
            ) : (
              <div className="rep-empty">
                <span className="rep-empty-icon"><ReceiptText size={24} /></span>
                <strong>Aún no hay ventas registradas</strong>
                <p>Registra una venta en Punto de venta y aquí podrás volver a imprimir su ticket.</p>
              </div>
            )
          )}
          {sales.isLoading && (
            <div className="rep-empty">
              <span className="rep-empty-icon"><Calendar size={24} /></span>
              <strong>Cargando tickets…</strong>
            </div>
          )}
        </div>

        {/* Paginación por hojas */}
        {rows.length > PAGE_SIZE && (
          <div className="rep-pager">
            <button className="btn-ghost" disabled={safePage === 0} onClick={() => setPage((p) => Math.max(p - 1, 0))}>
              <ChevronLeft size={16} /> Anterior
            </button>
            <span className="rep-pager-info">Hoja {safePage + 1} de {pageCount} · {rows.length} tickets</span>
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
    </div>
  );
}
