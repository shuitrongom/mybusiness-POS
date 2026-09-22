import { useMemo, useState } from 'react';
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

      {/* Calendario de vencimientos de prueba */}
      <h3 className="dash-section">Calendario de vencimientos</h3>
      <p className="admin-plan-hint" style={{ marginTop: 4 }}>
        Días con negocios cuya prueba termina. Da seguimiento para convertirlos a licencia.
      </p>
      <TrialCalendar businesses={businesses.data ?? []} />
    </div>
  );
}

const MONTHS = [
  'Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio',
  'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre',
];
const WEEKDAYS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

/**
 * Calendario mensual que resalta los días en los que vence la prueba de uno o más negocios.
 * Permite navegar entre meses. Al pasar el cursor por un día marcado se ven los negocios.
 */
function TrialCalendar({ businesses }: { businesses: Business[] }) {
  const today = new Date();
  const [cursor, setCursor] = useState(new Date(today.getFullYear(), today.getMonth(), 1));

  // Mapa "YYYY-M-D" -> lista de nombres de negocios que vencen ese día.
  const byDay = useMemo(() => {
    const map = new Map<string, string[]>();
    for (const b of businesses) {
      if (!b.trialEndsAt || b.status !== 'TRIAL') continue;
      const d = new Date(b.trialEndsAt);
      const key = `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
      map.set(key, [...(map.get(key) ?? []), b.name]);
    }
    return map;
  }, [businesses]);

  const year = cursor.getFullYear();
  const month = cursor.getMonth();
  const firstDay = new Date(year, month, 1);
  // getDay(): 0=domingo. Convertimos a semana que empieza en lunes (0=lunes).
  const leadingBlanks = (firstDay.getDay() + 6) % 7;
  const daysInMonth = new Date(year, month + 1, 0).getDate();

  const cells: (number | null)[] = [];
  for (let i = 0; i < leadingBlanks; i++) cells.push(null);
  for (let d = 1; d <= daysInMonth; d++) cells.push(d);

  const isToday = (d: number) =>
    d === today.getDate() && month === today.getMonth() && year === today.getFullYear();

  const goPrev = () => setCursor(new Date(year, month - 1, 1));
  const goNext = () => setCursor(new Date(year, month + 1, 1));

  return (
    <div className="card cal-card">
      <div className="cal-head">
        <button className="btn-ghost cal-nav" onClick={goPrev} aria-label="Mes anterior">‹</button>
        <div className="cal-title">{MONTHS[month]} {year}</div>
        <button className="btn-ghost cal-nav" onClick={goNext} aria-label="Mes siguiente">›</button>
      </div>

      <div className="cal-grid cal-weekdays">
        {WEEKDAYS.map((w) => <div key={w} className="cal-weekday">{w}</div>)}
      </div>

      <div className="cal-grid">
        {cells.map((d, i) => {
          if (d === null) return <div key={`b-${i}`} className="cal-cell cal-empty" />;
          const key = `${year}-${month}-${d}`;
          const list = byDay.get(key);
          return (
            <div
              key={key}
              className={`cal-cell ${isToday(d) ? 'is-today' : ''} ${list ? 'has-event' : ''}`}
              title={list ? `Vence prueba: ${list.join(', ')}` : ''}
            >
              <span className="cal-day">{d}</span>
              {list && <span className="cal-dot" aria-label={`${list.length} vencimiento(s)`}>{list.length}</span>}
            </div>
          );
        })}
      </div>

      <div className="cal-legend">
        <span className="cal-legend-item"><span className="cal-dot cal-dot-legend">•</span> Vence prueba</span>
        <span className="cal-legend-item"><span className="cal-today-dot" /> Hoy</span>
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
