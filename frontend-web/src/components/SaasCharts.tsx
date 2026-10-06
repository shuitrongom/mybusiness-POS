import {
  Bar,
  BarChart,
  Cell,
  Legend,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import './saas-charts.css';

/**
 * Gráficas premium del panel del SaaS (Recharts). Todas leen los colores del sistema de diseño
 * vía variables CSS resueltas en tiempo de ejecución, de modo que respetan el tema claro/oscuro.
 */

/** Lee el valor de una variable CSS del documento (para pasar colores a Recharts). */
function cssVar(name: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback;
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return v || fallback;
}

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN', maximumFractionDigits: 0 }).format(n || 0);

// ---------------------------------------------------------------------------
// Distribución de negocios por plan (barras)
// ---------------------------------------------------------------------------
export interface PlanDatum {
  plan: string;
  businesses: number;
}

export function PlanDistributionChart({ data }: { data: PlanDatum[] }) {
  const brand = cssVar('--brand-600', '#2e6ef2');
  const brandLight = cssVar('--brand-500', '#3b82f6');
  const grid = cssVar('--border', '#e2e8f0');
  const text = cssVar('--text-muted', '#64748b');

  if (data.length === 0) {
    return <p className="chart-empty">Aún no hay negocios por plan.</p>;
  }

  return (
    <div className="chart-wrap">
      <ResponsiveContainer width="100%" height={260}>
        <BarChart data={data} margin={{ top: 8, right: 8, left: -18, bottom: 4 }}>
          <defs>
            <linearGradient id="planBar" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={brandLight} />
              <stop offset="100%" stopColor={brand} />
            </linearGradient>
          </defs>
          <XAxis dataKey="plan" tick={{ fill: text, fontSize: 12 }} axisLine={{ stroke: grid }} tickLine={false} />
          <YAxis allowDecimals={false} tick={{ fill: text, fontSize: 12 }} axisLine={false} tickLine={false} />
          <Tooltip
            cursor={{ fill: 'rgba(46,110,242,0.08)' }}
            contentStyle={tooltipStyle()}
            labelStyle={{ color: cssVar('--text', '#1f2937'), fontWeight: 700 }}
          />
          <Bar dataKey="businesses" name="Negocios" fill="url(#planBar)" radius={[8, 8, 0, 0]} maxBarSize={64} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Cartera de negocios por estado (dona)
// ---------------------------------------------------------------------------
export interface PortfolioDatum {
  name: string;
  value: number;
  tone: 'trial' | 'active' | 'suspended' | 'expired';
}

export function PortfolioDonut({ data }: { data: PortfolioDatum[] }) {
  const toneColor: Record<PortfolioDatum['tone'], string> = {
    trial: cssVar('--warning', '#d97706'),
    active: cssVar('--success', '#16a34a'),
    suspended: cssVar('--text-muted', '#64748b'),
    expired: cssVar('--danger', '#dc2626'),
  };
  const nonZero = data.filter((d) => d.value > 0);

  if (nonZero.length === 0) {
    return <p className="chart-empty">Aún no hay negocios en la cartera.</p>;
  }

  return (
    <div className="chart-wrap">
      <ResponsiveContainer width="100%" height={260}>
        <PieChart>
          <Pie
            data={nonZero}
            dataKey="value"
            nameKey="name"
            innerRadius={62}
            outerRadius={92}
            paddingAngle={3}
            stroke="none"
          >
            {nonZero.map((d) => <Cell key={d.tone} fill={toneColor[d.tone]} />)}
          </Pie>
          <Tooltip contentStyle={tooltipStyle()} />
          <Legend
            verticalAlign="bottom"
            iconType="circle"
            wrapperStyle={{ fontSize: 12, color: cssVar('--text-muted', '#64748b') }}
          />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Ingresos por tipo (licencias vs módulos) — barras horizontales
// ---------------------------------------------------------------------------
export interface RevenueDatum {
  concept: string;
  amount: number;
}

export function RevenueByTypeChart({ data }: { data: RevenueDatum[] }) {
  const accent = cssVar('--accent-600', '#0ca678');
  const text = cssVar('--text-muted', '#64748b');
  const total = data.reduce((sum, d) => sum + d.amount, 0);

  if (total === 0) {
    return <p className="chart-empty">Aún no hay ingresos registrados.</p>;
  }

  return (
    <div className="chart-wrap">
      <ResponsiveContainer width="100%" height={200}>
        <BarChart data={data} layout="vertical" margin={{ top: 8, right: 16, left: 8, bottom: 4 }}>
          <XAxis type="number" hide />
          <YAxis
            type="category"
            dataKey="concept"
            width={120}
            tick={{ fill: text, fontSize: 12 }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip
            cursor={{ fill: 'rgba(12,166,120,0.08)' }}
            contentStyle={tooltipStyle()}
            formatter={(value) => [money(Number(value)), 'Ingreso']}
          />
          <Bar dataKey="amount" name="Ingreso" fill={accent} radius={[0, 8, 8, 0]} maxBarSize={40} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Estilo del tooltip alineado a los tokens del tema. */
function tooltipStyle(): React.CSSProperties {
  return {
    background: cssVar('--surface', '#ffffff'),
    border: `1px solid ${cssVar('--border', '#e2e8f0')}`,
    borderRadius: 12,
    boxShadow: '0 12px 32px rgba(0,0,0,0.18)',
    color: cssVar('--text', '#1f2937'),
    fontSize: 13,
  };
}
