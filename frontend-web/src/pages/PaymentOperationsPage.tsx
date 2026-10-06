import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { Receipt } from 'lucide-react';
import { api } from '@/lib/api';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/returns.css';
import '@/pages/collections.css';
import '@/pages/inventory.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const today = () => new Date().toISOString().slice(0, 10);
const firstOfMonth = () => { const d = new Date(); return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10); };

interface Op {
  id: number; opType: string; carrier: string | null; category: string | null; reference: string;
  amount: number; commission: number; cost: number; status: string; folio: string | null;
  cashier: string | null; createdAt: string;
}

/**
 * Consultar operaciones realizadas: historial de recargas y pagos con filtros por tipo y fechas,
 * mostrando monto cobrado, comisión ganada y estado.
 */
export function PaymentOperationsPage() {
  const [type, setType] = useState<'ALL' | 'RECHARGE' | 'SERVICE'>('ALL');
  const [from, setFrom] = useState(firstOfMonth());
  const [to, setTo] = useState(today());

  const { data: ops = [], isLoading } = useQuery({
    queryKey: ['payments', 'operations', type, from, to],
    queryFn: async () => (await api.get<Op[]>('/payments/operations', {
      params: { ...(type === 'ALL' ? {} : { type }), from, to, limit: 300 },
    })).data,
  });

  const totals = ops.reduce((acc, o) => o.status === 'SUCCESS'
    ? { amount: acc.amount + o.amount, commission: acc.commission + o.commission, count: acc.count + 1 }
    : acc, { amount: 0, commission: 0, count: 0 });

  return (
    <div>
      <h1 className="page-title">Operaciones realizadas</h1>
      <p className="page-sub">Historial de recargas y pagos de servicio</p>

      <div className="coll-kpis">
        <motion.div className="coll-kpi coll-kpi-accent" variants={fadeInUp} initial="hidden" animate="visible">
          <span className="coll-kpi-icon"><Receipt size={20} /></span>
          <div><div className="coll-kpi-value">{totals.count}</div><div className="coll-kpi-label">Operaciones exitosas</div></div>
        </motion.div>
        <motion.div className="coll-kpi" variants={fadeInUp} initial="hidden" animate="visible">
          <span className="coll-kpi-icon"><Receipt size={20} /></span>
          <div><div className="coll-kpi-value">{money(totals.amount)}</div><div className="coll-kpi-label">Monto cobrado</div></div>
        </motion.div>
        <motion.div className="coll-kpi" variants={fadeInUp} initial="hidden" animate="visible">
          <span className="coll-kpi-icon"><Receipt size={20} /></span>
          <div><div className="coll-kpi-value">{money(totals.commission)}</div><div className="coll-kpi-label">Comisión ganada</div></div>
        </motion.div>
      </div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <div className="sec-title-row">
          <div className="coll-tabs">
            {(['ALL', 'RECHARGE', 'SERVICE'] as const).map((t) => (
              <button key={t} className={`coll-tab ${type === t ? 'is-active' : ''}`} onClick={() => setType(t)}>
                {t === 'ALL' ? 'Todas' : t === 'RECHARGE' ? 'Recargas' : 'Servicios'}
              </button>
            ))}
          </div>
          <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
            <input type="date" className="pur-in" style={{ width: 150 }} value={from} onChange={(e) => setFrom(e.target.value)} />
            <span className="muted">al</span>
            <input type="date" className="pur-in" style={{ width: 150 }} value={to} onChange={(e) => setTo(e.target.value)} />
          </div>
        </div>

        <table className="ret-table">
          <thead>
            <tr><th>Fecha</th><th>Tipo</th><th>Compañía / servicio</th><th>Referencia</th>
              <th className="ta-right">Monto</th><th className="ta-right">Comisión</th><th>Estado</th><th>Cajero</th></tr>
          </thead>
          <tbody>
            {ops.map((o) => (
              <tr key={o.id}>
                <td>{new Date(o.createdAt).toLocaleString('es-MX', { dateStyle: 'short', timeStyle: 'short' })}</td>
                <td>{o.opType === 'RECHARGE' ? 'Recarga' : 'Servicio'}</td>
                <td><strong>{o.carrier}</strong>{o.category && <div className="muted" style={{ fontSize: 12 }}>{o.category}</div>}</td>
                <td>{o.reference}</td>
                <td className="ta-right">{money(o.amount)}</td>
                <td className="ta-right">{money(o.commission)}</td>
                <td><span className={`badge ${o.status === 'SUCCESS' ? 'badge-success' : 'badge-danger'}`}>{o.status === 'SUCCESS' ? 'Exitosa' : 'Fallida'}</span></td>
                <td>{o.cashier || '—'}</td>
              </tr>
            ))}
            {ops.length === 0 && <tr><td colSpan={8} className="ret-empty">{isLoading ? 'Cargando…' : 'Sin operaciones en el rango.'}</td></tr>}
          </tbody>
        </table>
      </motion.div>
    </div>
  );
}
