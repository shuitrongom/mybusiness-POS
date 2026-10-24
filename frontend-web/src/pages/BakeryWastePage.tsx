import { useEffect, useState } from 'react';
import { motion } from 'motion/react';
import { Trash2, Search } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import { InfoBanner } from '@/components/InfoBanner';
import '@/pages/dashboard.css';
import '@/pages/returns.css';
import '@/pages/purchases.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const int = (n: number) => new Intl.NumberFormat('es-MX').format(n || 0);

/** Registro de movimiento de merma de GET /bakery/waste (todos los orígenes). */
interface WasteRow {
  id: number;
  folio: string | null;
  productId: number | null;
  productName: string | null;
  origin: string | null;
  quantity: number | null;
  normalPrice: number | null;
  wastePrice: number | null;
  difference: number | null;
  cashier: string | null;
  saleId: number | null;
  saleFolio: string | null;
  shiftId: number | null;
  createdAt: string | null;
}

/** Etiqueta legible de cada origen de merma. */
const ORIGIN_LABEL: Record<string, string> = {
  CIERRE: 'Cierre de caja',
  MANUAL: 'Merma manual',
  VENTA: 'Venta de merma',
  REPARTO: 'Reparto',
  ENVEJECIMIENTO: 'Envejecimiento',
};

/** Color del chip por origen (coherente con el resto de la UI). */
function originColor(origin: string | null): string {
  switch (origin) {
    case 'CIERRE': return '#7c5cff';
    case 'MANUAL': return '#d97706';
    case 'VENTA': return '#12b886';
    case 'REPARTO': return '#0ea5e9';
    case 'ENVEJECIMIENTO': return '#d97706';
    default: return '#64748b';
  }
}

function todayIso(): string {
  const d = new Date();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${mm}-${dd}`;
}

/**
 * Reporte de MERMA (solo lectura, trazabilidad para el admin). Reúne TODOS los movimientos de
 * merma por rango: cierre automático de caja, merma manual, venta de merma y reparto. Muestra por
 * registro el origen, el artículo, la cantidad, el precio normal de referencia, el precio de merma,
 * la diferencia (pérdida) y el cajero. Se alimenta de los cuatro orígenes del flujo integrado.
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
      .catch(() => { setRows([]); toast.error('No se pudo cargar el reporte de merma'); })
      .finally(() => setBusy(false));
  };

  useEffect(() => {
    load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const totalUnits = rows.reduce((s, r) => s + (r.quantity ?? 0), 0);
  // El backend ya entrega 'difference' como el total del renglón: (precio normal − merma) × cantidad.
  const totalLoss = rows.reduce((s, r) => s + (r.difference ?? 0), 0);

  return (
    <div>
      <h1 className="page-title">Reporte de merma</h1>
      <p className="page-sub">Todos los movimientos de merma del periodo: cierre, manual, venta y reparto. Solo lectura.</p>

      <div style={{ margin: 'var(--space-3) 0' }}>
        <InfoBanner icon={<Trash2 size={18} />} tone="amber" title="Trazabilidad de merma">
          Cada movimiento de merma queda registrado con su origen: CIERRE (automático al cerrar caja),
          ENVEJECIMIENTO (pan del día anterior pasado a merma por el sistema al no venderse),
          MANUAL (en salidas), VENTA (merma vendida en el POS) y REPARTO (entregada a repartidor).
          Aquí ves la pérdida entre el precio normal y el de merma.
        </InfoBanner>
      </div>

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
        <h3 className="sec-title"><Trash2 size={18} /> Movimientos de merma</h3>
        <table className="ret-table">
          <thead><tr>
            <th>Fecha</th><th>Folio</th><th>Origen</th><th>Artículo</th><th className="ta-right">Cantidad</th>
            <th className="ta-right">Precio normal</th><th className="ta-right">Precio merma</th>
            <th className="ta-right">Diferencia</th><th>Referencia</th><th>Cajero</th>
          </tr></thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id}>
                <td>{r.createdAt ? new Date(r.createdAt).toLocaleString('es-MX') : '—'}</td>
                <td>{r.folio || '—'}</td>
                <td>
                  <span style={{
                    background: originColor(r.origin), color: '#fff', borderRadius: 999,
                    padding: '2px 10px', fontSize: '0.75rem', fontWeight: 700, whiteSpace: 'nowrap',
                  }}>
                    {ORIGIN_LABEL[r.origin ?? ''] ?? r.origin ?? '—'}
                  </span>
                </td>
                <td>{r.productName || <span className="muted">—</span>}</td>
                <td className="ta-right">{int(r.quantity ?? 0)}</td>
                <td className="ta-right">{money(r.normalPrice ?? 0)}</td>
                <td className="ta-right">{money(r.wastePrice ?? 0)}</td>
                <td className="ta-right">{money(r.difference ?? 0)}</td>
                <td>{r.saleFolio || (r.saleId != null ? `V-${r.saleId}` : r.shiftId != null ? `Turno ${r.shiftId}` : '—')}</td>
                <td>{r.cashier || '—'}</td>
              </tr>
            ))}
            {rows.length === 0 && <tr><td colSpan={10} className="ret-empty">Sin movimientos de merma en el periodo.</td></tr>}
          </tbody>
        </table>

        {rows.length > 0 && (
          <div className="pur-foot">
            <div />
            <div className="pur-totals">
              <div className="pur-total-row"><span>Piezas movidas a/por merma</span><span>{int(totalUnits)}</span></div>
              <div className="pur-total-row pur-total-grand"><span>Diferencia total</span><strong>{money(totalLoss)}</strong></div>
            </div>
          </div>
        )}
      </motion.div>
    </div>
  );
}
