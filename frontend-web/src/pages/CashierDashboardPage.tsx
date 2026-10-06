import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import {
  Wallet, Banknote, CreditCard, Landmark, Ticket as TicketIcon, Receipt,
  TrendingUp, Package, ArrowDownCircle, ArrowUpCircle, Clock, LockKeyhole,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import type { ReactNode } from 'react';
import { api } from '@/lib/api';
import { staggerContainer, staggerItem } from '@/lib/motion';
import './dashboard.css';

interface ShiftSummary {
  shiftId: number;
  registerName: string | null;
  openedAt: string;
  businessDate: string;
  openingFloat: number;
  cashSales: number; cardSales: number; transferSales: number; voucherSales: number;
  creditSales: number; cashIn: number; cashOut: number;
  expectedCash: number; totalSales: number; salesCount: number; units: number; averageTicket: number;
  lastSales: { id: number; folio: string | null; total: number; createdAt: string }[];
  topProducts: { name: string; quantity: number; revenue: number }[];
}

const money = (n: number) => new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const int = (n: number) => new Intl.NumberFormat('es-MX').format(Math.round(n || 0));
const timeOf = (s: string) => new Date(s).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' });

/**
 * Resumen del CAJERO: su corte del día EN VIVO sobre la caja abierta. Muestra el total vendido
 * hoy en su turno, el desglose por método de pago, el efectivo esperado en caja, entradas/salidas,
 * las últimas ventas y sus productos más vendidos del turno. Se refresca solo.
 */
export function CashierDashboardPage() {
  const navigate = useNavigate();
  const summary = useQuery({
    queryKey: ['cashier', 'my-summary'],
    queryFn: async () => {
      const res = await api.get<ShiftSummary>('/shifts/my-summary');
      return res.status === 204 || !res.data ? null : res.data;
    },
    refetchInterval: 20000, // refresco en vivo cada 20s
  });

  const s = summary.data;

  if (summary.isLoading) {
    return <div className="dash-head"><div><h1 className="page-title">Mi caja</h1><p className="page-sub">Cargando tu resumen…</p></div></div>;
  }

  if (!s) {
    return (
      <div>
        <div className="dash-head"><div><h1 className="page-title">Mi caja</h1><p className="page-sub">Resumen de tu turno de hoy</p></div></div>
        <motion.div className="card cash-dash-empty" variants={staggerItem} initial="hidden" animate="visible">
          <LockKeyhole size={40} strokeWidth={1.5} />
          <h3>No tienes una caja abierta</h3>
          <p>Abre tu caja con tu fondo inicial para empezar a vender y ver tu corte del día en vivo.</p>
          <button className="btn-accent" onClick={() => navigate('/pos')}><Wallet size={16} /> Ir al punto de venta</button>
        </motion.div>
      </div>
    );
  }

  return (
    <div>
      <div className="dash-head">
        <div>
          <h1 className="page-title">Mi caja de hoy</h1>
          <p className="page-sub">
            {s.registerName ?? 'Caja'} · abierta {timeOf(s.openedAt)} · fondo inicial {money(s.openingFloat)}
          </p>
        </div>
        <span className="dash-date">
          {new Date().toLocaleDateString('es-MX', { weekday: 'long', day: 'numeric', month: 'long' })}
        </span>
      </div>

      {/* Héroe: total vendido en el turno + KPIs */}
      <motion.div className="dash-hero-grid" variants={staggerContainer} initial="hidden" animate="visible">
        <motion.div className="hero-metric" variants={staggerItem}>
          <div className="hero-metric-top">
            <span className="hero-metric-label">Vendido en tu turno</span>
            <span className="hero-metric-badge"><TrendingUp size={14} /> En vivo</span>
          </div>
          <div className="hero-metric-value">{money(s.totalSales)}</div>
          <div className="hero-metric-foot">
            {int(s.salesCount)} ventas · ticket promedio {money(s.averageTicket)} · {int(s.units)} unidades
          </div>
          <div className="hero-glow" />
        </motion.div>

        <div className="kpi-column">
          <Kpi icon={<Banknote size={18} />} tint="green" label="Efectivo esperado en caja" value={money(s.expectedCash)} hint="fondo + efectivo − retiros" />
          <Kpi icon={<Receipt size={18} />} tint="blue" label="Ventas del turno" value={int(s.salesCount)} />
          <Kpi icon={<Package size={18} />} tint="amber" label="Unidades vendidas" value={int(s.units)} />
          <Kpi icon={<CreditCard size={18} />} tint="violet" label="Ticket promedio" value={money(s.averageTicket)} />
        </div>
      </motion.div>

      {/* Desglose por método + movimientos de efectivo */}
      <div className="dash-two-col">
        <motion.div className="card" variants={staggerItem} initial="hidden" animate="visible">
          <div className="sec-title"><Wallet size={18} /><h3>Cómo has cobrado hoy</h3></div>
          <div className="cash-methods">
            <MethodRow icon={<Banknote size={16} />} label="Efectivo" value={s.cashSales} tint="green" />
            <MethodRow icon={<CreditCard size={16} />} label="Tarjeta" value={s.cardSales} tint="blue" />
            <MethodRow icon={<Landmark size={16} />} label="Transferencia" value={s.transferSales} tint="violet" />
            <MethodRow icon={<TicketIcon size={16} />} label="Vale" value={s.voucherSales} tint="amber" />
            {s.creditSales > 0 && <MethodRow icon={<Clock size={16} />} label="A crédito" value={s.creditSales} tint="amber" />}
          </div>
          <div className="cash-moves">
            <div className="cash-move"><span><ArrowDownCircle size={15} /> Entradas de efectivo</span><strong>{money(s.cashIn)}</strong></div>
            <div className="cash-move"><span><ArrowUpCircle size={15} /> Salidas / retiros</span><strong>-{money(s.cashOut)}</strong></div>
          </div>
        </motion.div>

        <motion.div className="card" variants={staggerItem} initial="hidden" animate="visible">
          <div className="sec-title"><Package size={18} /><h3>Tus más vendidos hoy</h3></div>
          <div className="rank-list">
            {s.topProducts.map((p, i) => (
              <div key={p.name} className="rank-row">
                <span className={`rank-pos rank-pos-${i + 1}`}>{i + 1}</span>
                <div className="rank-main">
                  <div className="rank-line">
                    <span className="rank-name">{p.name}</span>
                    <span className="rank-rev">{money(p.revenue)}</span>
                  </div>
                  <span className="rank-qty">{int(p.quantity)} unidades</span>
                </div>
              </div>
            ))}
            {s.topProducts.length === 0 && <p className="empty">Aún no has vendido nada en este turno.</p>}
          </div>
        </motion.div>
      </div>

      {/* Últimas ventas del turno */}
      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={staggerItem} initial="hidden" animate="visible">
        <div className="sec-title"><Receipt size={18} /><h3>Últimas ventas</h3></div>
        <table className="table">
          <thead><tr><th>Folio</th><th>Hora</th><th className="ta-right">Importe</th></tr></thead>
          <tbody>
            {s.lastSales.map((v) => (
              <tr key={v.id}>
                <td><strong>{v.folio ?? `V-${v.id}`}</strong></td>
                <td>{timeOf(v.createdAt)}</td>
                <td className="ta-right">{money(v.total)}</td>
              </tr>
            ))}
            {s.lastSales.length === 0 && <tr><td colSpan={3} className="empty">Sin ventas todavía.</td></tr>}
          </tbody>
        </table>
      </motion.div>
    </div>
  );
}

function Kpi({ icon, label, value, tint, hint }: { icon: ReactNode; label: string; value: string; tint: string; hint?: string }) {
  return (
    <motion.div className="kpi-card" variants={staggerItem}>
      <span className={`kpi-icon kpi-${tint}`}>{icon}</span>
      <div className="kpi-body">
        <span className="kpi-label">{label}</span>
        <span className="kpi-value">{value}</span>
        {hint && <span className="kpi-hint">{hint}</span>}
      </div>
    </motion.div>
  );
}

function MethodRow({ icon, label, value, tint }: { icon: ReactNode; label: string; value: number; tint: string }) {
  return (
    <div className="cash-method">
      <span className={`kpi-icon kpi-${tint}`}>{icon}</span>
      <span className="cash-method-label">{label}</span>
      <strong className="cash-method-value">{money(value)}</strong>
    </div>
  );
}
