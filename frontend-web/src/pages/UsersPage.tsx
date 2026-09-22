import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { isValidEmail, isValidMxPhone, normalizeMxPhone } from '@/lib/validation';
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
    },
  });

  const toggleActive = useMutation({
    mutationFn: async ({ id, active }: { id: number; active: boolean }) =>
      api.post(`/users/${id}/${active ? 'disable' : 'enable'}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['tenant', 'users'] }),
  });

  const resetPassword = useMutation({
    mutationFn: async (u: TenantUser) =>
      ({ name: u.fullName, data: (await api.post<{ password: string }>(`/users/${u.id}/reset-password`)).data }),
    onSuccess: ({ name, data }) => {
      setResetInfo({ name, password: data.password });
      queryClient.invalidateQueries({ queryKey: ['tenant', 'users'] });
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

      <div className="card">
        <h3>Agregar usuario</h3>
        <p className="admin-plan-hint" style={{ marginTop: 4 }}>
          Se genera una contraseña temporal que verás una sola vez y, si capturas WhatsApp, se le
          envía. El usuario deberá cambiarla en su primer ingreso.
        </p>
        <div className="admin-grid">
          <label className="field">
            <span>Nombre completo</span>
            <input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })}
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
            <input type="tel" value={form.whatsapp}
              onChange={(e) => setForm({ ...form, whatsapp: e.target.value })}
              placeholder="55 1234 5678"
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
          <button className="btn-accent" disabled={!canSubmit || createUser.isPending}
            onClick={() => createUser.mutate()}>
            {createUser.isPending ? 'Creando…' : 'Agregar usuario'}
          </button>
          {createUser.isError && (
            <span className="admin-inline-error">No se pudo crear (¿correo repetido?).</span>
          )}
        </div>
      </div>

      <div className="card" style={{ marginTop: 'var(--space-5)' }}>
        <h3>Usuarios ({users.data?.length ?? 0})</h3>
        <table className="table">
          <thead>
            <tr><th>Nombre</th><th>Correo</th><th>Rol</th><th>Estado</th><th>Acciones</th></tr>
          </thead>
          <tbody>
            {(users.data ?? []).map((u) => (
              <tr key={u.id}>
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
              </tr>
            ))}
            {users.data?.length === 0 && (
              <tr><td colSpan={5} className="empty">Aún no hay usuarios. Agrega el primero arriba.</td></tr>
            )}
          </tbody>
        </table>
      </div>

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
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
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
                {whatsappSent ? '✓ Enviada por WhatsApp' : '• No se envió por WhatsApp'}
              </span>
            </div>
          )}
          <div className="admin-modal-actions">
            <button className="btn-ghost" onClick={copy}>{copied ? '¡Copiado!' : 'Copiar'}</button>
            <button className="btn-primary" onClick={onClose}>Entendido</button>
          </div>
        </div>
      </div>
    </div>
  );
}
