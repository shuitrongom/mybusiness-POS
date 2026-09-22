import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '@/lib/api';
import { useSession } from '@/store/session';
import './login.css';

type Mode = 'business' | 'system';

/**
 * Inicio de sesión enterprise. Un único lienzo centrado, sobrio y corporativo, con dos modos:
 *
 * - "Mi negocio": acceso de los usuarios del negocio (dueño, cajero…) vía {@code /auth/login}.
 * - "Administrador del sistema": acceso del Super Admin (proveedor del SaaS) vía
 *   {@code /auth/superadmin/login}, con soporte de doble factor.
 *
 * El diseño evita el estilo "split-screen" previo en favor de una tarjeta de acceso limpia
 * sobre un fondo institucional discreto.
 */
export function LoginPage() {
  const [mode, setMode] = useState<Mode>('business');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [mfaCode, setMfaCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  // Flujo de cambio de contraseña obligatorio en el primer ingreso del dueño.
  const [mustChange, setMustChange] = useState(false);
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const login = useSession((s) => s.login);
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const endpoint = mode === 'system' ? '/auth/superadmin/login' : '/auth/login';
      const payload =
        mode === 'system'
          ? { email, password, mfaCode: mfaCode || undefined }
          : { email, password };
      const { data } = await api.post(endpoint, payload);
      if (data.accessToken) {
        if (data.mustChangePassword) {
          // No inicia sesión aún: primero debe establecer una contraseña nueva.
          setMustChange(true);
        } else {
          login(data.accessToken);
          navigate('/', { replace: true });
        }
      } else {
        setError('Credenciales incorrectas. Verifica tu correo y contraseña.');
      }
    } catch {
      setError('No se pudo iniciar sesión. Revisa tus datos e inténtalo de nuevo.');
    } finally {
      setLoading(false);
    }
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (newPassword.length < 8) {
      setError('La nueva contraseña debe tener al menos 8 caracteres.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('Las contraseñas no coinciden.');
      return;
    }
    setLoading(true);
    try {
      const { data } = await api.post('/auth/change-password', {
        email,
        currentPassword: password,
        newPassword,
      });
      if (data.accessToken) {
        login(data.accessToken);
        navigate('/', { replace: true });
      } else {
        setError('No se pudo cambiar la contraseña. Inténtalo de nuevo.');
      }
    } catch {
      setError('No se pudo cambiar la contraseña. Verifica tu contraseña actual.');
    } finally {
      setLoading(false);
    }
  };

  const switchMode = (next: Mode) => {
    setMode(next);
    setError(null);
    setMfaCode('');
  };

  return (
    <div className="auth-page">
      {/* Cabecera de marca institucional */}
      <header className="auth-topbar">
        <div className="auth-brand">
          <div className="auth-mark">MS</div>
          <div className="auth-brand-text">
            <div className="auth-brand-name">MyBusiness Silva</div>
            <div className="auth-brand-tag">CLOUD POS · MÉXICO</div>
          </div>
        </div>
      </header>

      <main className="auth-main">
        <section className="auth-card" aria-label="Inicio de sesión">
          {mustChange ? (
            <>
              <div className="auth-card-head">
                <h1 className="auth-title">Cambia tu contraseña</h1>
                <p className="auth-subtitle">
                  Por tu seguridad, establece una contraseña nueva antes de entrar por primera vez.
                </p>
              </div>

              <form className="auth-form" onSubmit={handleChangePassword}>
                <label className="field">
                  <span>Nueva contraseña</span>
                  <input
                    type="password"
                    value={newPassword}
                    onChange={(e) => setNewPassword(e.target.value)}
                    placeholder="Mínimo 8 caracteres"
                    autoComplete="new-password"
                    required
                    autoFocus
                  />
                </label>

                <label className="field">
                  <span>Confirma la contraseña</span>
                  <input
                    type="password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="Repite la contraseña"
                    autoComplete="new-password"
                    required
                  />
                </label>

                {error && (
                  <div className="auth-error" role="alert">
                    {error}
                  </div>
                )}

                <button type="submit" className="btn-primary auth-submit" disabled={loading}>
                  {loading ? 'Guardando…' : 'Guardar y entrar'}
                </button>
              </form>
            </>
          ) : (
            <>
              <div className="auth-card-head">
                <h1 className="auth-title">Inicia sesión</h1>
                <p className="auth-subtitle">
                  {mode === 'business'
                    ? 'Accede a la operación de tu negocio'
                    : 'Consola de administración de la plataforma'}
                </p>
              </div>

              {/* Selector de tipo de acceso */}
              <div className="auth-segment" role="tablist" aria-label="Tipo de acceso">
                <button
                  type="button"
                  role="tab"
                  aria-selected={mode === 'business'}
                  className={`auth-segment-btn ${mode === 'business' ? 'is-active' : ''}`}
                  onClick={() => switchMode('business')}
                >
                  Mi negocio
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected={mode === 'system'}
                  className={`auth-segment-btn ${mode === 'system' ? 'is-active' : ''}`}
                  onClick={() => switchMode('system')}
                >
                  Administrador
                </button>
              </div>

              <form className="auth-form" onSubmit={handleSubmit}>
                <label className="field">
                  <span>Correo electrónico</span>
                  <input
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="tu@correo.com"
                    autoComplete="username"
                    required
                    autoFocus
                  />
                </label>

                <label className="field">
                  <span>Contraseña</span>
                  <input
                    type="password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="••••••••"
                    autoComplete="current-password"
                    required
                  />
                </label>

                {mode === 'system' && (
                  <label className="field">
                    <span>Código de doble factor</span>
                    <input
                      type="text"
                      value={mfaCode}
                      onChange={(e) => setMfaCode(e.target.value)}
                      placeholder="Solo si tienes MFA activo"
                      inputMode="numeric"
                      autoComplete="one-time-code"
                    />
                  </label>
                )}

                {error && (
                  <div className="auth-error" role="alert">
                    {error}
                  </div>
                )}

                <button type="submit" className="btn-primary auth-submit" disabled={loading}>
                  {loading ? 'Verificando…' : 'Entrar'}
                </button>
              </form>

              <div className="auth-trust">
                <span className="auth-trust-item">🔒 Cifrado de extremo a extremo</span>
                <span className="auth-trust-dot" aria-hidden="true">·</span>
                <span className="auth-trust-item">🧾 CFDI 4.0</span>
                <span className="auth-trust-dot" aria-hidden="true">·</span>
                <span className="auth-trust-item">☁️ 100% en la nube</span>
              </div>
            </>
          )}
        </section>

        <p className="auth-foot">
          MyBusiness Silva © 2026 · Punto de venta en la nube para México
        </p>
      </main>
    </div>
  );
}
