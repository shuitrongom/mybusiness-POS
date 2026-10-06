import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { api } from '@/lib/api';
import { money } from '@/lib/format';
import { TrialCalendar } from '@/components/TrialCalendar';
import {
  PlanDistributionChart,
  PortfolioDonut,
  RevenueByTypeChart,
  type PortfolioDatum,
} from '@/components/SaasCharts';
import { staggerContainer, staggerItem } from '@/lib/motion';
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

interface Business {
  id: number;
  name: string;
  status: string;
  trialEndsAt: string | null;
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
  const businesses = useQuery({
    queryKey: ['admin', 'businesses'],
    queryFn: async () => (await api.get<Business[]>('/admin/businesses')).data,
  });

  const s = summary.data;
  const total = s?.totalBusinesses ?? 0;
  const paying = s?.active ?? 0;
  const conversion = total > 0 ? Math.round((paying / total) * 100) : 0;
  const atRisk = (s?.expired ?? 0) + (s?.suspended ?? 0);
  const arpa = paying > 0 ? (s?.totalRevenue ?? 0) / paying : 0;

  // Datos para las gráficas premium (Recharts).
  const portfolioData: PortfolioDatum[] = [
    { name: 'En prueba', value: s?.trial ?? 0, tone: 'trial' },
    { name: 'Activos', value: s?.active ?? 0, tone: 'active' },
    { name: 'Suspendidos', value: s?.suspended ?? 0, tone: 'suspended' },
    { name: 'Prueba vencida', value: s?.expired ?? 0, tone: 'expired' },
  ];
  const revenueData = [
    { concept: 'Licencias', amount: s?.licenseRevenue ?? 0 },
    { concept: 'Módulos', amount: s?.surchargeRevenue ?? 0 },
  ];

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
      <motion.div className="metrics" style={{ marginTop: 'var(--space-5)' }}
        variants={staggerContainer} initial="hidden" animate="visible">
        <MetricCard label="Ingresos totales" value={money(s?.totalRevenue ?? 0)}
          hint={`${s?.salesCount ?? 0} ventas registradas`} accent />
        <MetricCard label="Por licencias" value={money(s?.licenseRevenue ?? 0)}
          hint="Pago único de licencia definitiva" />
        <MetricCard label="Por módulos adicionales" value={money(s?.surchargeRevenue ?? 0)}
          hint="Excedentes vendidos a negocios" />
        <MetricCard label="Ingreso por cliente activo" value={money(arpa)}
          hint={`${paying} negocios con licencia`} />
      </motion.div>

      {/* Cartera de negocios */}
      <h3 className="dash-section">Cartera de negocios</h3>
      <motion.div className="metrics" style={{ marginTop: 'var(--space-3)' }}
        variants={staggerContainer} initial="hidden" animate="visible">
        <MetricCard label="Total de negocios" value={String(total)}
          hint={`Conversión a licencia: ${conversion}%`} />
        <MetricCard label="En prueba" value={String(s?.trial ?? 0)}
          hint="Oportunidades por convertir" tone="warning" />
        <MetricCard label="Activos con licencia" value={String(paying)}
          hint="Clientes de pago" tone="success" />
        <MetricCard label="En riesgo" value={String(atRisk)}
          hint="Vencidos o suspendidos" tone="danger" />
      </motion.div>

      {/* Gráficas premium: distribución por plan + cartera por estado */}
      <motion.div className="dash-two-col" variants={staggerContainer} initial="hidden" animate="visible">
        <motion.div className="card" variants={staggerItem}>
          <h3>Negocios por plan</h3>
          <p className="admin-plan-hint" style={{ marginTop: 4 }}>Distribución de la cartera por plan contratado</p>
          <PlanDistributionChart data={byPlan.data ?? []} />
        </motion.div>

        <motion.div className="card" variants={staggerItem}>
          <h3>Cartera por estado</h3>
          <p className="admin-plan-hint" style={{ marginTop: 4 }}>Proporción de negocios según su ciclo de licencia</p>
          <PortfolioDonut data={portfolioData} />
        </motion.div>
      </motion.div>

      {/* Ingresos por tipo + ingresos recientes */}
      <motion.div className="dash-two-col" variants={staggerContainer} initial="hidden" animate="visible">
        <motion.div className="card" variants={staggerItem}>
          <h3>Ingresos por tipo</h3>
          <p className="admin-plan-hint" style={{ marginTop: 4 }}>Licencias definitivas frente a módulos adicionales</p>
          <RevenueByTypeChart data={revenueData} />
        </motion.div>

        <motion.div className="card" variants={staggerItem}>
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
        </motion.div>
      </motion.div>

      {/* Calendario de vencimientos de prueba */}
      <h3 className="dash-section">Calendario de vencimientos</h3>
      <p className="admin-plan-hint" style={{ marginTop: 4 }}>
        Días con negocios cuya prueba termina. Da seguimiento para convertirlos a licencia.
      </p>
      <TrialCalendar businesses={businesses.data ?? []} />
    </div>
  );
}

function MetricCard({ label, value, hint, accent, tone }:
  { label: string; value: string; hint?: string; accent?: boolean;
    tone?: 'success' | 'warning' | 'danger' }) {
  return (
    <motion.div className={`metric-card ${accent ? 'metric-accent' : ''} ${tone ? `metric-tone-${tone}` : ''}`}
      variants={staggerItem}>
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value}</div>
      {hint && <div className="metric-hint">{hint}</div>}
    </motion.div>
  );
}
