import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import {
  Wallet, Banknote, CreditCard, Landmark, Ticket as TicketIcon, Receipt,
  TrendingUp, Package, ArrowDownCircle, ArrowUpCircle, Clock, LockKeyhole,
  CheckCircle2, Scale, FlaskConical,
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useState, type ReactNode } from 'react';
import { api } from '@/lib/api';
import { InfoBanner } from '@/components/InfoBanner';
import { ScopeSelector, type ReportScope } from '@/components/ScopeSelector';
import { useTestMode } from '@/lib/testMode';
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
  // Campos del resumen del día (my-day): si el turno ya cerró hoy, se muestra en solo lectura.
  closed?: boolean;
  closedAt?: string | null;
  countedCash?: number | null;
  difference?: number | null;
}

const money = (n: number) => new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const int = (n: number) => new Intl.NumberFormat('es-MX').format(Math.round(n || 0));
const timeOf = (s: string) => new Date(s).toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' });

/**
 * Resumen del CAJERO sobre su ÚLTIMO turno del día (endpoint {@code /shifts/my-day}). Si el turno
 * está ABIERTO, es su corte EN VIVO (se refresca solo). Si ya CERRÓ hoy (relevo o fin de día),
 * muestra EXACTAMENTE el mismo resumen pero en SOLO LECTURA, más su corte final (efectivo esperado
 * vs contado y diferencia): puramente informativo, sin ocultar nada. La cajera que ya cerró no
 * puede vender ni abrir caja de nuevo hoy, pero sí ver cómo le fue.
 */
export function CashierDashboardPage() {
  const navigate = useNavigate();
  const { active: testModeActive } = useTestMode();
  // Selector de scope (modo prueba): si el usuario no ha elegido, el default es 'both' cuando el
  // modo prueba está ON y 'real' cuando está OFF.
  const [scopePick, setScopePick] = useState<ReportScope | null>(null);
  const scope: ReportScope = scopePick ?? (testModeActive ? 'both' : 'real');
  const summary = useQuery({
    queryKey: ['cashier', 'my-day', scope],
    queryFn: async () => {
      const res = await api.get<ShiftSummary>('/shifts/my-day', { params: { scope } });
      return res.status === 204 || !res.data ? null : res.data;
    },
    // Solo refresca en vivo mientras el turno siga abierto; tras cerrar es un resumen estático.
    refetchInterval: (query) => (query.state.data?.closed ? false : 20000),
  });

  const s = summary.data;
  const closed = s?.closed === true;

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
          <h1 className="page-title">{closed ? 'Resumen de tu día' : 'Mi caja de hoy'}</h1>
          <p className="page-sub">
            {s.registerName ?? 'Caja'} · abierta {timeOf(s.openedAt)}
            {closed && s.closedAt ? ` · cerrada ${timeOf(s.closedAt)}` : ''}
            {' · fondo inicial '}{money(s.openingFloat)}
          </p>
        </div>
        <span className="dash-date">
          {new Date().toLocaleDateString('es-MX', { weekday: 'long', day: 'numeric', month: 'long' })}
        </span>
      </div>

      {testModeActive && (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <InfoBanner icon={<FlaskConical size={18} />} title="MODO PRUEBA" tone="amber">
            Estas ventas se registran como práctica, no afectan tu histórico real.
          </InfoBanner>
        </div>
      )}

      {/* Selector informativo de histórico: real / prueba / ambos. */}
      <div className="dash-scope-row" style={{ marginBottom: 'var(--space-4)' }}>
        <ScopeSelector value={scope} onChange={setScopePick} />
      </div>

      {closed ? (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <InfoBanner icon={<CheckCircle2 size={18} />} title="Tu turno de hoy ya cerró" tone="sky">
            Este es tu resumen del día. Es solo informativo: puedes verlo completo, pero ya no podrás
            vender ni abrir caja de nuevo hasta el día siguiente.
          </InfoBanner>
        </div>
      ) : (
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <InfoBanner icon={<TrendingUp size={18} />} title="Tu corte en vivo" tone="brand">
            Así va tu turno ahora mismo. Se actualiza solo mientras tu caja esté abierta; al cerrar
            verás tu corte final con el efectivo esperado contra lo contado.
          </InfoBanner>
        </div>
      )}

      {/* Héroe: total vendido en el turno + KPIs */}
      <motion.div className="dash-hero-grid" variants={staggerContainer} initial="hidden" animate="visible">
        <motion.div className="hero-metric" variants={staggerItem}>
          <div className="hero-metric-top">
            <span className="hero-metric-label">{closed ? 'Vendido en tu día' : 'Vendido en tu turno'}</span>
            {!closed && <span className="hero-metric-badge"><TrendingUp size={14} /> En vivo</span>}
          </div>
          <div className="hero-metric-value num">{money(s.totalSales)}</div>
          <div className="hero-metric-foot num">
            {int(s.salesCount)} ventas · ticket promedio {money(s.averageTicket)} · {int(s.units)} unidades
          </div>
          <div className="hero-glow" />
        </motion.div>

        <div className="kpi-column">
          <Kpi icon={<Banknote size={18} />} tint="green" label="Efectivo esperado en caja" value={money(s.expectedCash)} hint="fondo + efectivo − retiros" />
          <Kpi icon={<Receipt size={18} />} tint="blue" label={closed ? 'Ventas del día' : 'Ventas del turno'} value={int(s.salesCount)} />
          <Kpi icon={<Package size={18} />} tint="amber" label="Unidades vendidas" value={int(s.units)} />
          <Kpi icon={<CreditCard size={18} />} tint="violet" label="Ticket promedio" value={money(s.averageTicket)} />
        </div>
      </motion.div>

      {closed && (
        <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={staggerItem} initial="hidden" animate="visible">
          <div className="sec-title"><Scale size={18} /><h3>Tu corte final</h3></div>
          <div className="cash-cut-grid">
            <Kpi icon={<Banknote size={18} />} tint="green" label="Efectivo esperado" value={money(s.expectedCash)} />
            <Kpi icon={<Wallet size={18} />} tint="blue" label="Efectivo contado" value={money(s.countedCash ?? 0)} />
            <Kpi
              icon={<Scale size={18} />}
              tint={(s.difference ?? 0) === 0 ? 'green' : 'amber'}
              label="Diferencia"
              value={money(s.difference ?? 0)}
              hint={(s.difference ?? 0) === 0 ? 'cuadró exacto' : ((s.difference ?? 0) > 0 ? 'sobrante' : 'faltante')}
            />
          </div>
        </motion.div>
      )}

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
            <div className="cash-move"><span><ArrowDownCircle size={15} /> Entradas de efectivo</span><strong className="num">{money(s.cashIn)}</strong></div>
            <div className="cash-move"><span><ArrowUpCircle size={15} /> Salidas / retiros</span><strong className="num">-{money(s.cashOut)}</strong></div>
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
                    <span className="rank-rev num">{money(p.revenue)}</span>
                  </div>
                  <span className="rank-qty num">{int(p.quantity)} unidades</span>
                </div>
              </div>
            ))}
            {s.topProducts.length === 0 && (
              <EmptyState
                icon={<Package size={28} />}
                title="Aún no hay ventas"
                hint="Tus productos más vendidos del turno aparecerán aquí conforme cobres."
              />
            )}
          </div>
        </motion.div>
      </div>

      {/* Últimas ventas del turno */}
      <motion.div className="card" style={{ marginTop: 'var(--space-4)' }} variants={staggerItem} initial="hidden" animate="visible">
        <div className="sec-title"><Receipt size={18} /><h3>Últimas ventas</h3></div>
        <div className="dash-sales">
          {s.lastSales.length > 0 && (
            <div className="dash-sales-head">
              <span>Folio</span><span>Hora</span><span className="ta-right">Importe</span>
            </div>
          )}
          {s.lastSales.map((v) => (
            <div className="dash-sale-row" key={v.id}>
              <span className="dash-sale-folio">{v.folio ?? `V-${v.id}`}</span>
              <span className="dash-sale-time num">{timeOf(v.createdAt)}</span>
              <span className="dash-sale-amt num">{money(v.total)}</span>
            </div>
          ))}
          {s.lastSales.length === 0 && (
            <EmptyState
              icon={<Receipt size={28} />}
              title="Sin ventas todavía"
              hint="Cuando cobres tu primer ticket del turno aparecerá aquí."
            />
          )}
        </div>
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
        <span className="kpi-value num">{value}</span>
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
      <strong className="cash-method-value num">{money(value)}</strong>
    </div>
  );
}

/**
 * Estado vacío premium reutilizable del dashboard: icono con acento + mensaje que GUÍA la acción
 * siguiente, dentro de un recuadro con borde punteado (coherente con el steering diseno-premium).
 */
function EmptyState({ icon, title, hint }: { icon: ReactNode; title: string; hint: string }) {
  return (
    <div className="dash-empty">
      <span className="dash-empty-icon">{icon}</span>
      <strong>{title}</strong>
      <p>{hint}</p>
    </div>
  );
}
