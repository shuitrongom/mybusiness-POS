import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
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

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

/**
 * Panel principal: métricas del día y productos más vendidos. Consume los endpoints de BI.
 */
export function DashboardPage() {
  const summary = useQuery({
    queryKey: ['dashboard', 'today'],
    queryFn: async () => (await api.get<DashboardSummary>('/bi/dashboard/today')).data,
  });

  const top = useQuery({
    queryKey: ['dashboard', 'top'],
    queryFn: async () => (await api.get<ProductRanking[]>('/bi/products/top?days=30&limit=5')).data,
  });

  return (
    <div>
      <h1 className="page-title">Panel</h1>
      <p className="page-sub">Resumen de tu negocio hoy</p>

      <div className="metrics">
        <MetricCard label="Ventas del día" value={String(summary.data?.salesCount ?? '—')} />
        <MetricCard label="Total vendido" value={money(summary.data?.salesTotal ?? 0)} accent />
        <MetricCard label="Ticket promedio" value={money(summary.data?.averageTicket ?? 0)} />
        <MetricCard label="Unidades vendidas" value={String(summary.data?.itemsSold ?? '—')} />
      </div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Productos más vendidos (30 días)</h3>
        <table className="table">
          <thead>
            <tr><th>Producto</th><th>Cantidad</th><th>Ingreso</th></tr>
          </thead>
          <tbody>
            {(top.data ?? []).map((p) => (
              <tr key={p.productId}>
                <td>{p.name}</td>
                <td>{p.quantity}</td>
                <td>{money(p.revenue)}</td>
              </tr>
            ))}
            {top.data?.length === 0 && (
              <tr><td colSpan={3} className="empty">Aún no hay ventas registradas.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function MetricCard({ label, value, accent }: { label: string; value: string; accent?: boolean }) {
  return (
    <div className={`metric-card ${accent ? 'metric-accent' : ''}`}>
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value}</div>
    </div>
  );
}
