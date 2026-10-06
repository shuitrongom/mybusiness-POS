import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { motion } from 'motion/react';
import { Wallet, BadgeDollarSign, Receipt, Landmark } from 'lucide-react';
import { api } from '@/lib/api';
import { fadeInUp } from '@/lib/motion';
import '@/pages/dashboard.css';
import '@/pages/returns.css';
import '@/pages/advances.css';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

interface Summary {
  balance: number; commissions: number; todayOps: number; todayAmount: number; pendingDeposits: number;
}
interface Op { id: number; opType: string; carrier: string | null; reference: string; amount: number; balanceAfter: number; createdAt: string; }

/**
 * Consultar saldo: muestra el saldo prepagado disponible (tarjeta grande), comisiones y los
 * últimos movimientos con el saldo resultante tras cada uno.
 */
export function PaymentBalancePage() {
  const navigate = useNavigate();

  const { data: s } = useQuery({
    queryKey: ['payments', 'balance'],
    queryFn: async () => (await api.get<Summary>('/payments/balance')).data,
  });
  const { data: ops = [] } = useQuery({
    queryKey: ['payments', 'operations', 'recent'],
    queryFn: async () => (await api.get<Op[]>('/payments/operations', { params: { limit: 20 } })).data,
  });

  return (
    <div>
      <h1 className="page-title">Consultar saldo</h1>
      <p className="page-sub">Saldo prepagado disponible y últimos movimientos</p>

      <motion.div className="card adv-balance-card" variants={fadeInUp} initial="hidden" animate="visible">
        <div className="adv-balance-info">
          <span className="adv-balance-icon"><Wallet size={26} /></span>
          <div>
            <div className="adv-balance-name">Saldo disponible</div>
            <div className="adv-balance-label">Para recargas y pago de servicios</div>
          </div>
        </div>
        <div className="adv-balance-amount">{money(s?.balance ?? 0)}</div>
      </motion.div>

      <div className="coll-kpis" style={{ marginTop: 'var(--space-4)' }}>
        <motion.div className="coll-kpi" variants={fadeInUp} initial="hidden" animate="visible">
          <span className="coll-kpi-icon"><BadgeDollarSign size={20} /></span>
          <div><div className="coll-kpi-value">{money(s?.commissions ?? 0)}</div><div className="coll-kpi-label">Comisiones ganadas</div></div>
        </motion.div>
        <motion.div className="coll-kpi" variants={fadeInUp} initial="hidden" animate="visible">
          <span className="coll-kpi-icon"><Receipt size={20} /></span>
          <div><div className="coll-kpi-value">{money(s?.todayAmount ?? 0)}</div><div className="coll-kpi-label">Vendido hoy</div></div>
        </motion.div>
        <motion.button className="coll-kpi coll-kpi-accent" onClick={() => navigate('/pay-deposit')}
          style={{ cursor: 'pointer', textAlign: 'left', border: 'none' }}
          variants={fadeInUp} initial="hidden" animate="visible">
          <span className="coll-kpi-icon"><Landmark size={20} /></span>
          <div><div className="coll-kpi-value">Reponer</div><div className="coll-kpi-label">Reportar un abono</div></div>
        </motion.button>
      </div>

      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Receipt size={18} /> Últimos movimientos</h3>
        <table className="ret-table">
          <thead><tr><th>Fecha</th><th>Operación</th><th>Referencia</th><th className="ta-right">Monto</th><th className="ta-right">Saldo</th></tr></thead>
          <tbody>
            {ops.map((o) => (
              <tr key={o.id}>
                <td>{new Date(o.createdAt).toLocaleString('es-MX', { dateStyle: 'short', timeStyle: 'short' })}</td>
                <td>{o.opType === 'RECHARGE' ? 'Recarga' : 'Servicio'}{o.carrier ? ` · ${o.carrier}` : ''}</td>
                <td>{o.reference}</td>
                <td className="ta-right">{money(o.amount)}</td>
                <td className="ta-right"><strong>{money(o.balanceAfter)}</strong></td>
              </tr>
            ))}
            {ops.length === 0 && <tr><td colSpan={5} className="ret-empty">Sin movimientos.</td></tr>}
          </tbody>
        </table>
      </motion.div>
    </div>
  );
}
