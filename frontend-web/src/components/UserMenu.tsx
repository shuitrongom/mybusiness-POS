import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'motion/react';
import { User, KeyRound, LogOut, X, ChevronDown, ShieldCheck, Camera } from 'lucide-react';
import { api } from '@/lib/api';
import { useSession } from '@/store/session';
import { decodeToken } from '@/lib/jwt';
import { toast } from '@/store/toast';
import { fileToThumbnailDataUrl } from '@/lib/image';
import './user-menu.css';

/** Clave de la foto de perfil en localStorage (por usuario). */
const AVATAR_KEY = 'mbs.avatar';
function loadAvatar(email: string): string | null {
  try { return localStorage.getItem(`${AVATAR_KEY}:${email}`); } catch { return null; }
}
function saveAvatar(email: string, dataUrl: string | null) {
  try {
    if (dataUrl) localStorage.setItem(`${AVATAR_KEY}:${email}`, dataUrl);
    else localStorage.removeItem(`${AVATAR_KEY}:${email}`);
  } catch { /* almacenamiento no disponible */ }
}

function initials(text: string): string {
  // Toma la parte antes de @ (si es correo) y separa por espacios/._- para sacar iniciales.
  const name = (text || '').split('@')[0].replace(/[._-]+/g, ' ').trim();
  const w = name.split(/\s+/).filter(Boolean);
  if (w.length === 0) return '?';
  return (w.length === 1 ? w[0].slice(0, 2) : w[0][0] + w[1][0]).toUpperCase();
}

function roleLabel(roles: string[]): string {
  if (roles.includes('OWNER')) return 'Dueño';
  if (roles.includes('ADMIN')) return 'Administrador';
  if (roles.includes('SUPER_ADMIN')) return 'Super Admin';
  if (roles.includes('SUPERVISOR')) return 'Supervisor';
  if (roles.includes('CASHIER')) return 'Cajero';
  return 'Usuario';
}

/**
 * Menú de usuario premium (esquina superior derecha): avatar con iniciales, nombre y rol, y un
 * desplegable con "Mi perfil", "Cambiar contraseña" y "Cerrar sesión". Patrón usado por los
 * sistemas modernos. Incluye el modal de cambio de contraseña.
 */
export function UserMenu() {
  const navigate = useNavigate();
  const token = useSession((s) => s.token);
  const logout = useSession((s) => s.logout);
  const claims = decodeToken(token);
  const email = claims?.subject ?? 'usuario';

  const [open, setOpen] = useState(false);
  const [view, setView] = useState<'profile' | 'password' | null>(null);
  const [avatar, setAvatar] = useState<string | null>(() => loadAvatar(email));
  // Nombre real del usuario (viene de /me); mientras carga, usa el del correo como respaldo.
  const [fullName, setFullName] = useState<string>(email.split('@')[0]);
  const [role, setRole] = useState<string>(roleLabel(claims?.roles ?? []));
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    api.get<{ fullName: string; role: string }>('/me')
      .then((r) => {
        if (r.data?.fullName) setFullName(r.data.fullName);
        if (r.data?.role) setRole(r.data.role);
      })
      .catch(() => { /* si falla, se queda con el respaldo */ });
  }, []);

  // Cierra el desplegable al hacer clic fuera.
  useEffect(() => {
    const onClick = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="usermenu" ref={ref}>
      <button className="usermenu-trigger" onClick={() => setOpen((o) => !o)}>
        <span className="usermenu-avatar">
          {avatar ? <img src={avatar} alt="Perfil" className="usermenu-avatar-img" /> : initials(fullName)}
        </span>
        <span className="usermenu-info">
          <span className="usermenu-name">{fullName}</span>
          <span className="usermenu-role">{role}</span>
        </span>
        <ChevronDown size={16} className="usermenu-caret" />
      </button>

      <AnimatePresence>
        {open && (
          <motion.div className="usermenu-drop"
            initial={{ opacity: 0, y: -8, scale: 0.97 }} animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8, scale: 0.97 }} transition={{ duration: 0.15 }}>
            <div className="usermenu-drop-head">
              <span className="usermenu-avatar usermenu-avatar-lg">{initials(email)}</span>
              <div>
                <div className="usermenu-drop-name">{email}</div>
                <div className="usermenu-drop-role"><ShieldCheck size={13} /> {role}</div>
              </div>
            </div>
            <button className="usermenu-item" onClick={() => { setOpen(false); setView('profile'); }}>
              <User size={17} /> Mi perfil
            </button>
            <button className="usermenu-item" onClick={() => { setOpen(false); setView('password'); }}>
              <KeyRound size={17} /> Cambiar contraseña
            </button>
            <div className="usermenu-sep" />
            <button className="usermenu-item usermenu-item-danger" onClick={handleLogout}>
              <LogOut size={17} /> Cerrar sesión
            </button>
          </motion.div>
        )}
      </AnimatePresence>

      <AnimatePresence>
        {view === 'profile' && (
          <ProfileModal email={email} fullName={fullName} role={role} avatar={avatar}
            onAvatarChange={(url) => { setAvatar(url); saveAvatar(email, url); }}
            onClose={() => setView(null)}
            onChangePassword={() => setView('password')} />
        )}
        {view === 'password' && (
          <PasswordModal email={email} onClose={() => setView(null)} />
        )}
      </AnimatePresence>
    </div>
  );
}

function ProfileModal({ email, fullName, role, avatar, onAvatarChange, onClose, onChangePassword }: {
  email: string; fullName: string; role: string; avatar: string | null;
  onAvatarChange: (url: string | null) => void;
  onClose: () => void; onChangePassword: () => void;
}) {
  const fileRef = useRef<HTMLInputElement>(null);

  const onPick = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    try {
      onAvatarChange(await fileToThumbnailDataUrl(file));
      toast.success('Foto actualizada', 'Tu foto de perfil se guardó.');
    } catch {
      toast.error('No se pudo procesar la foto', 'Prueba con otra imagen.');
    }
  };

  return createPortal(
    <motion.div className="um-overlay" onClick={onClose}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
      <motion.div className="um-modal um-modal-profile" onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95, y: 14 }} animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.97 }} transition={{ type: 'spring', stiffness: 320, damping: 28 }}>
        {/* Banner con degradado */}
        <div className="um-banner">
          <button className="um-close um-close-banner" onClick={onClose}><X size={18} /></button>
          <div className="um-banner-glow" />
        </div>
        <div className="um-profile-top">
          <div className="um-avatar-wrap">
            <span className="usermenu-avatar usermenu-avatar-hero">
              {avatar ? <img src={avatar} alt="Perfil" className="usermenu-avatar-img" /> : initials(fullName)}
            </span>
            <button className="um-avatar-cam" title="Subir foto" onClick={() => fileRef.current?.click()}>
              <Camera size={15} />
            </button>
            <input ref={fileRef} type="file" accept="image/*" hidden onChange={onPick} />
          </div>
          {avatar && (
            <button className="um-avatar-remove" onClick={() => { onAvatarChange(null); }}>
              Quitar foto
            </button>
          )}
          <div className="um-profile-name">{fullName}</div>
          <div className="um-profile-badge"><ShieldCheck size={14} /> {role}</div>
        </div>

        <div className="um-profile-cards">
          <div className="um-card">
            <span className="um-card-icon um-card-blue"><User size={18} /></span>
            <div className="um-card-body">
              <span className="um-card-label">Correo</span>
              <span className="um-card-value">{email}</span>
            </div>
          </div>
          <div className="um-card">
            <span className="um-card-icon um-card-green"><ShieldCheck size={18} /></span>
            <div className="um-card-body">
              <span className="um-card-label">Rol en el negocio</span>
              <span className="um-card-value">{role}</span>
            </div>
          </div>
          <button className="um-card um-card-action" onClick={onChangePassword}>
            <span className="um-card-icon um-card-amber"><KeyRound size={18} /></span>
            <div className="um-card-body">
              <span className="um-card-label">Seguridad</span>
              <span className="um-card-value">Cambiar mi contraseña</span>
            </div>
            <ChevronDown size={18} className="um-card-caret" />
          </button>
        </div>

        <div className="um-modal-foot">
          <button className="btn-ghost" onClick={onClose}>Cerrar</button>
          <button className="btn-accent" onClick={onChangePassword}>
            <KeyRound size={16} /> Cambiar contraseña
          </button>
        </div>
      </motion.div>
    </motion.div>,
    document.body,
  );
}

function PasswordModal({ email, onClose }: { email: string; onClose: () => void }) {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [saving, setSaving] = useState(false);
  const login = useSession((s) => s.login);

  const valid = current && next.length >= 8 && next === confirm;

  const submit = async () => {
    if (!valid) return;
    setSaving(true);
    try {
      const { data } = await api.post<{ accessToken: string; refreshToken?: string }>(
        '/auth/change-password', { email, currentPassword: current, newPassword: next });
      if (data?.accessToken) login(data.accessToken, data.refreshToken);
      toast.success('Contraseña actualizada', 'Tu nueva contraseña ya está activa.');
      onClose();
    } catch {
      toast.error('No se pudo cambiar la contraseña', 'Verifica tu contraseña actual.');
    } finally {
      setSaving(false);
    }
  };

  return createPortal(
    <motion.div className="um-overlay" onClick={onClose}
      initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}>
      <motion.div className="um-modal" onClick={(e) => e.stopPropagation()}
        initial={{ opacity: 0, scale: 0.95, y: 12 }} animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.97 }}>
        <div className="um-modal-head">
          <h3>Cambiar contraseña</h3>
          <button className="um-close" onClick={onClose}><X size={18} /></button>
        </div>
        <div className="um-form">
          <label className="field"><span>Contraseña actual</span>
            <input type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoFocus /></label>
          <label className="field"><span>Nueva contraseña</span>
            <input type="password" value={next} onChange={(e) => setNext(e.target.value)}
              placeholder="Mínimo 8 caracteres" /></label>
          <label className="field"><span>Confirmar nueva contraseña</span>
            <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} /></label>
          {next && next.length < 8 && <small className="um-hint-err">Debe tener al menos 8 caracteres.</small>}
          {confirm && next !== confirm && <small className="um-hint-err">Las contraseñas no coinciden.</small>}
        </div>
        <div className="um-modal-foot">
          <button className="btn-ghost" onClick={onClose}>Cancelar</button>
          <button className="btn-accent" disabled={!valid || saving} onClick={submit}>
            {saving ? 'Guardando…' : 'Guardar contraseña'}
          </button>
        </div>
      </motion.div>
    </motion.div>,
    document.body,
  );
}
