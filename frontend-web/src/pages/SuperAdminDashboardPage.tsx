import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { money } from '@/lib/format';
import '@/pages/dashboard.css';

interface SaasSummary {
  totalBusinesses: number;
  trial: number;
  active: number;
  suspended: number;
  expired: number;
  totalRevenue: number;
  licenseRevenue: number;
  surchargeRevenue: number;
  salesCount: number;
}

interface PlanRow {
  plan: string;
  businesses: number;
}

interface SaleRow {
  id: number;
  business: string;
  kind: string;
  amount: number;
  voucherType: string;
  date: string;
}

/**
 * Panel del Super Admin (dueños del SaaS): cómo va el negocio de MyBusiness Silva como proveedor.
 * Muestra los negocios por estado, los ingresos por venta de licencias/módulos y las ventas
 * recientes. No muestra la operación de los negocios (eso es de cada dueño).
 */
export function SuperAdminDashboardPage() {
  const summary = useQuery({
    queryKey: ['saas', 'summary'],
    queryFn: async () => (await api.get<SaasSummary>('/admin/bi/summary')).data,
  });

  const byPlan = useQuery({
    queryKey: ['saas', 'by-plan'],
    queryFn: async () => (await api.get<PlanRow[]>('/admin/bi/by-plan')).data,
  });

  const recent = useQuery({
    queryKey: ['saas', 'recent-sales'],
    queryFn: async () => (await api.get<SaleRow[]>('/admin/bi/recent-sales')).data,
  });

  const s = summary.data;

  return (
    <div>
      <h1 className="page-title">Panel del sistema</h1>
      <p className="page-sub">Cómo va MyBusiness Silva como negocio</p>

      {/* Métricas de ingresos */}
      <div className="metrics">
        <MetricCard label="Ingresos totales" value={money(s?.totalRevenue ?? 0)} accent />
        <MetricCard label="Por licencias" value={money(s?.licenseRevenue ?? 0)} />
        <MetricCard label="Por módulos extra" value={money(s?.surchargeRevenue ?? 0)} />
        <MetricCard label="Ventas realizadas" value={String(s?.salesCount ?? 0)} />
      </div>

      {/* Negocios por estado */}
      <h3 style={{ marginTop: 'var(--space-5)' }}>Negocios</h3>
      <div className="metrics" style={{ marginTop: 'var(--space-3)' }}>
        <MetricCard label="Total" value={String(s?.totalBusinesses ?? 0)} />
        <MetricCard label="En prueba" value={String(s?.trial ?? 0)} badge="warning" />
        <MetricCard label="Activos (con licencia)" value={String(s?.active ?? 0)} badge="success" />
        <MetricCard label="Vencidos / Suspendidos"
          value={String((s?.expired ?? 0) + (s?.suspended ?? 0))} badge="danger" />
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--space-5)', marginTop: 'var(--space-5)' }}>
        {/* Distribución por plan */}
        <div className="card">
          <h3>Negocios por plan</h3>
          <table className="table">
            <thead><tr><th>Plan</th><th>Negocios</th></tr></thead>
            <tbody>
              {(byPlan.data ?? []).map((p) => (
                <tr key={p.plan}><td>{p.plan}</td><td>{p.businesses}</td></tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Ventas recientes */}
        <div className="card">
          <h3>Ventas recientes</h3>
          <table className="table">
            <thead><tr><th>Negocio</th><th>Tipo</th><th>Monto</th><th>Comprobante</th></tr></thead>
            <tbody>
              {(recent.data ?? []).map((r) => (
                <tr key={r.id}>
                  <td>{r.business}</td>
                  <td>{r.kind === 'LICENSE' ? 'Licencia' : 'Módulo'}</td>
                  <td>{money(r.amount)}</td>
                  <td><span className="badge badge-muted">{r.voucherType}</span></td>
                </tr>
              ))}
              {recent.data?.length === 0 && (
                <tr><td colSpan={4} className="empty">Aún no hay ventas registradas.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function MetricCard({ label, value, accent, badge }:
  { label: string; value: string; accent?: boolean; badge?: 'success' | 'warning' | 'danger' }) {
  return (
    <div className={`metric-card ${accent ? 'metric-accent' : ''}`}>
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value}</div>
      {badge && <span className={`badge badge-${badge}`} style={{ marginTop: 6 }}>&nbsp;</span>}
    </div>
  );
}
