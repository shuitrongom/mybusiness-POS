import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { ListChecks, Check, X } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/returns.css';
import '@/pages/collections.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

interface Deposit {
  id: number; posId: string | null; name: string; bank: string; reference: string;
  amount: number; payDate: string | null; status: string; reviewedBy: string | null; createdAt: string;
}

const STATUS: Record<string, { label: string; cls: string }> = {
  PENDING: { label: 'Pendiente', cls: 'badge-warning' },
  APPROVED: { label: 'Aprobado', cls: 'badge-success' },
  REJECTED: { label: 'Rechazado', cls: 'badge-danger' },
};

/**
 * Consultar abonos: lista los depósitos reportados con su estado y permite aprobar (suma al
 * saldo) o rechazar los pendientes.
 */
export function PaymentDepositsPage() {
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'>('ALL');

  const { data: deposits = [], isLoading } = useQuery({
    queryKey: ['payments', 'deposits', filter],
    queryFn: async () => (await api.get<Deposit[]>('/payments/deposits', {
      params: filter === 'ALL' ? {} : { status: filter },
    })).data,
  });

  const approve = useMutation({
    mutationFn: async (id: number) => api.post(`/payments/deposits/${id}/approve`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payments'] });
      toast.success('Abono aprobado', 'Se sumó a tu saldo prepagado.');
    },
    onError: () => toast.error('No se pudo aprobar el abono'),
  });
  const reject = useMutation({
    mutationFn: async (id: number) => api.post(`/payments/deposits/${id}/reject`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payments', 'deposits'] });
      toast.success('Abono rechazado');
    },
    onError: () => toast.error('No se pudo rechazar el abono'),
  });

  return (
    <div>
      <h1 className="page-title">Consultar abonos</h1>
      <p className="page-sub">Depósitos reportados y su estado</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="coll-tabs">
          {(['ALL', 'PENDING', 'APPROVED', 'REJECTED'] as const).map((s) => (
            <button key={s} className={`coll-tab ${filter === s ? 'is-active' : ''}`} onClick={() => setFilter(s)}>
              {s === 'ALL' ? 'Todos' : STATUS[s].label}
            </button>
          ))}
        </div>

        <table className="ret-table">
          <thead>
            <tr><th>Fecha</th><th>Nombre</th><th>Banco</th><th>Referencia</th>
              <th className="ta-right">Importe</th><th>Estado</th><th className="ta-right">Acción</th></tr>
          </thead>
          <tbody>
            {deposits.map((d) => (
              <tr key={d.id}>
                <td>{d.payDate || '—'}</td>
                <td><strong>{d.name}</strong>{d.posId && <div className="muted" style={{ fontSize: 12 }}>POS {d.posId}</div>}</td>
                <td>{d.bank}</td>
                <td>{d.reference}</td>
                <td className="ta-right"><strong>{money(d.amount)}</strong></td>
                <td><span className={`badge ${STATUS[d.status]?.cls ?? 'badge-muted'}`}>{STATUS[d.status]?.label ?? d.status}</span></td>
                <td className="ta-right">
                  {d.status === 'PENDING' && (
                    <div style={{ display: 'inline-flex', gap: 6 }}>
                      <button className="btn-accent btn-sm" onClick={() => approve.mutate(d.id)}><Check size={13} /> Aprobar</button>
                      <button className="btn-ghost btn-sm" onClick={() => reject.mutate(d.id)}><X size={13} /> Rechazar</button>
                    </div>
                  )}
                  {d.status !== 'PENDING' && d.reviewedBy && <span className="muted">{d.reviewedBy}</span>}
                </td>
              </tr>
            ))}
            {deposits.length === 0 && (
              <tr><td colSpan={7} className="ret-empty">
                <ListChecks size={20} /> {isLoading ? 'Cargando…' : 'No hay abonos en este filtro.'}
              </td></tr>
            )}
          </tbody>
        </table>
      </motion.div>
    </div>
  );
}
