import { useEffect, useState } from 'react';
import { motion } from 'motion/react';
import { Search, Undo2, RotateCcw, Package, Check } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import { SupervisorAuthModal } from '@/components/SupervisorAuthModal';
import { PrintableVoucher } from '@/components/PrintableVoucher';
import { type VoucherData } from '@/components/Voucher';
import { useSession } from '@/store/session';
import { decodeToken, isBusinessAdmin, hasPermission } from '@/lib/jwt';
import '@/pages/dashboard.css';
import '@/pages/returns.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

interface SaleDetailLine {
  productId: number;
  description: string;
  quantity: number;
  unitPrice: number;
}
interface SaleDetail {
  saleId: number;
  folio: string | null;
  branchId: number;
  customerId: number | null;
  status: string;
  total: number;
  lines: SaleDetailLine[];
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
}

const REFUND_LABEL: Record<string, string> = {
  CASH: 'Efectivo', CREDIT_NOTE: 'Nota de crédito', STORE_CREDIT: 'Saldo a favor',
};

/**
 * Devoluciones: busca la venta original por folio o id, muestra sus renglones y permite
 * seleccionar cantidades a devolver, el motivo y el método de reembolso. Registra la devolución
 * (que valida contra lo vendido, reingresa inventario y deja histórico).
 */
export function ReturnsPage() {
  const [folio, setFolio] = useState('');
  const [sale, setSale] = useState<SaleDetail | null>(null);
  const [qtys, setQtys] = useState<Record<number, number>>({});
  const [reason, setReason] = useState('');
  const [refundMethod, setRefundMethod] = useState('CASH');
  const [loading, setLoading] = useState(false);
  const [history, setHistory] = useState<ReturnRow[]>([]);
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
  useEffect(loadHistory, []);

  const findSale = async () => {
    const id = Number(folio.replace(/[^0-9]/g, ''));
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

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Search size={18} /> Buscar venta</h3>
        <div className="ret-search">
          <input value={folio} onChange={(e) => setFolio(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && findSale()}
            placeholder="Folio del ticket (V-123 o 123)" autoFocus />
          <button className="btn-primary" onClick={findSale} disabled={loading}>
            {loading ? 'Buscando…' : 'Buscar'}
          </button>
        </div>
      </motion.div>

      {sale && (
        <motion.div className="card" style={{ marginTop: 'var(--space-4)' }}
          variants={fadeInUp} initial="hidden" animate="visible">
          <div className="ret-sale-head">
            <div>
              <h3 className="sec-title"><Package size={18} /> Venta {sale.folio || `V-${sale.saleId}`}</h3>
              <span className="ret-sale-sub">Total original: {money(sale.total)}</span>
            </div>
            {sale.status === 'VOIDED' && <span className="badge badge-danger">Cancelada</span>}
          </div>

          <table className="ret-table">
            <thead>
              <tr>
                <th>Producto</th>
                <th className="ta-right">Vendido</th>
                <th className="ta-right">Precio</th>
                <th className="ta-center">Devolver</th>
                <th className="ta-right">Reembolso</th>
              </tr>
            </thead>
            <tbody>
              {sale.lines.map((l) => (
                <tr key={l.productId}>
                  <td>{l.description}</td>
                  <td className="ta-right">{l.quantity}</td>
                  <td className="ta-right">{money(l.unitPrice)}</td>
                  <td className="ta-center">
                    <div className="ret-qty">
                      <button onClick={() => setQty(l.productId, (qtys[l.productId] || 0) - 1, l.quantity)}>−</button>
                      <input value={qtys[l.productId] || 0} inputMode="numeric"
                        onChange={(e) => setQty(l.productId, Number(e.target.value) || 0, l.quantity)} />
                      <button onClick={() => setQty(l.productId, (qtys[l.productId] || 0) + 1, l.quantity)}>+</button>
                    </div>
                  </td>
                  <td className="ta-right">{money((qtys[l.productId] || 0) * l.unitPrice)}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="ret-foot">
            <div className="ret-foot-fields">
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
            <div className="ret-total">
              <span>Total a reembolsar</span>
              <strong>{money(refundTotal)}</strong>
              <button className="btn-accent" disabled={!hasItems} onClick={submit}>
                <Check size={16} /> Registrar devolución
              </button>
            </div>
          </div>
        </motion.div>
      )}

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }}
        variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><RotateCcw size={18} /> Devoluciones recientes</h3>
        <table className="ret-table">
          <thead>
            <tr>
              <th>Folio</th><th>Venta</th><th>Motivo</th><th>Reembolso</th>
              <th className="ta-right">Importe</th><th>Procesó</th>
            </tr>
          </thead>
          <tbody>
            {history.map((r) => (
              <tr key={r.id}>
                <td><strong>{r.folio}</strong></td>
                <td>{r.saleId ? `V-${r.saleId}` : '—'}</td>
                <td>{r.reason || <span className="muted">—</span>}</td>
                <td>{REFUND_LABEL[r.refundMethod] || r.refundMethod}</td>
                <td className="ta-right">{money(r.total)}</td>
                <td>{r.processedBy || '—'}</td>
              </tr>
            ))}
            {history.length === 0 && (
              <tr><td colSpan={6} className="ret-empty">
                <Undo2 size={20} /> Aún no hay devoluciones registradas.
              </td></tr>
            )}
          </tbody>
        </table>
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
