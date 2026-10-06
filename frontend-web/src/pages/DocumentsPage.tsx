import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { FileArchive, Search, Printer } from 'lucide-react';
import { api } from '@/lib/api';
import { fadeInUp } from '@/lib/motion';
import { PrintableVoucher } from '@/components/PrintableVoucher';
import { type VoucherData } from '@/components/Voucher';
import '@/pages/dashboard.css';
import '@/pages/customers.css';
import '@/pages/invoicing.css';

interface DocRow {
  id: number; docType: string; folio: string | null; title: string | null;
  shiftId: number | null; amount: number | null; actor: string | null;
  reprint: boolean; createdAt: string;
}
interface DocDetail {
  id: number; docType: string; folio: string | null; title: string | null;
  amount: number | null; actor: string | null; payload: Record<string, unknown> | null; createdAt: string;
}
interface Summary { docType: string; count: number; total: number; }

const money = (n: number) => new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const today = () => new Date().toISOString().slice(0, 10);

const TYPE_LABEL: Record<string, string> = {
  SALE: 'Venta', CUT_X: 'Corte X', CUT_Z: 'Corte Z (cierre)',
  CASH_MOVEMENT: 'Movimiento de efectivo', RETURN: 'Devolución', TRANSFER: 'Traspaso', VOID: 'Cancelación',
};
const TYPES = ['', 'SALE', 'CUT_Z', 'CUT_X', 'CASH_MOVEMENT', 'RETURN', 'TRANSFER', 'VOID'];

/**
 * Comprobantes y cortes (para el dueño/administrador): consulta clasificada de todos los
 * comprobantes que respaldan movimientos de dinero o mercancía, con filtros por tipo, cajero y
 * fecha, y reimpresión. Da trazabilidad total para cero pérdidas.
 */
export function DocumentsPage() {
  const [type, setType] = useState('');
  const [cashier, setCashier] = useState('');
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(today());
  const [voucher, setVoucher] = useState<VoucherData | null>(null);

  const docs = useQuery({
    queryKey: ['documents', type, cashier, from, to],
    queryFn: async () => (await api.get<DocRow[]>('/documents', { params: { type: type || undefined, cashier: cashier || undefined, from, to } })).data,
  });
  const summary = useQuery({
    queryKey: ['documents', 'summary', from, to],
    queryFn: async () => (await api.get<Summary[]>('/documents/summary', { params: { from, to } })).data,
  });

  const rows = docs.data ?? [];
  const summaryData = summary.data ?? [];
  const grandTotal = useMemo(() => summaryData.reduce((a, s) => a + (Number(s.total) || 0), 0), [summaryData]);

  // Reimprime un comprobante a partir de su payload guardado.
  const reprint = async (id: number) => {
    try {
      const { data } = await api.get<DocDetail>(`/documents/${id}`);
      const p = (data.payload ?? {}) as Record<string, unknown>;
      const rows2: { label: string; value: string }[] = Object.entries(p)
        .filter(([, v]) => typeof v === 'number' || typeof v === 'string')
        .slice(0, 12)
        .map(([k, v]) => ({ label: k, value: typeof v === 'number' ? money(v) : String(v) }));
      setVoucher({
        title: (data.title ?? TYPE_LABEL[data.docType] ?? 'Comprobante').toUpperCase(),
        storeName: 'Reimpresión de comprobante',
        folio: data.folio ?? `#${data.id}`,
        dateTime: new Date(data.createdAt),
        cashier: data.actor,
        rows: rows2.length ? rows2 : [{ label: 'Importe', value: money(data.amount ?? 0) }],
        footer: 'Reimpresión · respaldo.',
      });
    } catch {
      // silencioso
    }
  };

  return (
    <div>
      <h1 className="page-title">Comprobantes y cortes</h1>
      <p className="page-sub">Trazabilidad de todo movimiento de dinero y mercancía. Consulta e reimprime evidencia.</p>

      {/* Resumen por tipo */}
      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="inv-toolbar">
          <label className="field"><span>Fecha inicial</span><input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
          <label className="field"><span>Fecha final</span><input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
          <div className="inv-chip" style={{ marginLeft: 'auto' }}>Total del periodo: <strong>{money(grandTotal)}</strong></div>
        </div>
        <div className="doc-summary">
          {summaryData.map((s) => (
            <button key={s.docType} className={`doc-summary-card ${type === s.docType ? 'is-on' : ''}`} onClick={() => setType(type === s.docType ? '' : s.docType)}>
              <span className="doc-summary-type">{TYPE_LABEL[s.docType] ?? s.docType}</span>
              <span className="doc-summary-count">{s.count}</span>
              <span className="doc-summary-total">{money(s.total)}</span>
            </button>
          ))}
          {summaryData.length === 0 && <p className="empty">Sin comprobantes en el periodo.</p>}
        </div>
      </motion.div>

      {/* Listado */}
      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <div className="inv-toolbar">
          <label className="field"><span>Tipo</span>
            <select value={type} onChange={(e) => setType(e.target.value)}>
              {TYPES.map((t) => <option key={t} value={t}>{t === '' ? 'Todos' : TYPE_LABEL[t]}</option>)}
            </select></label>
          <label className="field" style={{ flex: 1, minWidth: 200 }}><span>Cajero / responsable</span>
            <div className="cust-search"><Search size={16} /><input value={cashier} onChange={(e) => setCashier(e.target.value)} placeholder="Nombre o correo…" /></div></label>
        </div>
        <div className="inv-table-wrap">
          <table className="inv-table">
            <thead><tr><th>Tipo</th><th>Folio</th><th>Responsable</th><th>Fecha</th><th className="num">Importe</th><th></th></tr></thead>
            <tbody>
              {rows.map((d) => (
                <tr key={d.id}>
                  <td>{TYPE_LABEL[d.docType] ?? d.docType}{d.reprint && <span className="doc-reprint"> (reimp.)</span>}</td>
                  <td><strong>{d.folio ?? `#${d.id}`}</strong></td>
                  <td>{d.actor ?? '—'}</td>
                  <td>{new Date(d.createdAt).toLocaleString('es-MX', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}</td>
                  <td className="num">{d.amount != null ? money(d.amount) : '—'}</td>
                  <td><button className="btn-ghost" onClick={() => reprint(d.id)}><Printer size={14} /> Reimprimir</button></td>
                </tr>
              ))}
              {rows.length === 0 && <tr><td colSpan={6}><div className="inv-empty"><FileArchive size={26} /><p>Sin comprobantes con estos filtros.</p></div></td></tr>}
            </tbody>
          </table>
        </div>
      </motion.div>

      {voucher && <PrintableVoucher data={voucher} onDone={() => setVoucher(null)} />}
    </div>
  );
}
