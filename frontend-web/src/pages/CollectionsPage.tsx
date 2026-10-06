import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AnimatePresence, motion } from 'motion/react';
import { HandCoins, TriangleAlert, Wallet, Check, CalendarClock } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/returns.css';
import '@/pages/customers.css';
import '@/pages/pos.css';
import '@/pages/collections.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

interface Receivable {
  id: number;
  customerId: number;
  customerName: string;
  saleId: number | null;
  amount: number;
  paid: number;
  balance: number;
  status: string;
  dueDate: string | null;
  createdAt: string;
}
interface Summary { totalPending: number; openCount: number; overdueCount: number; }

function isOverdue(r: Receivable): boolean {
  return r.status === 'OPEN' && r.dueDate != null && new Date(r.dueDate) < new Date(new Date().toDateString());
}

/**
 * Cobranza: cuentas por cobrar de clientes con su saldo pendiente y abonos. Muestra KPIs de
 * cartera (pendiente, cuentas abiertas, vencidas) y permite abonar a cada cuenta.
 */
export function CollectionsPage() {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<'OPEN' | 'PAID' | 'ALL'>('OPEN');
  const [paying, setPaying] = useState<Receivable | null>(null);

  const { data: summary } = useQuery({
    queryKey: ['receivables-summary'],
    queryFn: async () => (await api.get<Summary>('/customers/receivables/summary')).data,
  });
  const { data: receivables = [], isLoading } = useQuery({
    queryKey: ['receivables', statusFilter],
    queryFn: async () => (await api.get<Receivable[]>('/customers/receivables', {
      params: statusFilter === 'ALL' ? {} : { status: statusFilter },
    })).data,
  });

  const pay = useMutation({
    mutationFn: async ({ id, amount }: { id: number; amount: number }) =>
      api.post(`/customers/receivables/${id}/pay`, { amount }),
    onSuccess: () => {
      setPaying(null);
      queryClient.invalidateQueries({ queryKey: ['receivables'] });
      queryClient.invalidateQueries({ queryKey: ['receivables-summary'] });
      toast.success('Abono registrado', 'Se actualizó el saldo del cliente.');
    },
    onError: () => toast.error('No se pudo registrar el abono'),
  });

  const rows = useMemo(() => receivables, [receivables]);

  return (
    <div>
      <h1 className="page-title">Cobranza</h1>
      <p className="page-sub">Cuentas por cobrar de clientes a crédito y sus abonos</p>

      <div className="coll-kpis">
        <motion.div className="coll-kpi coll-kpi-accent" variants={fadeInUp} initial="hidden" animate="visible">
          <span className="coll-kpi-icon"><Wallet size={20} /></span>
          <div>
            <div className="coll-kpi-value">{money(summary?.totalPending ?? 0)}</div>
            <div className="coll-kpi-label">Total por cobrar</div>
          </div>
        </motion.div>
        <motion.div className="coll-kpi" variants={fadeInUp} initial="hidden" animate="visible">
          <span className="coll-kpi-icon"><HandCoins size={20} /></span>
          <div>
            <div className="coll-kpi-value">{summary?.openCount ?? 0}</div>
            <div className="coll-kpi-label">Cuentas abiertas</div>
          </div>
        </motion.div>
        <motion.div className="coll-kpi coll-kpi-danger" variants={fadeInUp} initial="hidden" animate="visible">
          <span className="coll-kpi-icon"><TriangleAlert size={20} /></span>
          <div>
            <div className="coll-kpi-value">{summary?.overdueCount ?? 0}</div>
            <div className="coll-kpi-label">Cuentas vencidas</div>
          </div>
        </motion.div>
      </div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }}
        variants={fadeInUp} initial="hidden" animate="visible">
        <div className="coll-tabs">
          {(['OPEN', 'PAID', 'ALL'] as const).map((s) => (
            <button key={s} className={`coll-tab ${statusFilter === s ? 'is-active' : ''}`}
              onClick={() => setStatusFilter(s)}>
              {s === 'OPEN' ? 'Pendientes' : s === 'PAID' ? 'Pagadas' : 'Todas'}
            </button>
          ))}
        </div>

        <table className="ret-table">
          <thead>
            <tr>
              <th>Cliente</th><th>Venta</th><th>Vence</th>
              <th className="ta-right">Total</th><th className="ta-right">Abonado</th>
              <th className="ta-right">Saldo</th><th className="ta-right">Acción</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.id} className={isOverdue(r) ? 'coll-row-overdue' : ''}>
                <td><strong>{r.customerName}</strong></td>
                <td>{r.saleId ? `V-${r.saleId}` : '—'}</td>
                <td>
                  {r.dueDate ? (
                    <span className={isOverdue(r) ? 'coll-due-over' : ''}>
                      <CalendarClock size={12} /> {r.dueDate}
                    </span>
                  ) : <span className="muted">—</span>}
                </td>
                <td className="ta-right">{money(r.amount)}</td>
                <td className="ta-right">{money(r.paid)}</td>
                <td className="ta-right"><strong>{money(r.balance)}</strong></td>
                <td className="ta-right">
                  {r.status === 'OPEN' ? (
                    <button className="btn-accent btn-sm" onClick={() => setPaying(r)}>Abonar</button>
                  ) : (
                    <span className="badge badge-success">Pagada</span>
                  )}
                </td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr><td colSpan={7} className="ret-empty">
                {isLoading ? 'Cargando…' : 'No hay cuentas por cobrar en este filtro.'}
              </td></tr>
            )}
          </tbody>
        </table>
      </motion.div>

      <AnimatePresence>
        {paying && (
          <PayModal receivable={paying} onClose={() => setPaying(null)}
            onPay={(amount) => pay.mutate({ id: paying.id, amount })} busy={pay.isPending} />
        )}
      </AnimatePresence>
    </div>
  );
}

/** Modal de abono a una cuenta por cobrar. */
function PayModal({ receivable, onClose, onPay, busy }:
  { receivable: Receivable; onClose: () => void; onPay: (amount: number) => void; busy: boolean }) {
  const [amount, setAmount] = useState(String(receivable.balance));
  const num = Number(amount) || 0;
  const invalid = num <= 0 || num > receivable.balance;

  return (
    <motion.div className="pos-modal-overlay" onClick={onClose}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
      <motion.div className="pos-modal pos-modal-sm" onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.97 }} transition={{ type: 'spring', stiffness: 260, damping: 22 }}>
        <div className="pos-modal-head">
          <h3>Abonar a {receivable.customerName}</h3>
          <button className="modal-close" onClick={onClose} aria-label="Cerrar">×</button>
        </div>
        <div className="pos-modal-total">
          <span>Saldo pendiente</span>
          <strong>{money(receivable.balance)}</strong>
        </div>
        <label className="pos-received">
          <span>Monto del abono</span>
          <input type="number" min={0} max={receivable.balance} step="0.01" value={amount}
            onChange={(e) => setAmount(e.target.value)} autoFocus />
        </label>
        {invalid && num > receivable.balance && (
          <p className="pos-credit-warn">El abono no puede superar el saldo pendiente.</p>
        )}
        <div className="pos-modal-actions">
          <button className="btn-ghost" onClick={onClose}>Cancelar</button>
          <button className="pos-confirm" disabled={invalid || busy} onClick={() => onPay(num)}>
            <Check size={16} /> {busy ? 'Registrando…' : 'Registrar abono'}
          </button>
        </div>
      </motion.div>
    </motion.div>
  );
}
