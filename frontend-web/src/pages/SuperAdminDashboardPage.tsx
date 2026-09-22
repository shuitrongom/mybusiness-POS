import { useQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { money } from '@/lib/format';
import '@/pages/dashboard.css';
import './admin.css';

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

const fmtDate = (iso: string) =>
  new Date(iso).toLocaleDateString('es-MX', { day: '2-digit', month: 'short', year: 'numeric' });

/**
 * Panel del sistema (Super Admin / proveedor del SaaS): salud comercial de MyBusiness Silva.
 * Presenta ingresos, cartera de negocios por estado, conversión de prueba a licencia, mezcla de
 * planes e ingresos recientes. No muestra la operación de los negocios (eso pertenece a cada dueño).
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
  const total = s?.totalBusinesses ?? 0;
  const paying = s?.active ?? 0;
  const conversion = total > 0 ? Math.round((paying / total) * 100) : 0;
  const atRisk = (s?.expired ?? 0) + (s?.suspended ?? 0);
  const arpa = paying > 0 ? (s?.totalRevenue ?? 0) / paying : 0;
  const maxPlan = Math.max(1, ...(byPlan.data ?? []).map((p) => p.businesses));

  return (
    <div>
      <div className="dash-head">
        <div>
          <h1 className="page-title">Panel del sistema</h1>
          <p className="page-sub" style={{ margin: '4px 0 0' }}>
            Salud comercial de MyBusiness Silva como plataforma SaaS
          </p>
        </div>
        <span className="dash-live">● En vivo</span>
      </div>

      {/* Ingresos */}
      <div className="metrics" style={{ marginTop: 'var(--space-5)' }}>
        <MetricCard label="Ingresos totales" value={money(s?.totalRevenue ?? 0)}
          hint={`${s?.salesCount ?? 0} ventas registradas`} accent />
        <MetricCard label="Por licencias" value={money(s?.licenseRevenue ?? 0)}
          hint="Pago único de licencia definitiva" />
        <MetricCard label="Por módulos adicionales" value={money(s?.surchargeRevenue ?? 0)}
          hint="Excedentes vendidos a negocios" />
        <MetricCard label="Ingreso por cliente activo" value={money(arpa)}
          hint={`${paying} negocios con licencia`} />
      </div>

      {/* Cartera de negocios */}
      <h3 className="dash-section">Cartera de negocios</h3>
      <div className="metrics" style={{ marginTop: 'var(--space-3)' }}>
        <MetricCard label="Total de negocios" value={String(total)}
          hint={`Conversión a licencia: ${conversion}%`} />
        <MetricCard label="En prueba" value={String(s?.trial ?? 0)}
          hint="Oportunidades por convertir" tone="warning" />
        <MetricCard label="Activos con licencia" value={String(paying)}
          hint="Clientes de pago" tone="success" />
        <MetricCard label="En riesgo" value={String(atRisk)}
          hint="Vencidos o suspendidos" tone="danger" />
      </div>

      <div className="dash-two-col">
        {/* Mezcla de planes con barras */}
        <div className="card">
          <h3>Negocios por plan</h3>
          <p className="admin-plan-hint" style={{ marginTop: 4 }}>Distribución de la cartera por plan contratado</p>
          <div className="plan-bars">
            {(byPlan.data ?? []).map((p) => (
              <div key={p.plan} className="plan-bar-row">
                <span className="plan-bar-label">{p.plan}</span>
                <div className="plan-bar-track">
                  <div className="plan-bar-fill" style={{ width: `${(p.businesses / maxPlan) * 100}%` }} />
                </div>
                <span className="plan-bar-count">{p.businesses}</span>
              </div>
            ))}
            {byPlan.data?.length === 0 && <p className="empty">Aún no hay negocios por plan.</p>}
          </div>
        </div>

        {/* Ingresos recientes */}
        <div className="card">
          <h3>Ingresos recientes</h3>
          <p className="admin-plan-hint" style={{ marginTop: 4 }}>Últimas ventas de licencias y módulos</p>
          <table className="table">
            <thead><tr><th>Negocio</th><th>Concepto</th><th>Monto</th><th>Fecha</th></tr></thead>
            <tbody>
              {(recent.data ?? []).map((r) => (
                <tr key={r.id}>
                  <td>{r.business}</td>
                  <td>
                    <span className={`badge ${r.kind === 'LICENSE' ? 'badge-success' : 'badge-muted'}`}>
                      {r.kind === 'LICENSE' ? 'Licencia' : 'Módulo'}
                    </span>
                  </td>
                  <td><strong>{money(r.amount)}</strong></td>
                  <td>{r.date ? fmtDate(r.date) : '—'}</td>
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

function MetricCard({ label, value, hint, accent, tone }:
  { label: string; value: string; hint?: string; accent?: boolean;
    tone?: 'success' | 'warning' | 'danger' }) {
  return (
    <div className={`metric-card ${accent ? 'metric-accent' : ''} ${tone ? `metric-tone-${tone}` : ''}`}>
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value}</div>
      {hint && <div className="metric-hint">{hint}</div>}
    </div>
  );
}
