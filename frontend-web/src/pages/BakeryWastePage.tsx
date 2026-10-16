import { useEffect, useState } from 'react';
import { motion } from 'motion/react';
import { Trash2, Search } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/returns.css';
import '@/pages/purchases.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const int = (n: number) => new Intl.NumberFormat('es-MX').format(n || 0);

/** Registro de merma de GET /bakery/waste. */
interface WasteRow {
  id: number;
  saleId: number | null;
  saleFolio: string | null;
  productId: number | null;
  productName: string | null;
  quantity: number | null;
  entryPrice: number | null;
  wastePrice: number | null;
  difference: number | null;
  cashier: string | null;
  createdAt: string | null;
}

function todayIso(): string {
  const d = new Date();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/**
 * Historial de MERMA vendida (solo-lectura, trazabilidad para el admin). Muestra por registro el
 * precio de entrada, el precio de merma, la diferencia por unidad, el cajero, la fecha, el folio de
 * la venta y el artículo. No permite editar: la merma se captura en el POS al momento de la venta.
 */
export function BakeryWastePage() {
  const [from, setFrom] = useState<string>(todayIso());
  const [to, setTo] = useState<string>(todayIso());
  const [rows, setRows] = useState<WasteRow[]>([]);
  const [busy, setBusy] = useState(false);

  const load = () => {
    setBusy(true);
    api.get<WasteRow[]>('/bakery/waste', { params: { from, to } })
      .then((r) => setRows(r.data))
      .catch(() => { setRows([]); toast.error('No se pudo cargar el historial de merma'); })
      .finally(() => setBusy(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const totalUnits = rows.reduce((s, r) => s + (r.quantity ?? 0), 0);
  const totalLoss = rows.reduce((s, r) => s + (r.difference ?? 0) * (r.quantity ?? 0), 0);

  return (
    <div>
      <h1 className="page-title">Merma vendida</h1>
      <p className="page-sub">Pan vendido como merma, con su precio de entrada, precio de merma y la diferencia. Solo lectura.</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="pur-head">
          <label className="field"><span>Desde</span>
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
          <label className="field"><span>Hasta</span>
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
          <div className="field" style={{ alignSelf: 'end' }}>
            <button className="btn-accent" disabled={busy} onClick={load}><Search size={16} /> Consultar</button>
          </div>
        </div>
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Trash2 size={18} /> Registros de merma</h3>
        <table className="ret-table">
          <thead><tr>
            <th>Fecha</th><th>Folio venta</th><th>Artículo</th><th className="ta-right">Cantidad</th>
            <th className="ta-right">Precio entrada</th><th className="ta-right">Precio merma</th>
            <th className="ta-right">Diferencia</th><th>Cajero</th>
          </tr></thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td>{r.createdAt || '—'}</td>
                <td><strong>{r.saleFolio || (r.saleId != null ? `V-${r.saleId}` : '—')}</strong></td>
                <td>{r.productName || <span className="muted">—</span>}</td>
                <td className="ta-right">{int(r.quantity ?? 0)}</td>
                <td className="ta-right">{money(r.entryPrice ?? 0)}</td>
                <td className="ta-right">{money(r.wastePrice ?? 0)}</td>
                <td className="ta-right">{money(r.difference ?? 0)}</td>
                <td>{r.cashier || '—'}</td>
              </tr>
            ))}
            {rows.length === 0 && <tr><td colSpan={8} className="ret-empty">Sin mermas en el periodo.</td></tr>}
          </tbody>
        </table>

        {rows.length > 0 && (
          <div className="pur-foot">
            <div />
            <div className="pur-totals">
              <div className="pur-total-row"><span>Piezas en merma</span><span>{int(totalUnits)}</span></div>
              <div className="pur-total-row pur-total-grand"><span>Pérdida total</span><strong>{money(totalLoss)}</strong></div>
            </div>
          </div>
        )}
      </motion.div>
    </div>
  );
}
