import {
  Area,
  AreaChart,
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import './saas-charts.css';

/**
 * Gráficas premium del BI (Recharts) reutilizables por el panel del dueño y el del Super Admin.
 * Siguen el mismo patrón que SaasCharts.tsx: colores leídos del sistema de diseño vía variables
 * CSS en tiempo de ejecución, ResponsiveContainer y estado vacío con la clase .chart-empty.
 */

/** Lee el valor de una variable CSS del documento (para pasar colores a Recharts). */
function cssVar(name: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback;
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return v || fallback;
}

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN', maximumFractionDigits: 0 }).format(n || 0);
const int = (n: number) => new Intl.NumberFormat('es-MX').format(n || 0);

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

// ---------------------------------------------------------------------------
// Serie temporal de ventas (área): ingreso por día/mes
// ---------------------------------------------------------------------------
export interface TrendPoint {
  date: string;
  tickets: number;
  revenue: number;
}

export function SalesTrendChart({ data, emptyLabel }: { data: TrendPoint[]; emptyLabel?: string }) {
  const brand = cssVar('--brand-600', '#2e6ef2');
  const brandLight = cssVar('--brand-500', '#3b82f6');
  const grid = cssVar('--border', '#e2e8f0');
  const text = cssVar('--text-muted', '#64748b');

  if (data.length === 0) {
    return <p className="chart-empty">{emptyLabel ?? 'Aún no hay ventas para graficar.'}</p>;
  }

  return (
    <div className="chart-wrap">
      <ResponsiveContainer width="100%" height={260}>
        <AreaChart data={data} margin={{ top: 8, right: 8, left: -16, bottom: 4 }}>
          <defs>
            <linearGradient id="salesTrendFill" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={brandLight} stopOpacity={0.4} />
              <stop offset="100%" stopColor={brand} stopOpacity={0.02} />
            </linearGradient>
          </defs>
          <CartesianGrid stroke={grid} strokeDasharray="3 3" vertical={false} />
          <XAxis
            dataKey="date"
            tick={{ fill: text, fontSize: 11 }}
            axisLine={{ stroke: grid }}
            tickLine={false}
            minTickGap={24}
          />
          <YAxis
            tick={{ fill: text, fontSize: 11 }}
            axisLine={false}
            tickLine={false}
            tickFormatter={(v) => money(Number(v))}
            width={64}
          />
          <Tooltip
            contentStyle={tooltipStyle()}
            labelStyle={{ color: cssVar('--text', '#1f2937'), fontWeight: 700 }}
            formatter={(value, name) =>
              name === 'revenue'
                ? [money(Number(value)), 'Ingreso']
                : [int(Number(value)), 'Tickets']}
          />
          <Area
            type="monotone"
            dataKey="revenue"
            name="revenue"
            stroke={brand}
            strokeWidth={2.5}
            fill="url(#salesTrendFill)"
            dot={false}
            activeDot={{ r: 4 }}
          />
        </AreaChart>
      </ResponsiveContainer>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Mezcla por método de pago (barras)
// ---------------------------------------------------------------------------
export interface PaymentMixDatum {
  method: string;
  operations: number;
  amount: number;
}

const METHOD_LABEL: Record<string, string> = {
  CASH: 'Efectivo',
  CARD: 'Tarjeta',
  TRANSFER: 'Transferencia',
  VOUCHER: 'Vale',
};

export function PaymentMixChart({ data }: { data: PaymentMixDatum[] }) {
  const grid = cssVar('--border', '#e2e8f0');
  const text = cssVar('--text-muted', '#64748b');
  const palette = [
    cssVar('--brand-600', '#2e6ef2'),
    cssVar('--accent-600', '#0ca678'),
    cssVar('--warning', '#d97706'),
    cssVar('--brand-500', '#3b82f6'),
  ];

  const total = data.reduce((sum, d) => sum + d.amount, 0);
  if (total === 0) {
    return <p className="chart-empty">Aún no hay cobros por método de pago.</p>;
  }

  const chartData = data.map((d) => ({ ...d, label: METHOD_LABEL[d.method] ?? d.method }));

  return (
    <div className="chart-wrap">
      <ResponsiveContainer width="100%" height={260}>
        <BarChart data={chartData} margin={{ top: 8, right: 8, left: -16, bottom: 4 }}>
          <CartesianGrid stroke={grid} strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="label" tick={{ fill: text, fontSize: 12 }} axisLine={{ stroke: grid }} tickLine={false} />
          <YAxis
            tick={{ fill: text, fontSize: 11 }}
            axisLine={false}
            tickLine={false}
            tickFormatter={(v) => money(Number(v))}
            width={64}
          />
          <Tooltip
            cursor={{ fill: 'rgba(46,110,242,0.08)' }}
            contentStyle={tooltipStyle()}
            labelStyle={{ color: cssVar('--text', '#1f2937'), fontWeight: 700 }}
            formatter={(value) => [money(Number(value)), 'Cobrado']}
          />
          <Bar dataKey="amount" name="Cobrado" radius={[8, 8, 0, 0]} maxBarSize={56}>
            {chartData.map((d, i) => (
              <Cell key={d.method} fill={palette[i % palette.length]} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
