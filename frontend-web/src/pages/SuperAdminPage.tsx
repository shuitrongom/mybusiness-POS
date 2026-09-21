import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import '@/pages/dashboard.css';
import './admin.css';

interface Plan {
  id: number;
  code: string;
  name: string;
  licensePriceSuggested: number;
  moduleKeys: string[];
}

interface Business {
  id: number;
  name: string;
  rfc: string | null;
  businessLine: string;
  status: string;
  trialMonths: number;
  trialEndsAt: string | null;
  planId: number | null;
}

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

const STATUS_BADGE: Record<string, string> = {
  TRIAL: 'badge-warning',
  ACTIVE: 'badge-success',
  SUSPENDED: 'badge-muted',
  EXPIRED: 'badge-danger',
};

const STATUS_LABEL: Record<string, string> = {
  TRIAL: 'En prueba',
  ACTIVE: 'Activo',
  SUSPENDED: 'Suspendido',
  EXPIRED: 'Prueba vencida',
};

const LINES = [
  { code: 'abarrotes', name: 'Abarrotes' },
  { code: 'materias_primas', name: 'Materias primas' },
  { code: 'panaderia', name: 'Panadería' },
  { code: 'polleria', name: 'Pollería' },
];

/**
 * Panel del Super Admin: administra los negocios (tenants), sus licencias y planes.
 * Permite dar de alta un negocio (con plan y meses de prueba), comprar la licencia definitiva,
 * suspender/reactivar y consultar los planes disponibles.
 */
export function SuperAdminPage() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    name: '', rfc: '', businessLine: 'abarrotes', planId: '', trialMonths: '1',
  });

  const plans = useQuery({
    queryKey: ['admin', 'plans'],
    queryFn: async () => (await api.get<Plan[]>('/admin/plans')).data,
  });

  const businesses = useQuery({
    queryKey: ['admin', 'businesses'],
    queryFn: async () => (await api.get<Business[]>('/admin/businesses')).data,
  });

  const createBusiness = useMutation({
    mutationFn: async () =>
      api.post('/admin/businesses', {
        name: form.name,
        rfc: form.rfc || null,
        businessLine: form.businessLine,
        planId: Number(form.planId),
        trialMonths: Number(form.trialMonths),
      }),
    onSuccess: () => {
      setForm({ name: '', rfc: '', businessLine: 'abarrotes', planId: '', trialMonths: '1' });
      queryClient.invalidateQueries({ queryKey: ['admin', 'businesses'] });
    },
  });

  const action = useMutation({
    mutationFn: async ({ id, verb }: { id: number; verb: string }) =>
      api.post(`/admin/businesses/${id}/${verb}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'businesses'] }),
  });

  const selectedPlan = plans.data?.find((p) => String(p.id) === form.planId);

  return (
    <div>
      <h1 className="page-title">Administración</h1>
      <p className="page-sub">Gestiona los negocios, sus licencias y planes</p>

      <div className="card">
        <h3>Crear negocio</h3>
        <div className="admin-form">
          <label className="field">
            <span>Nombre del negocio</span>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </label>
          <label className="field">
            <span>RFC (opcional)</span>
            <input value={form.rfc} onChange={(e) => setForm({ ...form, rfc: e.target.value })} />
          </label>
          <label className="field">
            <span>Giro</span>
            <select value={form.businessLine} onChange={(e) => setForm({ ...form, businessLine: e.target.value })}>
              {LINES.map((l) => <option key={l.code} value={l.code}>{l.name}</option>)}
            </select>
          </label>
          <label className="field">
            <span>Plan</span>
            <select value={form.planId} onChange={(e) => setForm({ ...form, planId: e.target.value })}>
              <option value="">Selecciona…</option>
              {(plans.data ?? []).map((p) => (
                <option key={p.id} value={p.id}>{p.name} — {money(p.licensePriceSuggested)}</option>
              ))}
            </select>
          </label>
          <label className="field">
            <span>Meses de prueba</span>
            <input type="number" min={0} value={form.trialMonths}
                   onChange={(e) => setForm({ ...form, trialMonths: e.target.value })} />
          </label>
          <button className="btn-accent" disabled={!form.name || !form.planId || createBusiness.isPending}
                  onClick={() => createBusiness.mutate()}>
            {createBusiness.isPending ? 'Creando…' : 'Crear negocio'}
          </button>
        </div>
        {selectedPlan && (
          <p className="admin-plan-hint">
            Precio de licencia sugerido: <strong>{money(selectedPlan.licensePriceSuggested)}</strong>
            {' · '}Módulos incluidos: {selectedPlan.moduleKeys.length}
          </p>
        )}
      </div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Negocios</h3>
        <table className="table">
          <thead>
            <tr><th>Negocio</th><th>Giro</th><th>Estado</th><th>Prueba</th><th>Acciones</th></tr>
          </thead>
          <tbody>
            {(businesses.data ?? []).map((b) => (
              <tr key={b.id}>
                <td>{b.name}</td>
                <td>{b.businessLine}</td>
                <td>
                  <span className={`badge ${STATUS_BADGE[b.status] ?? 'badge-muted'}`}>
                    {STATUS_LABEL[b.status] ?? b.status}
                  </span>
                </td>
                <td>{b.trialEndsAt ? new Date(b.trialEndsAt).toLocaleDateString('es-MX') : '—'}</td>
                <td className="admin-actions">
                  {b.status !== 'ACTIVE' && (
                    <button className="btn-primary" onClick={() => action.mutate({ id: b.id, verb: 'purchase' })}>
                      Vender licencia
                    </button>
                  )}
                  {b.status === 'SUSPENDED' ? (
                    <button className="btn-ghost" onClick={() => action.mutate({ id: b.id, verb: 'reactivate' })}>
                      Reactivar
                    </button>
                  ) : (
                    <button className="btn-ghost" onClick={() => action.mutate({ id: b.id, verb: 'suspend' })}>
                      Suspender
                    </button>
                  )}
                </td>
              </tr>
            ))}
            {businesses.data?.length === 0 && (
              <tr><td colSpan={5} className="empty">Aún no hay negocios. Crea el primero arriba.</td></tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
