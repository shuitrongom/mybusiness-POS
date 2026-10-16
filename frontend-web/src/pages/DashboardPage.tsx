import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import {
  Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from 'recharts';
import {
  Receipt, TrendingUp, TrendingDown, CreditCard, Package, Trophy, ArrowUpRight, LineChart, Wallet,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { api } from '@/lib/api';
import { staggerContainer, staggerItem } from '@/lib/motion';
import { SalesTrendChart, PaymentMixChart, type TrendPoint, type PaymentMixDatum } from '@/components/BiCharts';
import { InsightsPanel, type Insight } from '@/components/InsightsPanel';
import './dashboard.css';

interface DashboardSummary {
  date: string;
  salesCount: number;
  salesTotal: number;
  averageTicket: number;
  itemsSold: number;
}

interface ProductRanking {
  productId: number;
  name: string;
  quantity: number;
  revenue: number;
}

interface PeriodDelta {
  current: number;
  previous: number;
  changePct: number | null;
}

interface PeriodComparison {
  today: PeriodDelta;
  week: PeriodDelta;
  month: PeriodDelta;
}

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);
const int = (n: number) => new Intl.NumberFormat('es-MX').format(n || 0);

function cssVar(name: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback;
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return v || fallback;
}

/**
 * Panel principal del dueño, nivel enterprise: una métrica HÉROE (total vendido), KPIs de apoyo,
 * serie de ventas, comparativa de periodos con variación %, mezcla por método de pago, ranking de
 * productos y el panel de "Análisis inteligente" (insights de IA). Consume el BI de negocio
 * (/api/v1/bi) protegido por el módulo 'bi' en el backend.
 */
export function DashboardPage() {
  const summary = useQuery({
    queryKey: ['dashboard', 'today'],
    queryFn: async () => (await api.get<DashboardSummary>('/bi/dashboard/today')).data,
  });
  const series = useQuery({
    queryKey: ['dashboard', 'series'],
    queryFn: async () => (await api.get<TrendPoint[]>('/bi/sales/series?days=30')).data,
  });
  const comparison = useQuery({
    queryKey: ['dashboard', 'comparison'],
    queryFn: async () => (await api.get<PeriodComparison>('/bi/comparison')).data,
  });
  const paymentMix = useQuery({
    queryKey: ['dashboard', 'payments-mix'],
    queryFn: async () => (await api.get<PaymentMixDatum[]>('/bi/payments/mix?days=30')).data,
  });
  const top = useQuery({
    queryKey: ['dashboard', 'top'],
    queryFn: async () => (await api.get<ProductRanking[]>('/bi/products/top?days=30&limit=5')).data,
  });
  const insights = useQuery({
    queryKey: ['dashboard', 'insights'],
    queryFn: async () => (await api.get<Insight[]>('/bi/insights')).data,
  });

  const s = summary.data;
  const topData = top.data ?? [];
  const maxRevenue = topData.reduce((m, p) => Math.max(m, p.revenue), 0);
  const cmp = comparison.data;

  return (
    <div>
      <div className="dash-head">
        <div>
          <h1 className="page-title">Panel</h1>
          <p className="page-sub">Resumen de tu negocio hoy</p>
        </div>
        <span className="dash-date">
          {new Date().toLocaleDateString('es-MX', { weekday: 'long', day: 'numeric', month: 'long' })}
        </span>
      </div>

      {/* Métrica héroe + KPIs de apoyo */}
      <motion.div className="dash-hero-grid" variants={staggerContainer} initial="hidden" animate="visible">
        <motion.div className="hero-metric" variants={staggerItem}>
          <div className="hero-metric-top">
            <span className="hero-metric-label">Total vendido hoy</span>
            <span className="hero-metric-badge"><ArrowUpRight size={14} /> En vivo</span>
          </div>
          <div className="hero-metric-value">{money(s?.salesTotal ?? 0)}</div>
          <div className="hero-metric-foot">
            {int(s?.salesCount ?? 0)} ventas · ticket promedio {money(s?.averageTicket ?? 0)}
          </div>
          <div className="hero-glow" />
        </motion.div>

        <div className="kpi-column">
          <Kpi icon={<Receipt size={18} />} tint="blue"
            label="Ventas del día" value={int(s?.salesCount ?? 0)} />
          <Kpi icon={<CreditCard size={18} />} tint="green"
            label="Ticket promedio" value={money(s?.averageTicket ?? 0)} />
          <Kpi icon={<Package size={18} />} tint="amber"
            label="Unidades vendidas" value={int(s?.itemsSold ?? 0)} />
          <Kpi icon={<TrendingUp size={18} />} tint="violet"
            label="Productos activos" value={int(topData.length ? topData.length : 0)} hint="en top 30 días" />
        </div>
      </motion.div>

      {/* Comparativa de periodos con variación % */}
      <h3 className="dash-section">Comparativa de periodos</h3>
      <motion.div className="metrics" style={{ marginTop: 'var(--space-3)' }}
        variants={staggerContainer} initial="hidden" animate="visible">
        <DeltaCard label="Hoy vs ayer" delta={cmp?.today} />
        <DeltaCard label="Esta semana vs anterior" delta={cmp?.week} />
        <DeltaCard label="Este mes vs anterior" delta={cmp?.month} />
      </motion.div>

      {/* Serie de ventas + mezcla por método de pago */}
      <motion.div className="dash-two-col" variants={staggerContainer} initial="hidden" animate="visible">
        <motion.div className="card" variants={staggerItem}>
          <div className="sec-title"><LineChart size={18} /><h3>Ventas (30 días)</h3></div>
          <p className="admin-plan-hint" style={{ marginTop: 4 }}>Ingreso diario del último mes</p>
          <SalesTrendChart data={series.data ?? []} />
        </motion.div>

        <motion.div className="card" variants={staggerItem}>
          <div className="sec-title"><Wallet size={18} /><h3>Mezcla por método de pago</h3></div>
          <p className="admin-plan-hint" style={{ marginTop: 4 }}>Monto cobrado por forma de pago (30 días)</p>
          <PaymentMixChart data={paymentMix.data ?? []} />
        </motion.div>
      </motion.div>

      <div className="dash-two-col">
        <motion.div className="card" variants={staggerItem} initial="hidden" animate="visible">
          <div className="sec-title"><Trophy size={18} /><h3>Más vendidos (30 días)</h3></div>
          <p className="admin-plan-hint" style={{ marginTop: 4 }}>Unidades vendidas por producto</p>
          <TopProductsChart data={topData} />
        </motion.div>

        <motion.div className="card" variants={staggerItem} initial="hidden" animate="visible">
          <div className="sec-title"><Package size={18} /><h3>Detalle por producto</h3></div>
          <p className="admin-plan-hint" style={{ marginTop: 4 }}>Ingreso generado y participación</p>
          <div className="rank-list">
            {topData.map((p, i) => (
              <div key={p.productId} className="rank-row">
                <span className={`rank-pos rank-pos-${i + 1}`}>{i + 1}</span>
                <div className="rank-main">
                  <div className="rank-line">
                    <span className="rank-name">{p.name}</span>
                    <span className="rank-rev">{money(p.revenue)}</span>
                  </div>
                  <div className="rank-bar-track">
                    <div className="rank-bar-fill"
                      style={{ width: `${maxRevenue ? (p.revenue / maxRevenue) * 100 : 0}%` }} />
                  </div>
                  <span className="rank-qty">{int(p.quantity)} unidades</span>
                </div>
              </div>
            ))}
            {topData.length === 0 && <p className="empty">Aún no hay ventas registradas.</p>}
          </div>
        </motion.div>
      </div>

      {/* Análisis inteligente (IA) */}
      <InsightsPanel insights={insights.data ?? []} />
    </div>
  );
}

function Kpi({ icon, label, value, tint, hint }: {
  icon: ReactNode; label: string; value: string; tint: string; hint?: string;
}) {
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

/** Tarjeta de comparativa de periodo: valor actual + variación % con flecha/color por signo. */
function DeltaCard({ label, delta }: { label: string; delta?: PeriodDelta }) {
  const pct = delta?.changePct ?? null;
  const up = pct !== null && pct >= 0;
  const toneClass = pct === null ? '' : up ? 'metric-tone-success' : 'metric-tone-danger';

  return (
    <motion.div className={`metric-card ${toneClass}`} variants={staggerItem}>
      <div className="metric-label">{label}</div>
      <div className="metric-value">{money(delta?.current ?? 0)}</div>
      <div className="metric-hint" style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        {pct === null ? (
          <span>Sin periodo anterior comparable</span>
        ) : (
          <>
            <span style={{
              display: 'inline-flex', alignItems: 'center', gap: 3, fontWeight: 700,
              color: up ? 'var(--success)' : 'var(--danger)',
            }}>
              {up ? <TrendingUp size={14} /> : <TrendingDown size={14} />}
              {Math.abs(pct).toFixed(1)}%
            </span>
            <span>vs {money(delta?.previous ?? 0)}</span>
          </>
        )}
      </div>
    </motion.div>
  );
}

function TopProductsChart({ data }: { data: ProductRanking[] }) {
  const brand = cssVar('--brand-600', '#2e6ef2');
  const brandLight = cssVar('--brand-500', '#3b82f6');
  const text = cssVar('--text-muted', '#64748b');

  if (data.length === 0) return <p className="chart-empty">Aún no hay ventas para graficar.</p>;

  return (
    <div className="chart-wrap">
      <ResponsiveContainer width="100%" height={260}>
        <BarChart data={data} layout="vertical" margin={{ top: 8, right: 16, left: 8, bottom: 4 }}>
          <XAxis type="number" allowDecimals={false} hide />
          <YAxis type="category" dataKey="name" width={130}
            tick={{ fill: text, fontSize: 12 }} axisLine={false} tickLine={false} />
          <Tooltip cursor={{ fill: 'rgba(46,110,242,0.08)' }}
            contentStyle={{
              background: cssVar('--surface', '#fff'),
              border: `1px solid ${cssVar('--border', '#e2e8f0')}`,
              borderRadius: 12, color: cssVar('--text', '#1f2937'), fontSize: 13,
            }}
            formatter={(value) => [`${Number(value)} u.`, 'Cantidad']} />
          <Bar dataKey="quantity" name="Cantidad" radius={[0, 8, 8, 0]} maxBarSize={34}>
            {data.map((d, i) => (
              <Cell key={d.productId} fill={i % 2 === 0 ? brand : brandLight} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
