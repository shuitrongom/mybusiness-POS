import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, getToken } from '@/lib/api';
import { isValidEmail, isValidMxPhone, isValidRfc, normalizeMxPhone, normalizeRfc } from '@/lib/validation';
import '@/pages/dashboard.css';
import './admin.css';

// ---------------------------------------------------------------------------
// Tipos
// ---------------------------------------------------------------------------
interface Plan {
  id: number;
  code: string;
  name: string;
  description: string | null;
  licensePriceSuggested: number;
  active: boolean;
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

interface BusinessLine {
  code: string;
  name: string;
  description: string | null;
}

interface ModuleCatalogItem {
  moduleKey: string;
  name: string;
  description: string | null;
  surchargeSuggestedPrice: number;
}

interface ModuleView {
  moduleKey: string;
  enabled: boolean;
  origin: string;
  soldPrice: number | null;
  soldAt: string | null;
}

interface BusinessDetail extends Business {
  schemaName: string;
  trialStartsAt: string | null;
  purchasedAt: string | null;
  modules: ModuleView[];
}

interface OwnerCredentials {
  email: string;
  password: string;
  whatsapp: string | null;
}

interface CreatedBusiness {
  business: Business;
  owner: OwnerCredentials;
  emailSent: boolean;
  whatsappSent: boolean;
}

// ---------------------------------------------------------------------------
// Utilidades de presentación
// ---------------------------------------------------------------------------
const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

const fmtDate = (iso: string | null) =>
  iso ? new Date(iso).toLocaleDateString('es-MX', { year: 'numeric', month: 'short', day: 'numeric' }) : '—';

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

type Tab = 'businesses' | 'plans' | 'lines';

/**
 * Consola de administración del SaaS (Super Admin). Tres áreas:
 * - Negocios: alta con usuario dueño (credenciales visibles una vez), detalle, licencia,
 *   suspensión, respaldo y baja.
 * - Planes: catálogo con módulos y precio.
 * - Giros: perfiles de negocio configurables.
 */
export function SuperAdminPage() {
  const [tab, setTab] = useState<Tab>('businesses');

  return (
    <div>
      <h1 className="page-title">Administración</h1>
      <p className="page-sub">Gestiona negocios, planes y giros de la plataforma</p>

      <div className="admin-tabs" role="tablist">
        <button role="tab" aria-selected={tab === 'businesses'}
          className={`admin-tab ${tab === 'businesses' ? 'is-active' : ''}`}
          onClick={() => setTab('businesses')}>Negocios</button>
        <button role="tab" aria-selected={tab === 'plans'}
          className={`admin-tab ${tab === 'plans' ? 'is-active' : ''}`}
          onClick={() => setTab('plans')}>Planes</button>
        <button role="tab" aria-selected={tab === 'lines'}
          className={`admin-tab ${tab === 'lines' ? 'is-active' : ''}`}
          onClick={() => setTab('lines')}>Giros</button>
      </div>

      {tab === 'businesses' && <BusinessesTab />}
      {tab === 'plans' && <PlansTab />}
      {tab === 'lines' && <LinesTab />}
    </div>
  );
}

// ===========================================================================
// NEGOCIOS
// ===========================================================================
function BusinessesTab() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({
    name: '', rfc: '', businessLine: '', planId: '', trialMonths: '1',
    ownerName: '', ownerEmail: '', ownerWhatsapp: '',
  });
  const [credentials, setCredentials] = useState<CreatedBusiness | null>(null);
  const [detailId, setDetailId] = useState<number | null>(null);
  const [confirmDelete, setConfirmDelete] = useState<Business | null>(null);

  const plans = useQuery({
    queryKey: ['admin', 'plans'],
    queryFn: async () => (await api.get<Plan[]>('/admin/plans')).data,
  });
  const lines = useQuery({
    queryKey: ['admin', 'lines'],
    queryFn: async () => (await api.get<BusinessLine[]>('/admin/business-lines')).data,
  });
  const businesses = useQuery({
    queryKey: ['admin', 'businesses'],
    queryFn: async () => (await api.get<Business[]>('/admin/businesses')).data,
  });

  const createBusiness = useMutation({
    mutationFn: async () =>
      (await api.post<CreatedBusiness>('/admin/businesses', {
        name: form.name.trim(),
        rfc: form.rfc ? normalizeRfc(form.rfc) : null,
        businessLine: form.businessLine,
        planId: Number(form.planId),
        trialMonths: Number(form.trialMonths),
        ownerName: form.ownerName.trim(),
        ownerEmail: form.ownerEmail.trim(),
        ownerWhatsapp: form.ownerWhatsapp ? normalizeMxPhone(form.ownerWhatsapp) : null,
      })).data,
    onSuccess: (data) => {
      setCredentials(data);
      setForm({
        name: '', rfc: '', businessLine: '', planId: '', trialMonths: '1',
        ownerName: '', ownerEmail: '', ownerWhatsapp: '',
      });
      queryClient.invalidateQueries({ queryKey: ['admin', 'businesses'] });
    },
  });

  const action = useMutation({
    mutationFn: async ({ id, verb }: { id: number; verb: string }) =>
      api.post(`/admin/businesses/${id}/${verb}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'businesses'] }),
  });

  const removeBusiness = useMutation({
    mutationFn: async (id: number) => api.delete(`/admin/businesses/${id}`),
    onSuccess: () => {
      setConfirmDelete(null);
      queryClient.invalidateQueries({ queryKey: ['admin', 'businesses'] });
    },
  });

  const selectedPlan = plans.data?.find((p) => String(p.id) === form.planId);
  const planName = (id: number | null) => plans.data?.find((p) => p.id === id)?.name ?? '—';

  // Errores de validación en vivo (solo se muestran cuando el campo tiene contenido).
  const rfcError = form.rfc && !isValidRfc(form.rfc) ? 'RFC inválido (12 o 13 caracteres).' : '';
  const emailError = form.ownerEmail && !isValidEmail(form.ownerEmail) ? 'Correo inválido.' : '';
  const whatsappError = form.ownerWhatsapp && !isValidMxPhone(form.ownerWhatsapp)
    ? 'Debe tener 10 dígitos (lada de México).' : '';

  const canSubmit =
    form.name.trim() && form.planId && form.businessLine &&
    form.ownerName.trim() && form.ownerEmail.trim() &&
    !rfcError && !emailError && !whatsappError;

  const downloadBackup = async (b: Business) => {
    // Descarga autenticada del respaldo JSON del negocio.
    const res = await fetch(`/api/v1/admin/businesses/${b.id}/backup`, {
      headers: { Authorization: `Bearer ${getToken() ?? ''}` },
    });
    if (!res.ok) return;
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `respaldo-${b.name.replace(/\s+/g, '-').toLowerCase()}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <>
      <div className="card">
        <h3>Crear negocio</h3>
        <p className="admin-plan-hint" style={{ marginTop: 4 }}>
          Al crear el negocio se genera automáticamente el usuario Dueño con una contraseña
          temporal que verás una sola vez para entregarla al cliente.
        </p>
        <div className="admin-grid">
          <label className="field">
            <span>Nombre del negocio</span>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
              placeholder="Abarrotes Don Silva" />
          </label>
          <label className="field">
            <span>RFC (opcional)</span>
            <input value={form.rfc}
              onChange={(e) => setForm({ ...form, rfc: e.target.value.toUpperCase() })}
              placeholder="XAXX010101000" maxLength={13}
              className={rfcError ? 'input-error' : ''} />
            {rfcError && <small className="field-error">{rfcError}</small>}
          </label>
          <label className="field">
            <span>Giro</span>
            <select value={form.businessLine} onChange={(e) => setForm({ ...form, businessLine: e.target.value })}>
              <option value="">Selecciona…</option>
              {(lines.data ?? []).map((l) => <option key={l.code} value={l.code}>{l.name}</option>)}
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
          <label className="field">
            <span>Nombre del dueño</span>
            <input value={form.ownerName} onChange={(e) => setForm({ ...form, ownerName: e.target.value })}
              placeholder="Juan Silva" />
          </label>
          <label className="field">
            <span>Correo del dueño (acceso)</span>
            <input type="email" value={form.ownerEmail}
              onChange={(e) => setForm({ ...form, ownerEmail: e.target.value })}
              placeholder="dueno@negocio.com"
              className={emailError ? 'input-error' : ''} />
            {emailError && <small className="field-error">{emailError}</small>}
          </label>
          <label className="field">
            <span>WhatsApp del dueño (10 dígitos)</span>
            <input type="tel" value={form.ownerWhatsapp}
              onChange={(e) => setForm({ ...form, ownerWhatsapp: e.target.value })}
              placeholder="55 1234 5678"
              className={whatsappError ? 'input-error' : ''} />
            {whatsappError && <small className="field-error">{whatsappError}</small>}
          </label>
        </div>

        {selectedPlan && (
          <p className="admin-plan-hint">
            Precio de licencia sugerido: <strong>{money(selectedPlan.licensePriceSuggested)}</strong>
            {' · '}Módulos incluidos: <strong>{selectedPlan.moduleKeys.length}</strong>
            {' — '}{selectedPlan.moduleKeys.join(', ')}
          </p>
        )}

        <div style={{ marginTop: 'var(--space-4)' }}>
          <button className="btn-accent" disabled={!canSubmit || createBusiness.isPending}
            onClick={() => createBusiness.mutate()}>
            {createBusiness.isPending ? 'Creando…' : 'Crear negocio y usuario dueño'}
          </button>
          {createBusiness.isError && (
            <span className="admin-inline-error">No se pudo crear. Revisa el correo del dueño (¿ya existe?).</span>
          )}
        </div>
      </div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Negocios ({businesses.data?.length ?? 0})</h3>
        <table className="table">
          <thead>
            <tr><th>Negocio</th><th>Giro</th><th>Plan</th><th>Estado</th><th>Prueba hasta</th><th>Acciones</th></tr>
          </thead>
          <tbody>
            {(businesses.data ?? []).map((b) => (
              <tr key={b.id}>
                <td><strong>{b.name}</strong>{b.rfc && <div className="admin-sub">{b.rfc}</div>}</td>
                <td>{b.businessLine}</td>
                <td>{planName(b.planId)}</td>
                <td>
                  <span className={`badge ${STATUS_BADGE[b.status] ?? 'badge-muted'}`}>
                    {STATUS_LABEL[b.status] ?? b.status}
                  </span>
                </td>
                <td>{fmtDate(b.trialEndsAt)}</td>
                <td className="admin-actions">
                  <button className="btn-ghost" onClick={() => setDetailId(b.id)}>Detalle</button>
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
                  <button className="btn-ghost" onClick={() => downloadBackup(b)}>Respaldo</button>
                  <button className="btn-danger-ghost" onClick={() => setConfirmDelete(b)}>Eliminar</button>
                </td>
              </tr>
            ))}
            {businesses.data?.length === 0 && (
              <tr><td colSpan={6} className="empty">Aún no hay negocios. Crea el primero arriba.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {credentials && (
        <CredentialsModal data={credentials} onClose={() => setCredentials(null)} />
      )}
      {detailId != null && (
        <DetailModal id={detailId} planName={planName} onClose={() => setDetailId(null)} />
      )}
      {confirmDelete && (
        <DeleteModal
          business={confirmDelete}
          pending={removeBusiness.isPending}
          onCancel={() => setConfirmDelete(null)}
          onConfirm={() => removeBusiness.mutate(confirmDelete.id)}
        />
      )}
    </>
  );
}

function CredentialsModal({ data, onClose }: { data: CreatedBusiness; onClose: () => void }) {
  const [copied, setCopied] = useState(false);
  const text =
    `Negocio: ${data.business.name}\n` +
    `Acceso: ${data.owner.email}\n` +
    `Contraseña temporal: ${data.owner.password}`;
  const copy = async () => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <ModalShell onClose={onClose} title="Negocio creado" wide={false}>
      <p className="admin-modal-lead">
        Guarda estas credenciales ahora. La contraseña <strong>no se volverá a mostrar</strong>.
        El dueño ingresa desde la pestaña “Mi negocio” del inicio de sesión y el sistema le
        pedirá cambiarla en su primer acceso.
      </p>
      <div className="cred-box">
        <div className="cred-row"><span>Negocio</span><strong>{data.business.name}</strong></div>
        <div className="cred-row"><span>Correo de acceso</span><code>{data.owner.email}</code></div>
        <div className="cred-row"><span>Contraseña temporal</span><code className="cred-pass">{data.owner.password}</code></div>
        {data.owner.whatsapp && (
          <div className="cred-row"><span>WhatsApp</span><code>{data.owner.whatsapp}</code></div>
        )}
      </div>

      <div className="cred-delivery">
        <span className={`cred-delivery-chip ${data.emailSent ? 'is-ok' : 'is-off'}`}>
          {data.emailSent ? '✓ Enviado por correo' : '• Correo no enviado'}
        </span>
        <span className={`cred-delivery-chip ${data.whatsappSent ? 'is-ok' : 'is-off'}`}>
          {data.whatsappSent ? '✓ Enviado por WhatsApp' : '• WhatsApp no enviado'}
        </span>
      </div>
      <p className="admin-plan-hint" style={{ marginTop: 8 }}>
        Si el cliente no recibe el mensaje, comparte tú estas credenciales. Quedan guardadas aquí
        solo hasta que cierres esta ventana.
      </p>

      <div className="admin-modal-actions">
        <button className="btn-ghost" onClick={copy}>{copied ? '¡Copiado!' : 'Copiar credenciales'}</button>
        <button className="btn-primary" onClick={onClose}>Entendido</button>
      </div>
    </ModalShell>
  );
}

function DetailModal({ id, planName, onClose }:
  { id: number; planName: (id: number | null) => string; onClose: () => void }) {
  const detail = useQuery({
    queryKey: ['admin', 'business', id],
    queryFn: async () => (await api.get<BusinessDetail>(`/admin/businesses/${id}`)).data,
  });
  const d = detail.data;
  return (
    <ModalShell onClose={onClose} title={d?.name ?? 'Detalle del negocio'} wide>
      {!d ? (
        <p className="admin-modal-lead">Cargando…</p>
      ) : (
        <>
          <div className="detail-grid">
            <Info label="Estado" value={STATUS_LABEL[d.status] ?? d.status} />
            <Info label="Giro" value={d.businessLine} />
            <Info label="Plan" value={planName(d.planId)} />
            <Info label="RFC" value={d.rfc ?? '—'} />
            <Info label="Schema de datos" value={d.schemaName} />
            <Info label="Meses de prueba" value={String(d.trialMonths)} />
            <Info label="Inicio de prueba" value={fmtDate(d.trialStartsAt)} />
            <Info label="Fin de prueba" value={fmtDate(d.trialEndsAt)} />
            <Info label="Licencia comprada" value={fmtDate(d.purchasedAt)} />
          </div>
          <h4 className="detail-section">Módulos habilitados ({d.modules.filter((m) => m.enabled).length})</h4>
          <table className="table">
            <thead><tr><th>Módulo</th><th>Origen</th><th>Estado</th></tr></thead>
            <tbody>
              {d.modules.map((m) => (
                <tr key={m.moduleKey}>
                  <td>{m.moduleKey}</td>
                  <td>{m.origin === 'PLAN' ? 'Plan' : 'Adicional'}</td>
                  <td><span className={`badge ${m.enabled ? 'badge-success' : 'badge-muted'}`}>
                    {m.enabled ? 'Activo' : 'Inactivo'}</span></td>
                </tr>
              ))}
              {d.modules.length === 0 && <tr><td colSpan={3} className="empty">Sin módulos.</td></tr>}
            </tbody>
          </table>
        </>
      )}
    </ModalShell>
  );
}

function DeleteModal({ business, pending, onCancel, onConfirm }:
  { business: Business; pending: boolean; onCancel: () => void; onConfirm: () => void }) {
  const [text, setText] = useState('');
  return (
    <ModalShell onClose={onCancel} title="Eliminar negocio" wide={false}>
      <p className="admin-modal-lead">
        Esta acción <strong>elimina de forma permanente</strong> el negocio “{business.name}” y
        todos sus datos (ventas, inventario, clientes). No se puede deshacer. Descarga primero un
        respaldo si lo necesitas.
      </p>
      <label className="field">
        <span>Escribe <code>ELIMINAR</code> para confirmar</span>
        <input value={text} onChange={(e) => setText(e.target.value)} placeholder="ELIMINAR" />
      </label>
      <div className="admin-modal-actions">
        <button className="btn-ghost" onClick={onCancel}>Cancelar</button>
        <button className="btn-danger" disabled={text !== 'ELIMINAR' || pending} onClick={onConfirm}>
          {pending ? 'Eliminando…' : 'Eliminar definitivamente'}
        </button>
      </div>
    </ModalShell>
  );
}

// ===========================================================================
// PLANES
// ===========================================================================
function PlansTab() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({ code: '', name: '', description: '', licensePrice: '', modules: [] as string[] });

  const plans = useQuery({
    queryKey: ['admin', 'plans'],
    queryFn: async () => (await api.get<Plan[]>('/admin/plans')).data,
  });
  const modules = useQuery({
    queryKey: ['admin', 'module-catalog'],
    queryFn: async () => (await api.get<ModuleCatalogItem[]>('/admin/modules')).data,
  });

  const createPlan = useMutation({
    mutationFn: async () =>
      api.post('/admin/plans', {
        code: form.code, name: form.name, description: form.description || null,
        licensePrice: Number(form.licensePrice), moduleKeys: form.modules,
      }),
    onSuccess: () => {
      setForm({ code: '', name: '', description: '', licensePrice: '', modules: [] });
      queryClient.invalidateQueries({ queryKey: ['admin', 'plans'] });
    },
  });

  const deactivate = useMutation({
    mutationFn: async (id: number) => api.post(`/admin/plans/${id}/deactivate`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'plans'] }),
  });

  const toggleModule = (key: string) =>
    setForm((f) => ({
      ...f,
      modules: f.modules.includes(key) ? f.modules.filter((k) => k !== key) : [...f.modules, key],
    }));

  const canSubmit = form.code && form.name && form.licensePrice && form.modules.length > 0;

  return (
    <>
      <div className="card">
        <h3>Crear plan</h3>
        <div className="admin-grid">
          <label className="field">
            <span>Código</span>
            <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })}
              placeholder="PROFESSIONAL" />
          </label>
          <label className="field">
            <span>Nombre</span>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
              placeholder="Profesional" />
          </label>
          <label className="field">
            <span>Precio de licencia</span>
            <input type="number" min={0} value={form.licensePrice}
              onChange={(e) => setForm({ ...form, licensePrice: e.target.value })} placeholder="9900" />
          </label>
          <label className="field admin-col-span">
            <span>Descripción</span>
            <input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })}
              placeholder="Para negocios en crecimiento" />
          </label>
        </div>
        <div className="module-picker">
          <span className="module-picker-title">Módulos incluidos</span>
          <div className="module-chips">
            {(modules.data ?? []).map((m) => (
              <label key={m.moduleKey} className={`module-chip ${form.modules.includes(m.moduleKey) ? 'is-on' : ''}`}
                title={m.description ?? ''}>
                <input type="checkbox" checked={form.modules.includes(m.moduleKey)}
                  onChange={() => toggleModule(m.moduleKey)} />
                {m.name}
              </label>
            ))}
          </div>
        </div>
        <div style={{ marginTop: 'var(--space-4)' }}>
          <button className="btn-accent" disabled={!canSubmit || createPlan.isPending}
            onClick={() => createPlan.mutate()}>
            {createPlan.isPending ? 'Creando…' : 'Crear plan'}
          </button>
        </div>
      </div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Planes ({plans.data?.length ?? 0})</h3>
        <table className="table">
          <thead><tr><th>Plan</th><th>Precio</th><th>Módulos</th><th>Estado</th><th>Acciones</th></tr></thead>
          <tbody>
            {(plans.data ?? []).map((p) => (
              <tr key={p.id}>
                <td><strong>{p.name}</strong><div className="admin-sub">{p.code}</div></td>
                <td>{money(p.licensePriceSuggested)}</td>
                <td>{p.moduleKeys.length}</td>
                <td><span className={`badge ${p.active ? 'badge-success' : 'badge-muted'}`}>
                  {p.active ? 'Activo' : 'Inactivo'}</span></td>
                <td className="admin-actions">
                  {p.active && (
                    <button className="btn-ghost" onClick={() => deactivate.mutate(p.id)}>Desactivar</button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

// ===========================================================================
// GIROS
// ===========================================================================
function LinesTab() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({ code: '', name: '', description: '' });

  const lines = useQuery({
    queryKey: ['admin', 'lines'],
    queryFn: async () => (await api.get<BusinessLine[]>('/admin/business-lines')).data,
  });

  const createLine = useMutation({
    mutationFn: async () =>
      api.post('/admin/business-lines', {
        code: form.code || null, name: form.name, description: form.description || null,
      }),
    onSuccess: () => {
      setForm({ code: '', name: '', description: '' });
      queryClient.invalidateQueries({ queryKey: ['admin', 'lines'] });
    },
  });

  const removeLine = useMutation({
    mutationFn: async (code: string) => api.delete(`/admin/business-lines/${code}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'lines'] }),
  });

  return (
    <>
      <div className="card">
        <h3>Crear giro</h3>
        <p className="admin-plan-hint" style={{ marginTop: 4 }}>
          Un giro define el tipo de negocio (abarrotes, panadería…). El código se genera del nombre
          si lo dejas vacío.
        </p>
        <div className="admin-grid">
          <label className="field">
            <span>Nombre</span>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
              placeholder="Ferretería" />
          </label>
          <label className="field">
            <span>Código (opcional)</span>
            <input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })}
              placeholder="ferreteria" />
          </label>
          <label className="field admin-col-span">
            <span>Descripción</span>
            <input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })}
              placeholder="Venta de herramientas y materiales" />
          </label>
        </div>
        <div style={{ marginTop: 'var(--space-4)' }}>
          <button className="btn-accent" disabled={!form.name || createLine.isPending}
            onClick={() => createLine.mutate()}>
            {createLine.isPending ? 'Creando…' : 'Crear giro'}
          </button>
          {createLine.isError && (
            <span className="admin-inline-error">No se pudo crear (¿código repetido?).</span>
          )}
        </div>
      </div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Giros ({lines.data?.length ?? 0})</h3>
        <table className="table">
          <thead><tr><th>Giro</th><th>Código</th><th>Descripción</th><th>Acciones</th></tr></thead>
          <tbody>
            {(lines.data ?? []).map((l) => (
              <tr key={l.code}>
                <td><strong>{l.name}</strong></td>
                <td><code>{l.code}</code></td>
                <td>{l.description ?? '—'}</td>
                <td className="admin-actions">
                  <button className="btn-danger-ghost" onClick={() => removeLine.mutate(l.code)}>
                    Eliminar
                  </button>
                </td>
              </tr>
            ))}
            {lines.data?.length === 0 && (
              <tr><td colSpan={4} className="empty">Aún no hay giros.</td></tr>
            )}
          </tbody>
        </table>
        {removeLine.isError && (
          <p className="admin-inline-error">No se pudo eliminar: el giro está en uso por algún negocio.</p>
        )}
      </div>
    </>
  );
}

// ===========================================================================
// Componentes compartidos
// ===========================================================================
function ModalShell({ title, children, onClose, wide }:
  { title: string; children: React.ReactNode; onClose: () => void; wide?: boolean }) {
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className={`modal-card ${wide ? 'modal-wide' : ''}`} onClick={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>{title}</h3>
          <button className="modal-close" onClick={onClose} aria-label="Cerrar">×</button>
        </div>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  );
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <div className="detail-info">
      <div className="detail-info-label">{label}</div>
      <div className="detail-info-value">{value}</div>
    </div>
  );
}
