import { useEffect, useState } from 'react';
import { motion } from 'motion/react';
import { CalendarClock, Plus, Check, CalendarDays } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/returns.css';
import '@/pages/customers.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const today = () => new Date().toISOString().slice(0, 10);

interface Supplier { id: number; name: string; }
interface Visit {
  id: number; supplierName: string; visitDate: string; visitTime: string | null;
  estimatedAmount: number; purchaseAmount: number; visited: boolean; notes: string | null;
}

/**
 * Visita de proveedores: agenda las visitas (rol/periodicidad) y registra el resultado (monto
 * comprado). Muestra la agenda por rango de fechas.
 */
export function SupplierVisitsPage() {
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [visits, setVisits] = useState<Visit[]>([]);
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(today());
  const [form, setForm] = useState({ supplierId: '', date: today(), time: '', estimatedAmount: '', notes: '' });

  const load = () => {
    api.get<Visit[]>('/purchasing/visits', { params: { from, to } })
      .then((r) => setVisits(r.data)).catch(() => setVisits([]));
  };
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [from, to]);
  useEffect(() => {
    api.get<Supplier[]>('/purchasing/suppliers').then((r) => setSuppliers(r.data)).catch(() => {});
  }, []);

  const schedule = async () => {
    if (!form.supplierId) { toast.info('Elige un proveedor'); return; }
    try {
      await api.post('/purchasing/visits', {
        supplierId: Number(form.supplierId), date: form.date, time: form.time || null,
        estimatedAmount: form.estimatedAmount ? Number(form.estimatedAmount) : 0, notes: form.notes || null,
      });
      toast.success('Visita agendada');
      setForm({ ...form, time: '', estimatedAmount: '', notes: '' });
      load();
    } catch { toast.error('No se pudo agendar la visita'); }
  };

  const markVisited = async (id: number) => {
    const amount = prompt('Monto comprado en la visita (opcional):', '0');
    if (amount === null) return;
    try {
      await api.post(`/purchasing/visits/${id}/visited`, { purchaseAmount: Number(amount) || 0 });
      toast.success('Visita marcada como realizada');
      load();
    } catch { toast.error('No se pudo actualizar la visita'); }
  };

  return (
    <div>
      <h1 className="page-title">Visita de proveedores</h1>
      <p className="page-sub">Rol y agenda de visitas de tus proveedores</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Plus size={18} /> Agendar visita</h3>
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr 1fr auto', gap: 'var(--space-3)', marginTop: 'var(--space-3)', alignItems: 'end' }}>
          <label className="field"><span>Proveedor</span>
            <select value={form.supplierId} onChange={(e) => setForm({ ...form, supplierId: e.target.value })}>
              <option value="">Selecciona…</option>
              {suppliers.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
            </select></label>
          <label className="field"><span>Fecha</span>
            <input type="date" value={form.date} onChange={(e) => setForm({ ...form, date: e.target.value })} /></label>
          <label className="field"><span>Hora</span>
            <input type="time" value={form.time} onChange={(e) => setForm({ ...form, time: e.target.value })} /></label>
          <label className="field"><span>Monto estimado</span>
            <input type="number" step="0.01" value={form.estimatedAmount} onChange={(e) => setForm({ ...form, estimatedAmount: e.target.value })} /></label>
          <button className="btn-accent" onClick={schedule}>Agendar</button>
        </div>
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <div className="sec-title-row">
          <h3 className="sec-title"><CalendarDays size={18} /> Agenda</h3>
          <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
            <input type="date" className="pur-in" style={{ width: 150 }} value={from} onChange={(e) => setFrom(e.target.value)} />
            <span className="muted">al</span>
            <input type="date" className="pur-in" style={{ width: 150 }} value={to} onChange={(e) => setTo(e.target.value)} />
          </div>
        </div>
        <table className="ret-table">
          <thead>
            <tr><th>Proveedor</th><th>Fecha</th><th>Hora</th><th className="ta-right">Estimado</th><th className="ta-right">Comprado</th><th>Estado</th><th className="ta-right">Acción</th></tr>
          </thead>
          <tbody>
            {visits.map((v) => (
              <tr key={v.id}>
                <td><strong>{v.supplierName}</strong></td>
                <td>{v.visitDate}</td>
                <td>{v.visitTime || '—'}</td>
                <td className="ta-right">{money(v.estimatedAmount)}</td>
                <td className="ta-right">{money(v.purchaseAmount)}</td>
                <td>{v.visited ? <span className="badge badge-success">Visitado</span> : <span className="badge badge-warning">Pendiente</span>}</td>
                <td className="ta-right">
                  {!v.visited && <button className="btn-accent btn-sm" onClick={() => markVisited(v.id)}><Check size={14} /> Marcar</button>}
                </td>
              </tr>
            ))}
            {visits.length === 0 && <tr><td colSpan={7} className="ret-empty"><CalendarClock size={18} /> Sin visitas en el rango.</td></tr>}
          </tbody>
        </table>
      </motion.div>
    </div>
  );
}
