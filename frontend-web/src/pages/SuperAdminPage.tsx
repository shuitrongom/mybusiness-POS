import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AnimatePresence, motion } from 'motion/react';
import {
  Trash2, Eye, ShieldOff, ShieldCheck, DownloadCloud, BadgeDollarSign,
  Store, Layers, PackageOpen, Copy, Check, KeyRound, Mail, MessageCircle, UploadCloud, Database,
} from 'lucide-react';
import { api, getToken } from '@/lib/api';
import {
  isValidEmail, isValidMxPhone, isValidRfc, normalizeMxPhone, normalizeRfc,
  titleCase, limitToTenDigits,
} from '@/lib/validation';
import { fadeInUp, popIn, pressable, staggerContainer, staggerItem, quick } from '@/lib/motion';
import { toast } from '@/store/toast';
import '@/pages/dashboard.css';
import './admin.css';

// ---------------------------------------------------------------------------
// Tipos (idénticos a los contratos de la API — NO modificar)
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

interface ResetOwnerPassword {
  businessName: string;
  email: string;
  password: string;
  whatsapp: string | null;
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

/** Genera las iniciales para el avatar de un negocio (máx. 2 letras). */
const initials = (name: string) =>
  name.trim().split(/\s+/).slice(0, 2).map((w) => w.charAt(0).toUpperCase()).join('') || '?';

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

      {/* Transición animada entre pestañas: el panel saliente se desvanece antes de entrar el nuevo. */}
      <AnimatePresence mode="wait">
        <motion.div
          key={tab}
          variants={fadeInUp}
          initial="hidden"
          animate="visible"
          exit="exit"
        >
          {tab === 'businesses' && <BusinessesTab />}
          {tab === 'plans' && <PlansTab />}
          {tab === 'lines' && <LinesTab />}
        </motion.div>
      </AnimatePresence>
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
  const [resetResult, setResetResult] = useState<ResetOwnerPassword | null>(null);

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
      toast.success('Negocio creado', 'Se generaron las credenciales del dueño.');
    },
    onError: () => {
      toast.error('No se pudo crear el negocio', 'Revisa el correo del dueño (¿ya existe?).');
    },
  });

  const action = useMutation({
    mutationFn: async ({ id, verb }: { id: number; verb: string }) =>
      api.post(`/admin/businesses/${id}/${verb}`),
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'businesses'] });
      // Toast según la acción realizada.
      if (variables.verb === 'purchase') toast.success('Licencia vendida', 'El negocio quedó activo.');
      else if (variables.verb === 'suspend') toast.info('Negocio suspendido');
      else if (variables.verb === 'reactivate') toast.success('Negocio reactivado');
    },
    onError: () => toast.error('No se pudo completar la acción'),
  });

  const removeBusiness = useMutation({
    mutationFn: async (id: number) => api.delete(`/admin/businesses/${id}`),
    onSuccess: () => {
      setConfirmDelete(null);
      queryClient.invalidateQueries({ queryKey: ['admin', 'businesses'] });
      toast.success('Negocio eliminado', 'Se borraron sus datos de forma permanente.');
    },
    onError: () => toast.error('No se pudo eliminar el negocio'),
  });

  const resetOwnerPassword = useMutation({
    mutationFn: async (b: Business) => {
      const { data } = await api.post<Omit<ResetOwnerPassword, 'businessName'>>(
        `/admin/businesses/${b.id}/reset-owner-password`);
      return { ...data, businessName: b.name } as ResetOwnerPassword;
    },
    onSuccess: (data) => {
      setResetResult(data);
      toast.success('Contraseña regenerada', 'Se reenvió al dueño por correo y WhatsApp.');
    },
    onError: () => toast.error('No se pudo regenerar la contraseña del dueño'),
  });

  const seedDemo = useMutation({
    mutationFn: async (b: Business) =>
      (await api.post<{ sales: number; products: number; customers: number }>(
        `/admin/businesses/${b.id}/seed-demo`)).data,
    onSuccess: (data) => {
      if (data.sales === 0) {
        toast.info('Sin cambios', 'El negocio ya tenía datos; no se volvió a sembrar.');
      } else {
        toast.success('Datos demo cargados',
          `${data.sales} ventas, ${data.products} productos y ${data.customers} clientes.`);
      }
    },
    onError: () => toast.error('No se pudieron sembrar los datos demo'),
  });

  const restoreBusiness = useMutation({
    mutationFn: async (backup: unknown) =>
      (await api.post<Business>('/admin/businesses/restore', backup)).data,
    onSuccess: (b) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'businesses'] });
      toast.success('Empresa restaurada', `"${b.name}" se restauró desde el respaldo.`);
    },
    onError: () => toast.error('No se pudo restaurar la empresa', 'Verifica que el archivo sea un respaldo válido.'),
  });

  // Lee un archivo JSON de respaldo y dispara la restauración.
  const onRestoreFile = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = ''; // permite volver a elegir el mismo archivo
    if (!file) return;
    try {
      const text = await file.text();
      const backup = JSON.parse(text);
      restoreBusiness.mutate(backup);
    } catch {
      toast.error('Archivo inválido', 'No se pudo leer el JSON de respaldo.');
    }
  };

  const selectedPlan = plans.data?.find((p) => String(p.id) === form.planId);
  const planName = (id: number | null) => plans.data?.find((p) => p.id === id)?.name ?? '—';
  const lineName = (code: string) => lines.data?.find((l) => l.code === code)?.name ?? code;

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
    if (!res.ok) {
      toast.error('No se pudo generar el respaldo');
      return;
    }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `respaldo-${b.name.replace(/\s+/g, '-').toLowerCase()}.json`;
    a.click();
    URL.revokeObjectURL(url);
    toast.success('Respaldo descargado', b.name);
  };

  // Métricas rápidas derivadas de la lista de negocios.
  const list = businesses.data ?? [];
  const metrics = [
    { label: 'Total', value: list.length, tone: '' },
    { label: 'En prueba', value: list.filter((b) => b.status === 'TRIAL').length, tone: 'metric-tone-warning' },
    { label: 'Activos', value: list.filter((b) => b.status === 'ACTIVE').length, tone: 'metric-tone-success' },
    {
      label: 'En riesgo',
      value: list.filter((b) => b.status === 'SUSPENDED' || b.status === 'EXPIRED').length,
      tone: 'metric-tone-danger',
    },
  ];

  return (
    <>
      {/* Métricas rápidas animadas con stagger. */}
      <motion.div className="metrics admin-metrics" variants={staggerContainer} initial="hidden" animate="visible">
        {metrics.map((m) => (
          <motion.div key={m.label} className={`metric-card ${m.tone}`} variants={staggerItem}>
            <div className="metric-label">{m.label}</div>
            <div className="metric-value">{m.value}</div>
          </motion.div>
        ))}
      </motion.div>

      {/* Restaurar empresa desde un respaldo JSON. */}
      <motion.div className="card restore-card" variants={fadeInUp} initial="hidden" animate="visible"
        style={{ marginTop: 'var(--space-5)' }}>
        <div className="restore-info">
          <div className="restore-icon" aria-hidden><UploadCloud size={22} /></div>
          <div>
            <h3>Restaurar empresa</h3>
            <p className="admin-plan-hint" style={{ marginTop: 2 }}>
              Sube un archivo de respaldo (.json) generado con el botón “Respaldo”. Se creará una
              empresa nueva con esos datos, sin afectar las existentes.
            </p>
          </div>
        </div>
        <label className="btn-ghost restore-btn">
          <UploadCloud size={16} />
          {restoreBusiness.isPending ? 'Restaurando…' : 'Elegir archivo de respaldo'}
          <input type="file" accept="application/json,.json" hidden
            onChange={onRestoreFile} disabled={restoreBusiness.isPending} />
        </label>
      </motion.div>

      {/* Formulario de alta dentro de una card animada. */}
      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible"
        style={{ marginTop: 'var(--space-5)' }}>
        <h3>Crear negocio</h3>
        <p className="admin-plan-hint" style={{ marginTop: 4 }}>
          Al crear el negocio se genera automáticamente el usuario Dueño con una contraseña
          temporal que verás una sola vez para entregarla al cliente.
        </p>
        <div className="admin-grid">
          <label className="field">
            <span>Nombre del negocio</span>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: titleCase(e.target.value) })}
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
            <input value={form.ownerName} onChange={(e) => setForm({ ...form, ownerName: titleCase(e.target.value) })}
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
            <input type="tel" value={form.ownerWhatsapp} inputMode="numeric" maxLength={10}
              onChange={(e) => setForm({ ...form, ownerWhatsapp: limitToTenDigits(e.target.value) })}
              placeholder="5512345678"
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
          <motion.button className="btn-accent" disabled={!canSubmit || createBusiness.isPending}
            onClick={() => createBusiness.mutate()}
            whileHover={!canSubmit || createBusiness.isPending ? undefined : pressable.whileHover}
            whileTap={!canSubmit || createBusiness.isPending ? undefined : pressable.whileTap}
            transition={pressable.transition}>
            {createBusiness.isPending ? 'Creando…' : 'Crear negocio y usuario dueño'}
          </motion.button>
          {createBusiness.isError && (
            <span className="admin-inline-error">No se pudo crear. Revisa el correo del dueño (¿ya existe?).</span>
          )}
        </div>
      </motion.div>

      {/* Negocios como tarjetas premium en rejilla responsiva. */}
      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Negocios ({list.length})</h3>

        {businesses.isLoading ? (
          <p className="admin-plan-hint">Cargando…</p>
        ) : list.length === 0 ? (
          <EmptyState icon={<Store size={40} />} text="Aún no hay negocios. Crea el primero arriba." />
        ) : (
          <motion.div className="biz-grid" variants={staggerContainer} initial="hidden" animate="visible">
            {list.map((b) => (
              <motion.article key={b.id} className="biz-card" variants={staggerItem} layout>
                <div className="biz-card-head">
                  <div className="biz-avatar" aria-hidden>{initials(b.name)}</div>
                  <div className="biz-card-titles">
                    <strong className="biz-name">{b.name}</strong>
                    <span className="biz-rfc">{b.rfc || 'Sin RFC'}</span>
                  </div>
                  <span className={`badge ${STATUS_BADGE[b.status] ?? 'badge-muted'}`}>
                    {STATUS_LABEL[b.status] ?? b.status}
                  </span>
                </div>

                <dl className="biz-meta">
                  <div><dt>Giro</dt><dd>{lineName(b.businessLine)}</dd></div>
                  <div><dt>Plan</dt><dd>{planName(b.planId)}</dd></div>
                  <div><dt>Prueba hasta</dt><dd>{fmtDate(b.trialEndsAt)}</dd></div>
                </dl>

                <div className="admin-actions biz-actions">
                  <motion.button className="btn-ghost" onClick={() => setDetailId(b.id)}
                    whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
                    <Eye size={15} /> Detalle
                  </motion.button>
                  {b.status !== 'ACTIVE' && (
                    <motion.button className="btn-primary" onClick={() => action.mutate({ id: b.id, verb: 'purchase' })}
                      whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
                      <BadgeDollarSign size={15} /> Vender licencia
                    </motion.button>
                  )}
                  {b.status === 'SUSPENDED' ? (
                    <motion.button className="btn-ghost" onClick={() => action.mutate({ id: b.id, verb: 'reactivate' })}
                      whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
                      <ShieldCheck size={15} /> Reactivar
                    </motion.button>
                  ) : (
                    <motion.button className="btn-ghost" onClick={() => action.mutate({ id: b.id, verb: 'suspend' })}
                      whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
                      <ShieldOff size={15} /> Suspender
                    </motion.button>
                  )}
                  <motion.button className="btn-ghost"
                    disabled={resetOwnerPassword.isPending}
                    onClick={() => resetOwnerPassword.mutate(b)}
                    whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
                    <KeyRound size={15} /> Regenerar contraseña
                  </motion.button>
                  <motion.button className="btn-ghost" disabled={seedDemo.isPending}
                    onClick={() => seedDemo.mutate(b)}
                    whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
                    <Database size={15} /> {seedDemo.isPending ? 'Sembrando…' : 'Cargar datos demo'}
                  </motion.button>
                  <motion.button className="btn-ghost" onClick={() => downloadBackup(b)}
                    whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
                    <DownloadCloud size={15} /> Respaldo
                  </motion.button>
                  <motion.button className="btn-danger-ghost" onClick={() => setConfirmDelete(b)}
                    whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
                    <Trash2 size={15} /> Eliminar
                  </motion.button>
                </div>
              </motion.article>
            ))}
          </motion.div>
        )}
      </div>

      <AnimatePresence>
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
        {resetResult && (
          <ResetPasswordModal data={resetResult} onClose={() => setResetResult(null)} />
        )}
      </AnimatePresence>
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
    toast.success('Credenciales copiadas');
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <ModalShell onClose={onClose} title="Negocio creado" wide={false}>
      <p className="admin-modal-lead">
        Guarda estas credenciales ahora. La contraseña <strong>no se volverá a mostrar</strong>.
        El dueño ingresa desde la pestaña “Mi negocio” del inicio de sesión y el sistema le
        pedirá cambiarla en su primer acceso.
      </p>

      {/* Contraseña temporal destacada como bloque premium. */}
      <div className="cred-hero">
        <div className="cred-hero-icon" aria-hidden><KeyRound size={20} /></div>
        <div className="cred-hero-body">
          <span className="cred-hero-label">Contraseña temporal</span>
          <code className="cred-hero-value">{data.owner.password}</code>
        </div>
      </div>

      <div className="cred-box">
        <div className="cred-row"><span>Negocio</span><strong>{data.business.name}</strong></div>
        <div className="cred-row"><span>Correo de acceso</span><code>{data.owner.email}</code></div>
        {data.owner.whatsapp && (
          <div className="cred-row"><span>WhatsApp</span><code>{data.owner.whatsapp}</code></div>
        )}
      </div>

      <div className="cred-delivery">
        <span className={`cred-delivery-chip ${data.emailSent ? 'is-ok' : 'is-off'}`}>
          <Mail size={13} /> {data.emailSent ? 'Enviado por correo' : 'Correo no enviado'}
        </span>
        <span className={`cred-delivery-chip ${data.whatsappSent ? 'is-ok' : 'is-off'}`}>
          <MessageCircle size={13} /> {data.whatsappSent ? 'Enviado por WhatsApp' : 'WhatsApp no enviado'}
        </span>
      </div>
      <p className="admin-plan-hint" style={{ marginTop: 8 }}>
        Si el cliente no recibe el mensaje, comparte tú estas credenciales. Quedan guardadas aquí
        solo hasta que cierres esta ventana.
      </p>

      <div className="admin-modal-actions">
        <motion.button className="btn-ghost" onClick={copy}
          whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
          {copied ? <><Check size={15} /> ¡Copiado!</> : <><Copy size={15} /> Copiar credenciales</>}
        </motion.button>
        <motion.button className="btn-primary" onClick={onClose}
          whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
          Entendido
        </motion.button>
      </div>
    </ModalShell>
  );
}

function ResetPasswordModal({ data, onClose }: { data: ResetOwnerPassword; onClose: () => void }) {
  const [copied, setCopied] = useState(false);
  const text = `Negocio: ${data.businessName}\nAcceso: ${data.email}\nContraseña temporal: ${data.password}`;
  const copy = async () => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    toast.success('Credenciales copiadas');
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <ModalShell onClose={onClose} title="Contraseña regenerada" wide={false}>
      <p className="admin-modal-lead">
        Se generó una nueva contraseña temporal para el dueño de <strong>{data.businessName}</strong>.
        Se reenvió por correo y WhatsApp; el dueño deberá <strong>cambiarla en su próximo ingreso</strong>.
        Guárdala también aquí por si necesitas entregarla tú.
      </p>

      <div className="cred-hero">
        <div className="cred-hero-icon" aria-hidden><KeyRound size={20} /></div>
        <div className="cred-hero-body">
          <span className="cred-hero-label">Nueva contraseña temporal</span>
          <code className="cred-hero-value">{data.password}</code>
        </div>
      </div>

      <div className="cred-box">
        <div className="cred-row"><span>Correo de acceso</span><code>{data.email}</code></div>
        {data.whatsapp && <div className="cred-row"><span>WhatsApp</span><code>{data.whatsapp}</code></div>}
      </div>

      <div className="cred-delivery">
        <span className={`cred-delivery-chip ${data.emailSent ? 'is-ok' : 'is-off'}`}>
          <Mail size={13} /> {data.emailSent ? 'Enviada por correo' : 'Correo no enviado'}
        </span>
        <span className={`cred-delivery-chip ${data.whatsappSent ? 'is-ok' : 'is-off'}`}>
          <MessageCircle size={13} /> {data.whatsappSent ? 'Enviada por WhatsApp' : 'WhatsApp no enviado'}
        </span>
      </div>

      <div className="admin-modal-actions">
        <motion.button className="btn-ghost" onClick={copy}
          whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
          {copied ? <><Check size={15} /> ¡Copiado!</> : <><Copy size={15} /> Copiar credenciales</>}
        </motion.button>
        <motion.button className="btn-primary" onClick={onClose}
          whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
          Entendido
        </motion.button>
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
        <motion.button className="btn-ghost" onClick={onCancel}
          whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
          Cancelar
        </motion.button>
        <motion.button className="btn-danger" disabled={text !== 'ELIMINAR' || pending} onClick={onConfirm}
          whileHover={text !== 'ELIMINAR' || pending ? undefined : pressable.whileHover}
          whileTap={text !== 'ELIMINAR' || pending ? undefined : pressable.whileTap}
          transition={pressable.transition}>
          {pending ? 'Eliminando…' : 'Eliminar definitivamente'}
        </motion.button>
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
      toast.success('Plan creado');
    },
    onError: () => toast.error('No se pudo crear el plan', '¿Código repetido?'),
  });

  const deactivate = useMutation({
    mutationFn: async (id: number) => api.post(`/admin/plans/${id}/deactivate`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'plans'] });
      toast.info('Plan desactivado');
    },
    onError: () => toast.error('No se pudo desactivar el plan'),
  });

  const toggleModule = (key: string) =>
    setForm((f) => ({
      ...f,
      modules: f.modules.includes(key) ? f.modules.filter((k) => k !== key) : [...f.modules, key],
    }));

  const canSubmit = form.code && form.name && form.licensePrice && form.modules.length > 0;

  return (
    <>
      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
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
            {(modules.data ?? []).map((m) => {
              const on = form.modules.includes(m.moduleKey);
              return (
                <motion.label key={m.moduleKey} className={`module-chip ${on ? 'is-on' : ''}`}
                  title={m.description ?? ''}
                  animate={{ scale: on ? 1.05 : 1 }} transition={quick}
                  whileTap={{ scale: 0.95 }}>
                  <input type="checkbox" checked={on}
                    onChange={() => toggleModule(m.moduleKey)} />
                  {m.name}
                </motion.label>
              );
            })}
          </div>
        </div>
        <div style={{ marginTop: 'var(--space-4)' }}>
          <motion.button className="btn-accent" disabled={!canSubmit || createPlan.isPending}
            onClick={() => createPlan.mutate()}
            whileHover={!canSubmit || createPlan.isPending ? undefined : pressable.whileHover}
            whileTap={!canSubmit || createPlan.isPending ? undefined : pressable.whileTap}
            transition={pressable.transition}>
            {createPlan.isPending ? 'Creando…' : 'Crear plan'}
          </motion.button>
        </div>
      </motion.div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Planes ({plans.data?.length ?? 0})</h3>
        <table className="table">
          <thead><tr><th>Plan</th><th>Precio</th><th>Módulos</th><th>Estado</th><th>Acciones</th></tr></thead>
          <motion.tbody variants={staggerContainer} initial="hidden" animate="visible">
            {(plans.data ?? []).map((p) => (
              <motion.tr key={p.id} variants={staggerItem}>
                <td><strong>{p.name}</strong><div className="admin-sub">{p.code}</div></td>
                <td>{money(p.licensePriceSuggested)}</td>
                <td>{p.moduleKeys.length}</td>
                <td><span className={`badge ${p.active ? 'badge-success' : 'badge-muted'}`}>
                  {p.active ? 'Activo' : 'Inactivo'}</span></td>
                <td className="admin-actions">
                  {p.active && (
                    <motion.button className="btn-ghost" onClick={() => deactivate.mutate(p.id)}
                      whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
                      Desactivar
                    </motion.button>
                  )}
                </td>
              </motion.tr>
            ))}
          </motion.tbody>
        </table>
        {plans.data?.length === 0 && (
          <EmptyState icon={<Layers size={40} />} text="Aún no hay planes. Crea el primero arriba." />
        )}
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
      toast.success('Giro creado');
    },
    onError: () => toast.error('No se pudo crear el giro', '¿Código repetido?'),
  });

  const removeLine = useMutation({
    mutationFn: async (code: string) => api.delete(`/admin/business-lines/${code}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'lines'] });
      toast.success('Giro eliminado');
    },
    onError: () => toast.error('No se pudo eliminar', 'El giro está en uso por algún negocio.'),
  });

  return (
    <>
      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <h3>Crear giro</h3>
        <p className="admin-plan-hint" style={{ marginTop: 4 }}>
          Un giro define el tipo de negocio (abarrotes, panadería…). El código se genera del nombre
          si lo dejas vacío.
        </p>
        <div className="admin-grid">
          <label className="field">
            <span>Nombre</span>
            <input value={form.name} onChange={(e) => setForm({ ...form, name: titleCase(e.target.value) })}
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
          <motion.button className="btn-accent" disabled={!form.name || createLine.isPending}
            onClick={() => createLine.mutate()}
            whileHover={!form.name || createLine.isPending ? undefined : pressable.whileHover}
            whileTap={!form.name || createLine.isPending ? undefined : pressable.whileTap}
            transition={pressable.transition}>
            {createLine.isPending ? 'Creando…' : 'Crear giro'}
          </motion.button>
          {createLine.isError && (
            <span className="admin-inline-error">No se pudo crear (¿código repetido?).</span>
          )}
        </div>
      </motion.div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Giros ({lines.data?.length ?? 0})</h3>
        <table className="table">
          <thead><tr><th>Giro</th><th>Código</th><th>Descripción</th><th>Acciones</th></tr></thead>
          <motion.tbody variants={staggerContainer} initial="hidden" animate="visible">
            {(lines.data ?? []).map((l) => (
              <motion.tr key={l.code} variants={staggerItem}>
                <td><strong>{l.name}</strong></td>
                <td><code>{l.code}</code></td>
                <td>{l.description ?? '—'}</td>
                <td className="admin-actions">
                  <motion.button className="btn-danger-ghost" onClick={() => removeLine.mutate(l.code)}
                    whileHover={pressable.whileHover} whileTap={pressable.whileTap} transition={pressable.transition}>
                    <Trash2 size={15} /> Eliminar
                  </motion.button>
                </td>
              </motion.tr>
            ))}
          </motion.tbody>
        </table>
        {lines.data?.length === 0 && (
          <EmptyState icon={<PackageOpen size={40} />} text="Aún no hay giros. Crea el primero arriba." />
        )}
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
    <motion.div className="modal-overlay" onClick={onClose}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={quick}>
      <motion.div className={`modal-card ${wide ? 'modal-wide' : ''}`} onClick={(e) => e.stopPropagation()}
        variants={popIn} initial="hidden" animate="visible" exit="exit">
        <div className="modal-head">
          <h3>{title}</h3>
          <button className="modal-close" onClick={onClose} aria-label="Cerrar">×</button>
        </div>
        <div className="modal-body">{children}</div>
      </motion.div>
    </motion.div>
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

/** Estado vacío con gracia: icono lucide + texto centrado. */
function EmptyState({ icon, text }: { icon: React.ReactNode; text: string }) {
  return (
    <motion.div className="admin-empty" variants={fadeInUp} initial="hidden" animate="visible">
      <div className="admin-empty-icon" aria-hidden>{icon}</div>
      <p className="admin-empty-text">{text}</p>
    </motion.div>
  );
}
