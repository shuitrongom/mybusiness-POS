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

interface Payable {
  id: number; supplierId: number; supplierName: string; purchaseId: number | null;
  invoiceRef: string | null; amount: number; paid: number; balance: number;
  status: string; dueDate: string | null; currency: string;
}
interface Summary { totalPending: number; openCount: number; overdueCount: number; }

function isOverdue(p: Payable): boolean {
  return p.status === 'OPEN' && p.dueDate != null && new Date(p.dueDate) < new Date(new Date().toDateString());
}

/**
 * Cuentas por pagar a proveedores: cartera con KPIs, filtro por estado y abonos.
 */
export function PayablesPage() {
  const queryClient = useQueryClient();
  const [onlyOpen, setOnlyOpen] = useState(true);
  const [paying, setPaying] = useState<Payable | null>(null);

  const { data: summary } = useQuery({
    queryKey: ['payables-summary'],
    queryFn: async () => (await api.get<Summary>('/purchasing/payables/summary')).data,
  });
  const { data: payables = [], isLoading } = useQuery({
    queryKey: ['payables', onlyOpen],
    queryFn: async () => (await api.get<Payable[]>('/purchasing/payables', {
      params: { onlyWithBalance: onlyOpen },
    })).data,
  });

  const pay = useMutation({
    mutationFn: async ({ id, amount }: { id: number; amount: number }) =>
      api.post(`/purchasing/payables/${id}/pay`, { amount }),
    onSuccess: () => {
      setPaying(null);
      queryClient.invalidateQueries({ queryKey: ['payables'] });
      queryClient.invalidateQueries({ queryKey: ['payables-summary'] });
      toast.success('Abono registrado', 'Se actualizó el saldo del proveedor.');
    },
    onError: () => toast.error('No se pudo registrar el abono'),
  });

  const rows = useMemo(() => payables, [payables]);

  return (
    <div>
      <h1 className="page-title">Cuentas por pagar</h1>
      <p className="page-sub">Saldos con proveedores y abonos</p>

      <div className="coll-kpis">
        <motion.div className="coll-kpi coll-kpi-accent" variants={fadeInUp} initial="hidden" animate="visible">
          <span className="coll-kpi-icon"><Wallet size={20} /></span>
          <div><div className="coll-kpi-value">{money(summary?.totalPending ?? 0)}</div><div className="coll-kpi-label">Total por pagar</div></div>
        </motion.div>
        <motion.div className="coll-kpi" variants={fadeInUp} initial="hidden" animate="visible">
          <span className="coll-kpi-icon"><HandCoins size={20} /></span>
          <div><div className="coll-kpi-value">{summary?.openCount ?? 0}</div><div className="coll-kpi-label">Cuentas abiertas</div></div>
        </motion.div>
        <motion.div className="coll-kpi coll-kpi-danger" variants={fadeInUp} initial="hidden" animate="visible">
          <span className="coll-kpi-icon"><TriangleAlert size={20} /></span>
          <div><div className="coll-kpi-value">{summary?.overdueCount ?? 0}</div><div className="coll-kpi-label">Cuentas vencidas</div></div>
        </motion.div>
      </div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <div className="coll-tabs">
          <button className={`coll-tab ${onlyOpen ? 'is-active' : ''}`} onClick={() => setOnlyOpen(true)}>Con saldo</button>
          <button className={`coll-tab ${!onlyOpen ? 'is-active' : ''}`} onClick={() => setOnlyOpen(false)}>Todas</button>
        </div>
        <table className="ret-table">
          <thead>
            <tr>
              <th>Proveedor</th><th>Documento</th><th>Factura</th><th>Vence</th>
              <th className="ta-right">Importe</th><th className="ta-right">Abonado</th>
              <th className="ta-right">Saldo</th><th className="ta-right">Acción</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((p) => (
              <tr key={p.id} className={isOverdue(p) ? 'coll-row-overdue' : ''}>
                <td><strong>{p.supplierName}</strong></td>
                <td>{p.purchaseId ? `C-${p.purchaseId}` : '—'}</td>
                <td>{p.invoiceRef || <span className="muted">—</span>}</td>
                <td>{p.dueDate ? <span className={isOverdue(p) ? 'coll-due-over' : ''}><CalendarClock size={12} /> {p.dueDate}</span> : <span className="muted">—</span>}</td>
                <td className="ta-right">{money(p.amount)}</td>
                <td className="ta-right">{money(p.paid)}</td>
                <td className="ta-right"><strong>{money(p.balance)}</strong></td>
                <td className="ta-right">
                  {p.status === 'OPEN'
                    ? <button className="btn-accent btn-sm" onClick={() => setPaying(p)}>Abonar</button>
                    : <span className="badge badge-success">Pagada</span>}
                </td>
              </tr>
            ))}
            {rows.length === 0 && (
              <tr><td colSpan={8} className="ret-empty">{isLoading ? 'Cargando…' : 'No hay cuentas por pagar.'}</td></tr>
            )}
          </tbody>
        </table>
      </motion.div>

      <AnimatePresence>
        {paying && (
          <PayModal payable={paying} onClose={() => setPaying(null)}
            onPay={(amount) => pay.mutate({ id: paying.id, amount })} busy={pay.isPending} />
        )}
      </AnimatePresence>
    </div>
  );
}

function PayModal({ payable, onClose, onPay, busy }:
  { payable: Payable; onClose: () => void; onPay: (amount: number) => void; busy: boolean }) {
  const [amount, setAmount] = useState(String(payable.balance));
  const num = Number(amount) || 0;
  const invalid = num <= 0 || num > payable.balance;
  return (
    <motion.div className="pos-modal-overlay" onClick={onClose}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: 0.15 }}>
      <motion.div className="pos-modal pos-modal-sm" onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.97 }} transition={{ type: 'spring', stiffness: 260, damping: 22 }}>
        <div className="pos-modal-head">
          <h3>Abonar a {payable.supplierName}</h3>
          <button className="modal-close" onClick={onClose} aria-label="Cerrar">×</button>
        </div>
        <div className="pos-modal-total"><span>Saldo pendiente</span><strong>{money(payable.balance)}</strong></div>
        <label className="pos-received">
          <span>Monto del abono</span>
          <input type="number" min={0} max={payable.balance} step="0.01" value={amount}
            onChange={(e) => setAmount(e.target.value)} autoFocus />
        </label>
        {invalid && num > payable.balance && <p className="pos-credit-warn">El abono no puede superar el saldo.</p>}
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
