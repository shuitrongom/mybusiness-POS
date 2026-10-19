import { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import {
  Search, Undo2, RotateCcw, Package, Check, Calendar, User, DollarSign, Hash, Receipt,
} from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import { InfoBanner } from '@/components/InfoBanner';
import { SupervisorAuthModal } from '@/components/SupervisorAuthModal';
import { PrintableVoucher } from '@/components/PrintableVoucher';
import { type VoucherData } from '@/components/Voucher';
import { useSession } from '@/store/session';
import { decodeToken, isBusinessAdmin, hasPermission } from '@/lib/jwt';
import '@/pages/dashboard.css';
import '@/pages/returns.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const num = (n: number) => new Intl.NumberFormat('es-MX', { maximumFractionDigits: 3 }).format(n || 0);

// Avatar del artículo (color estable + iniciales), coherente con el resto de la app
// (mismos helpers que BakeryDayStockPage / InventoryDocPage).
const AVATAR_COLORS = ['#2e6ef2', '#12b886', '#f59e0b', '#e11d48', '#7c5cff', '#0ea5e9', '#d97706', '#0891b2'];
function avatarColor(text: string): string {
  let hash = 0;
  for (let i = 0; i < text.length; i++) hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
  return AVATAR_COLORS[hash % AVATAR_COLORS.length];
}
function avatarInitials(name: string): string {
  const w = name.trim().split(/\s+/).filter(Boolean);
  if (w.length === 0) return '?';
  return (w.length === 1 ? w[0].slice(0, 2) : w[0][0] + w[1][0]).toUpperCase();
}

// Fecha + hora del registro, apiladas (es-MX), coherente con el historial de Entradas/Salidas.
function fmtDate(iso: string | null): { date: string; time: string } {
  if (!iso) return { date: '—', time: '' };
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return { date: '—', time: '' };
  return {
    date: d.toLocaleDateString('es-MX', { day: '2-digit', month: 'short', year: 'numeric' }),
    time: d.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' }),
  };
}

interface SaleDetailLine {
  productId: number;
  description: string;
  quantity: number;
  unitPrice: number;
  unit: string | null;
  productName: string | null;
  active: boolean;
  alreadyReturned: number;
}
interface SaleDetail {
  saleId: number;
  folio: string | null;
  branchId: number;
  customerId: number | null;
  status: string;
  total: number;
  lines: SaleDetailLine[];
  createdAt: string | null;
  customerName: string | null;
}
interface ReturnRow {
  id: number;
  folio: string;
  saleId: number | null;
  total: number;
  refundMethod: string;
  reason: string | null;
  processedBy: string | null;
  createdAt: string;
  itemCount?: number;
}
// Venta reciente COMPLETED devolvible (selector que aparece al entrar, sin teclear).
interface RecentSale {
  saleId: number;
  folio: string | null;
  createdAt: string | null;
  total: number;
  customerName: string | null;
  itemCount: number;
  returnedCount: number;
  fullyReturned: boolean;
}

const REFUND_LABEL: Record<string, string> = {
  CASH: 'Efectivo', CREDIT_NOTE: 'Nota de crédito', STORE_CREDIT: 'Saldo a favor',
};

// Grid COMPARTIDO cabecera/filas (alineación por construcción, nunca a mano).
const LINE_COLS = 'minmax(200px, 1fr) 96px 120px 150px 120px';
const HIST_COLS = 'minmax(90px, 0.7fr) 96px minmax(140px, 1fr) 150px 120px minmax(120px, 0.8fr) 150px';
// Grid compartido cabecera/filas del selector de ventas recientes (alineación por construcción).
const PICK_COLS = 'minmax(180px, 1.2fr) minmax(150px, 1fr) 90px minmax(150px, 0.9fr) 140px';

/**
 * Devoluciones: busca la venta original por folio o id, muestra sus renglones (con avatar, cantidad
 * vendida vs ya devuelta y precio bloqueado) y permite seleccionar cuánto devolver, el motivo y el
 * método de reembolso. Registra la devolución (valida contra lo vendido, reingresa inventario y deja
 * histórico). Mismo lenguaje visual premium que Entradas/Salidas/Existencias.
 */
export function ReturnsPage() {
  const [folio, setFolio] = useState('');
  const [sale, setSale] = useState<SaleDetail | null>(null);
  const [qtys, setQtys] = useState<Record<number, number>>({});
  const [reason, setReason] = useState('');
  const [refundMethod, setRefundMethod] = useState('CASH');
  const [loading, setLoading] = useState(false);
  const [history, setHistory] = useState<ReturnRow[]>([]);
  const [recent, setRecent] = useState<RecentSale[]>([]);
  const [recentLoading, setRecentLoading] = useState(true);
  const [authOpen, setAuthOpen] = useState(false);
  const [voucher, setVoucher] = useState<VoucherData | null>(null);

  const token = useSession((s) => s.token);
  const claims = decodeToken(token);
  // Puede devolver sin autorización si es Dueño/Admin o su rol tiene el permiso sales:RETURN.
  const canReturnDirectly = isBusinessAdmin(claims) || hasPermission(claims, 'sales', 'RETURN');

  const loadHistory = () => {
    api.get<ReturnRow[]>('/sales/returns', { params: { limit: 20 } })
      .then((r) => setHistory(r.data)).catch(() => setHistory([]));
  };
  // Al entrar, carga las últimas ventas COMPLETED devolvibles para mostrarlas sin que el
  // cajero teclee nada (igual que el buscador de productos muestra la lista completa).
  const loadRecent = () => {
    setRecentLoading(true);
    api.get<RecentSale[]>('/sales/recent', { params: { limit: 20 } })
      .then((r) => setRecent(r.data))
      .catch(() => setRecent([]))
      .finally(() => setRecentLoading(false));
  };
  useEffect(() => { loadHistory(); loadRecent(); }, []);

  // Carga el detalle de una venta (por folio tecleado o por id de una fila seleccionada) y
  // prepara los steppers. Reusa el flujo actual GET /sales/{id}.
  const findSale = async (explicitId?: number) => {
    const id = explicitId ?? Number(folio.replace(/[^0-9]/g, ''));
    if (!id) { toast.info('Escribe un folio válido', 'Ejemplo: V-123 o 123.'); return; }
    setLoading(true);
    try {
      const { data } = await api.get<SaleDetail>(`/sales/${id}`);
      setSale(data);
      setQtys(Object.fromEntries(data.lines.map((l) => [l.productId, 0])));
      if (data.status === 'VOIDED') toast.info('Venta cancelada', 'Esta venta ya fue cancelada.');
    } catch {
      setSale(null);
      toast.error('Venta no encontrada', `No existe la venta ${id}.`);
    } finally {
      setLoading(false);
    }
  };

  // El texto del buscador filtra en vivo la lista de ventas recientes (patrón de ProductPicker:
  // filtrado en memoria sobre la lista ya cargada). Vacío = muestra todas.
  const filteredRecent = useMemo(() => {
    const q = folio.trim().toLowerCase();
    if (!q) return recent;
    const digits = q.replace(/[^0-9]/g, '');
    return recent.filter((s) => {
      const label = (s.folio || `V-${s.saleId}`).toLowerCase();
      const customer = (s.customerName || '').toLowerCase();
      const when = fmtDate(s.createdAt);
      const date = `${when.date} ${when.time}`.toLowerCase();
      return label.includes(q) || customer.includes(q) || date.includes(q)
        || (digits !== '' && String(s.saleId).includes(digits));
    });
  }, [folio, recent]);

  // Selecciona una venta de la lista: sincroniza el texto del folio y carga su detalle.
  const selectRecent = (s: RecentSale) => {
    setFolio(s.folio || `V-${s.saleId}`);
    findSale(s.saleId);
  };

  // Lo que queda por devolver de un renglón = vendido − ya devuelto (no se excede; segunda
  // barrera en backend). El tope del stepper usa ese remanente, no la cantidad vendida.
  const remainingOf = (l: SaleDetailLine) => Math.max(0, l.quantity - (l.alreadyReturned || 0));

  const setQty = (productId: number, q: number, max: number) => {
    setQtys((prev) => ({ ...prev, [productId]: Math.max(0, Math.min(q, max)) }));
  };

  const refundTotal = sale
    ? sale.lines.reduce((s, l) => s + (qtys[l.productId] || 0) * l.unitPrice, 0)
    : 0;
  const hasItems = Object.values(qtys).some((q) => q > 0);

  const doReturn = async () => {
    if (!sale || !hasItems) return;
    const items = sale.lines
      .filter((l) => (qtys[l.productId] || 0) > 0)
      .map((l) => ({
        productId: l.productId, description: l.description,
        quantity: qtys[l.productId], unitPrice: l.unitPrice,
      }));
    try {
      await api.post('/sales/returns', {
        saleId: sale.saleId, branchId: sale.branchId, customerId: sale.customerId,
        reason: reason || null, refundMethod, items,
      });
      // Comprobante de devolución para respaldo (qué se devolvió y cuánto se reembolsó).
      setVoucher({
        title: 'DEVOLUCIÓN DE VENTA',
        storeName: 'Comprobante de devolución',
        folio: `DEV V-${sale.saleId}`,
        dateTime: new Date(),
        items: items.map((it) => ({ description: it.description, quantity: it.quantity, amount: it.quantity * it.unitPrice })),
        rows: [
          { label: 'Reembolso', value: REFUND_LABEL[refundMethod] || refundMethod },
          { label: 'Total devuelto', value: money(refundTotal), strong: true },
        ],
        note: reason ? `Motivo: ${reason}` : null,
        signature: true,
        footer: 'Comprobante de devolución · respaldo.',
      });
      toast.success('Devolución registrada', `Se reembolsan ${money(refundTotal)} y se reingresó el inventario.`);
      setSale(null); setFolio(''); setReason(''); setQtys({});
      loadHistory();
    } catch (e: unknown) {
      const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
      toast.error('No se pudo registrar la devolución', msg || 'Revisa las cantidades.');
    }
  };

  // Las devoluciones requieren autorización: si el usuario no puede devolver por su rol, pide
  // el PIN de un supervisor antes de procesar.
  const submit = () => {
    if (!sale || !hasItems) return;
    if (canReturnDirectly) doReturn();
    else setAuthOpen(true);
  };

  return (
    <div>
      <h1 className="page-title">Devoluciones</h1>
      <p className="page-sub">Devuelve productos de una venta, reingresa inventario y registra el reembolso</p>

      <div style={{ marginTop: 'var(--space-3)' }}>
        <InfoBanner icon={<Undo2 size={18} />} tone="sky" title="Devoluciones">
          Busca una venta por su folio para devolver productos: reingresa el inventario y registra el
          reembolso. El precio lo manda el artículo (bloqueado); solo eliges cuánto devolver de cada
          renglón.
        </InfoBanner>
      </div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }}
        variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Search size={18} /> Buscar venta</h3>
        <div className="rret-search">
          <div className="rret-search-field">
            <Search size={16} />
            <input value={folio} onChange={(e) => setFolio(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && findSale()}
              placeholder="Filtra por folio o cliente… o escribe el folio y Enter" autoFocus />
          </div>
          <button className="btn-primary" onClick={() => findSale()} disabled={loading}>
            <Search size={16} /> {loading ? 'Buscando…' : 'Buscar'}
          </button>
        </div>

        {/* Ventas recientes devolvibles: aparecen al entrar (sin teclear) y se filtran en vivo.
            Cada fila es seleccionable y carga el detalle de esa venta. */}
        <div className="rret-pick">
          {!recentLoading && recent.length > 0 && filteredRecent.length > 0 && (
            <div className="rret-pick-head" style={{ gridTemplateColumns: PICK_COLS }}>
              <span>Venta</span>
              <span>Cliente</span>
              <span className="rret-h-center">Artículos</span>
              <span className="rret-h-right">Total</span>
              <span className="rret-h-right">Fecha</span>
            </div>
          )}
          {filteredRecent.map((s) => {
            const label = s.folio || `V-${s.saleId}`;
            const avatarName = s.customerName || label;
            const when = fmtDate(s.createdAt);
            return (
              <button type="button" className="rret-pick-row" key={s.saleId}
                style={{ gridTemplateColumns: PICK_COLS }}
                onClick={() => selectRecent(s)}>
                <span className="rret-pick-sale">
                  <span className="rret-article-avatar" style={{ background: avatarColor(avatarName) }}>
                    {avatarInitials(avatarName)}
                  </span>
                  <span className="rret-pick-folio"><Hash size={12} /> {label}</span>
                  {s.fullyReturned ? (
                    <span className="rret-chip-fully">Devuelta</span>
                  ) : s.returnedCount > 0 ? (
                    <span className="rret-chip-returned">Con devoluciones</span>
                  ) : null}
                </span>
                <span className={`rret-pick-customer ${s.customerName ? '' : 'is-none'}`}>
                  <User size={13} /> {s.customerName || 'Mostrador'}
                </span>
                <span className="rret-pick-count">{num(s.itemCount)}</span>
                <span className="rret-pick-total">{money(s.total)}</span>
                <span className="rret-pick-when">
                  <span className="rret-pick-when-date">{when.date}</span>
                  {when.time && <span className="rret-pick-when-time">{when.time}</span>}
                </span>
              </button>
            );
          })}
          {!recentLoading && recent.length === 0 && (
            <div className="rret-empty">
              <span className="rret-empty-icon"><Receipt size={24} /></span>
              <strong>Aún no hay ventas registradas para devolver</strong>
              <p>Registra una venta en Punto de venta para poder devolverla aquí.</p>
            </div>
          )}
          {!recentLoading && recent.length > 0 && filteredRecent.length === 0 && (
            <div className="rret-empty">
              <span className="rret-empty-icon"><Search size={24} /></span>
              <strong>Ninguna venta coincide</strong>
              <p>Prueba con otro folio o cliente, o escribe el folio exacto y pulsa Buscar.</p>
            </div>
          )}
        </div>
      </motion.div>

      {sale && (() => {
        const when = fmtDate(sale.createdAt);
        return (
          <motion.div className="card" style={{ marginTop: 'var(--space-4)' }}
            variants={fadeInUp} initial="hidden" animate="visible">
            <div className="rret-sale-head">
              <div>
                <h3 className="sec-title"><Package size={18} /> Venta {sale.folio || `V-${sale.saleId}`}</h3>
                <div className="rret-sale-meta">
                  <span className="rret-meta-chip"><Hash size={13} /> {sale.folio || `V-${sale.saleId}`}</span>
                  {sale.createdAt && (
                    <span className="rret-meta-chip">
                      <Calendar size={13} /> {when.date}{when.time && ` · ${when.time}`}
                    </span>
                  )}
                  {sale.customerName && (
                    <span className="rret-meta-chip is-info"><User size={13} /> {sale.customerName}</span>
                  )}
                  <span className="rret-meta-chip is-total">
                    <DollarSign size={13} /> Total original: {money(sale.total)}
                  </span>
                </div>
              </div>
              {sale.status === 'VOIDED' && <span className="badge badge-danger">Cancelada</span>}
            </div>

            <div className="rret-lines">
              <div className="rret-line-head" style={{ gridTemplateColumns: LINE_COLS }}>
                <span>Artículo</span>
                <span className="rret-h-center">Vendido</span>
                <span className="rret-h-center">Precio</span>
                <span className="rret-h-center">Devolver</span>
                <span className="rret-h-center">Reembolso</span>
              </div>
              {sale.lines.map((l) => {
                const name = l.productName || l.description;
                const q = qtys[l.productId] || 0;
                const max = remainingOf(l);
                const isFree = l.productId == null;
                const isDisabled = !isFree && !l.active;
                return (
                  <div className="rret-line" key={l.productId} style={{ gridTemplateColumns: LINE_COLS }}>
                    <div className="rret-article">
                      <span className="rret-article-avatar" style={{ background: avatarColor(name) }}>
                        {avatarInitials(name)}
                      </span>
                      <div className="rret-article-text">
                        <div className="rret-article-name">
                          <span>{name}</span>
                          {isDisabled && <span className="rret-chip-off">Desactivado</span>}
                        </div>
                        <div className="rret-article-unit">{l.unit || 'pieza'}</div>
                      </div>
                    </div>
                    <div className="rret-sold">
                      <span className="rret-sold-qty">{num(l.quantity)}</span>
                      {l.alreadyReturned > 0 && (
                        <span className="rret-returned">ya devuelto: {num(l.alreadyReturned)}</span>
                      )}
                    </div>
                    <span className="rret-fixed-price">{money(l.unitPrice)}</span>
                    <div className={`rret-qty ${q === 0 ? 'is-empty' : ''}`}>
                      <button onClick={() => setQty(l.productId, q - 1, max)} disabled={q <= 0}>−</button>
                      <input value={q === 0 ? '' : q} inputMode="numeric" placeholder="0"
                        onChange={(e) => setQty(l.productId, Number(e.target.value) || 0, max)} />
                      <button onClick={() => setQty(l.productId, q + 1, max)} disabled={q >= max}>+</button>
                    </div>
                    <span className={`rret-refund ${q === 0 ? 'is-zero' : ''}`}>{money(q * l.unitPrice)}</span>
                  </div>
                );
              })}
            </div>

            <div className="rret-foot">
              <div className="rret-foot-fields">
                <label className="field">
                  <span>Motivo</span>
                  <input value={reason} onChange={(e) => setReason(e.target.value)}
                    placeholder="Producto defectuoso, cambio de opinión…" />
                </label>
                <label className="field">
                  <span>Reembolso</span>
                  <select value={refundMethod} onChange={(e) => setRefundMethod(e.target.value)}>
                    <option value="CASH">Efectivo</option>
                    <option value="CREDIT_NOTE">Nota de crédito</option>
                    <option value="STORE_CREDIT">Saldo a favor</option>
                  </select>
                </label>
              </div>
              <div className="rret-total">
                <span>Total a reembolsar</span>
                <strong>{money(refundTotal)}</strong>
                <button className="btn-accent" disabled={!hasItems} onClick={submit}>
                  <Check size={16} /> Registrar devolución
                </button>
              </div>
            </div>
          </motion.div>
        );
      })()}

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }}
        variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><RotateCcw size={18} /> Devoluciones recientes</h3>
        <div className="rret-hist">
          {history.length > 0 && (
            <div className="rret-hist-head" style={{ gridTemplateColumns: HIST_COLS }}>
              <span>Folio</span>
              <span>Venta</span>
              <span>Motivo</span>
              <span>Reembolso</span>
              <span className="rret-h-right">Importe</span>
              <span>Procesó</span>
              <span className="rret-h-right">Fecha</span>
            </div>
          )}
          <div className="rret-hist-body">
            {history.map((r) => {
              const when = fmtDate(r.createdAt);
              return (
                <div className="rret-hist-row" key={r.id} style={{ gridTemplateColumns: HIST_COLS }}>
                  <span className="rret-hist-folio">{r.folio}</span>
                  <span className={`rret-hist-venta ${r.saleId ? '' : 'is-none'}`}>
                    {r.saleId ? `V-${r.saleId}` : '—'}
                  </span>
                  <span className={`rret-chip-reason ${r.reason ? '' : 'is-none'}`} title={r.reason || ''}>
                    {r.reason || '—'}
                  </span>
                  <span className="rret-chip-refund">{REFUND_LABEL[r.refundMethod] || r.refundMethod}</span>
                  <span className="rret-hist-amount">{money(r.total)}</span>
                  <span className={`rret-hist-who ${r.processedBy ? '' : 'is-none'}`}>
                    {r.processedBy || '—'}
                  </span>
                  <span className="rret-hist-when">
                    <span className="rret-hist-when-date">{when.date}</span>
                    {when.time && <span className="rret-hist-when-time">{when.time}</span>}
                  </span>
                </div>
              );
            })}
          </div>
          {history.length === 0 && (
            <div className="rret-empty">
              <span className="rret-empty-icon"><Undo2 size={24} /></span>
              <strong>Aún no hay devoluciones</strong>
              <p>Busca una venta por su folio arriba para registrar la primera: se reingresa el
                inventario y se registra el reembolso.</p>
            </div>
          )}
        </div>
      </motion.div>

      <SupervisorAuthModal
        open={authOpen}
        action="Registrar devolución"
        onClose={() => setAuthOpen(false)}
        onAuthorized={(by) => { setAuthOpen(false); toast.info('Autorizado', `Por ${by}.`); doReturn(); }}
      />

      {voucher && <PrintableVoucher data={voucher} onDone={() => setVoucher(null)} />}
    </div>
  );
}
