import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AnimatePresence, motion } from 'motion/react';
import { Check, Users, UserPlus, X } from 'lucide-react';
import { api } from '@/lib/api';
import { toast } from '@/store/toast';
import { isValidEmail, isValidMxPhone, normalizeMxPhone, titleCase, limitToTenDigits } from '@/lib/validation';
import { fadeInUp, popIn, pressable, quick, staggerContainer, staggerItem } from '@/lib/motion';
import '@/pages/dashboard.css';
import './admin.css';

interface TenantUser {
  id: number;
  email: string;
  fullName: string;
  active: boolean;
  whatsapp: string | null;
  mustChangePassword: boolean;
  roleCode: string | null;
  roleName: string | null;
}

interface Role {
  id: number;
  code: string;
  name: string;
  systemRole: boolean;
}

interface CreatedUser {
  email: string;
  password: string;
  whatsappSent: boolean;
}

const ROLE_LABEL: Record<string, string> = {
  OWNER: 'Dueño',
  ADMIN: 'Administrador',
  SUPERVISOR: 'Supervisor',
  CASHIER: 'Cajero',
};

/**
 * Gestión de usuarios del negocio (cajeros, supervisores, administradores) por el Dueño/Admin.
 * Permite dar de alta usuarios con rol, ver sus credenciales temporales, activar/desactivar y
 * restablecer la contraseña.
 */
export function UsersPage() {
  const queryClient = useQueryClient();
  const [form, setForm] = useState({ fullName: '', email: '', whatsapp: '', roleCode: 'CASHIER' });
  const [credentials, setCredentials] = useState<CreatedUser | null>(null);
  const [resetInfo, setResetInfo] = useState<{ name: string; password: string } | null>(null);

  const users = useQuery({
    queryKey: ['tenant', 'users'],
    queryFn: async () => (await api.get<TenantUser[]>('/users')).data,
  });
  const roles = useQuery({
    queryKey: ['tenant', 'roles'],
    queryFn: async () => (await api.get<Role[]>('/roles')).data,
  });

  const createUser = useMutation({
    mutationFn: async () =>
      (await api.post<CreatedUser>('/users', {
        email: form.email.trim(),
        fullName: form.fullName.trim(),
        whatsapp: form.whatsapp ? normalizeMxPhone(form.whatsapp) : null,
        roleCode: form.roleCode,
      })).data,
    onSuccess: (data) => {
      setCredentials(data);
      setForm({ fullName: '', email: '', whatsapp: '', roleCode: 'CASHIER' });
      queryClient.invalidateQueries({ queryKey: ['tenant', 'users'] });
      toast.success('Usuario creado', 'Guarda la contraseña temporal que se muestra.');
    },
    onError: () => {
      toast.error('No se pudo crear el usuario', 'Verifica que el correo no esté repetido.');
    },
  });

  const toggleActive = useMutation({
    mutationFn: async ({ id, active }: { id: number; active: boolean }) =>
      api.post(`/users/${id}/${active ? 'disable' : 'enable'}`),
    onSuccess: (_data, vars) => {
      queryClient.invalidateQueries({ queryKey: ['tenant', 'users'] });
      toast.success(vars.active ? 'Usuario desactivado' : 'Usuario activado');
    },
    onError: () => {
      toast.error('No se pudo cambiar el estado del usuario', 'Inténtalo de nuevo.');
    },
  });

  const resetPassword = useMutation({
    mutationFn: async (u: TenantUser) =>
      ({ name: u.fullName, data: (await api.post<{ password: string }>(`/users/${u.id}/reset-password`)).data }),
    onSuccess: ({ name, data }) => {
      setResetInfo({ name, password: data.password });
      queryClient.invalidateQueries({ queryKey: ['tenant', 'users'] });
      toast.success('Contraseña restablecida', 'Comparte la nueva contraseña temporal con el usuario.');
    },
    onError: () => {
      toast.error('No se pudo restablecer la contraseña', 'Inténtalo de nuevo.');
    },
  });

  const emailError = form.email && !isValidEmail(form.email) ? 'Correo inválido.' : '';
  const whatsappError = form.whatsapp && !isValidMxPhone(form.whatsapp)
    ? 'Debe tener 10 dígitos (lada de México).' : '';
  const canSubmit = form.fullName.trim() && form.email.trim() && !emailError && !whatsappError;

  // Roles asignables: no permitimos crear otro Dueño desde aquí.
  const assignableRoles = (roles.data ?? []).filter((r) => r.code !== 'OWNER');

  return (
    <div>
      <h1 className="page-title">Usuarios</h1>
      <p className="page-sub">Da de alta cajeros y personal, y controla su acceso</p>

      <motion.div className="card" variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><UserPlus size={18} /> Agregar usuario</h3>
        <p className="admin-plan-hint" style={{ marginTop: 4 }}>
          Se genera una contraseña temporal que verás una sola vez y, si capturas WhatsApp, se le
          envía. El usuario deberá cambiarla en su primer ingreso.
        </p>
        <div className="admin-grid">
          <label className="field">
            <span>Nombre completo</span>
            <input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: titleCase(e.target.value) })}
              placeholder="María López" />
          </label>
          <label className="field">
            <span>Correo (acceso)</span>
            <input type="email" value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              placeholder="cajero@negocio.com"
              className={emailError ? 'input-error' : ''} />
            {emailError && <small className="field-error">{emailError}</small>}
          </label>
          <label className="field">
            <span>WhatsApp (10 dígitos)</span>
            <input type="tel" value={form.whatsapp} inputMode="numeric" maxLength={10}
              onChange={(e) => setForm({ ...form, whatsapp: limitToTenDigits(e.target.value) })}
              placeholder="5512345678"
              className={whatsappError ? 'input-error' : ''} />
            {whatsappError && <small className="field-error">{whatsappError}</small>}
          </label>
          <label className="field">
            <span>Rol</span>
            <select value={form.roleCode} onChange={(e) => setForm({ ...form, roleCode: e.target.value })}>
              {assignableRoles.map((r) => (
                <option key={r.code} value={r.code}>{ROLE_LABEL[r.code] ?? r.name}</option>
              ))}
            </select>
          </label>
        </div>
        <div style={{ marginTop: 'var(--space-4)' }}>
          <motion.button className="btn-accent" disabled={!canSubmit || createUser.isPending}
            onClick={() => createUser.mutate()}
            whileHover={!canSubmit || createUser.isPending ? undefined : pressable.whileHover}
            whileTap={!canSubmit || createUser.isPending ? undefined : pressable.whileTap}
            transition={pressable.transition}>
            {createUser.isPending ? 'Creando…' : 'Agregar usuario'}
          </motion.button>
          {createUser.isError && (
            <span className="admin-inline-error">No se pudo crear (¿correo repetido?).</span>
          )}
        </div>
      </motion.div>

      <motion.div className="card" style={{ marginTop: 'var(--space-5)' }} variants={fadeInUp} initial="hidden" animate="visible">
        <h3 className="sec-title"><Users size={18} /> Usuarios ({users.data?.length ?? 0})</h3>
        <table className="table">
          <thead>
            <tr><th>Nombre</th><th>Correo</th><th>Rol</th><th>Estado</th><th>Acciones</th></tr>
          </thead>
          <motion.tbody variants={staggerContainer} initial="hidden" animate="visible">
            {(users.data ?? []).map((u) => (
              <motion.tr key={u.id} variants={staggerItem}>
                <td><strong>{u.fullName}</strong></td>
                <td>{u.email}{u.whatsapp && <div className="admin-sub">{u.whatsapp}</div>}</td>
                <td>{u.roleCode ? (ROLE_LABEL[u.roleCode] ?? u.roleName) : '—'}</td>
                <td>
                  <span className={`badge ${u.active ? 'badge-success' : 'badge-muted'}`}>
                    {u.active ? 'Activo' : 'Inactivo'}
                  </span>
                  {u.mustChangePassword && (
                    <span className="badge badge-warning" style={{ marginLeft: 6 }}>Cambio pendiente</span>
                  )}
                </td>
                <td className="admin-actions">
                  <button className="btn-ghost" onClick={() => resetPassword.mutate(u)}>
                    Restablecer contraseña
                  </button>
                  {u.roleCode !== 'OWNER' && (
                    <button className={u.active ? 'btn-danger-ghost' : 'btn-ghost'}
                      onClick={() => toggleActive.mutate({ id: u.id, active: u.active })}>
                      {u.active ? 'Desactivar' : 'Activar'}
                    </button>
                  )}
                </td>
              </motion.tr>
            ))}
            {users.data?.length === 0 && (
              <tr><td colSpan={5} className="empty">Aún no hay usuarios. Agrega el primero arriba.</td></tr>
            )}
          </motion.tbody>
        </table>
      </motion.div>

      {/* Modales de credenciales animados (fade en overlay + popIn en la tarjeta). */}
      <AnimatePresence>
        {credentials && (
          <CredentialsModal
            title="Usuario creado"
            email={credentials.email}
            password={credentials.password}
            whatsappSent={credentials.whatsappSent}
            onClose={() => setCredentials(null)}
          />
        )}
        {resetInfo && (
          <CredentialsModal
            title={`Contraseña restablecida — ${resetInfo.name}`}
            password={resetInfo.password}
            onClose={() => setResetInfo(null)}
          />
        )}
      </AnimatePresence>
    </div>
  );
}

function CredentialsModal({ title, email, password, whatsappSent, onClose }:
  { title: string; email?: string; password: string; whatsappSent?: boolean; onClose: () => void }) {
  const [copied, setCopied] = useState(false);
  const text = email
    ? `Acceso: ${email}\nContraseña temporal: ${password}`
    : `Contraseña temporal: ${password}`;
  const copy = async () => {
    await navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <motion.div className="modal-overlay" onClick={onClose}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={quick}>
      <motion.div className="modal-card" onClick={(e) => e.stopPropagation()}
        variants={popIn} initial="hidden" animate="visible" exit="exit">
        <div className="modal-head">
          <h3>{title}</h3>
          <button className="modal-close" onClick={onClose} aria-label="Cerrar">×</button>
        </div>
        <div className="modal-body">
          <p className="admin-modal-lead">
            Guarda esta contraseña ahora. <strong>No se volverá a mostrar</strong>. El usuario
            deberá cambiarla en su primer ingreso.
          </p>
          <div className="cred-box">
            {email && <div className="cred-row"><span>Correo</span><code>{email}</code></div>}
            <div className="cred-row"><span>Contraseña temporal</span><code className="cred-pass">{password}</code></div>
          </div>
          {whatsappSent !== undefined && (
            <div className="cred-delivery">
              <span className={`cred-delivery-chip ${whatsappSent ? 'is-ok' : 'is-off'}`}>
                {/* Emojis reemplazados por iconos lucide para consistencia visual. */}
                {whatsappSent
                  ? <><Check size={14} /> Enviada por WhatsApp</>
                  : <><X size={14} /> No se envió por WhatsApp</>}
              </span>
            </div>
          )}
          <div className="admin-modal-actions">
            <button className="btn-ghost" onClick={copy}>{copied ? '¡Copiado!' : 'Copiar'}</button>
            <button className="btn-primary" onClick={onClose}>Entendido</button>
          </div>
        </div>
      </motion.div>
    </motion.div>
  );
}
