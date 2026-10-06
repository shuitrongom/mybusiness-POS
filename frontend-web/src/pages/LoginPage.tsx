import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Eye, EyeOff } from 'lucide-react';
import { api } from '@/lib/api';
import { useSession } from '@/store/session';
import { ThemeToggle } from '@/components/ThemeToggle';
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

  // Mostrar u ocultar contraseñas (el "ojito").
  const [showPassword, setShowPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

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
          login(data.accessToken, data.refreshToken);
          navigate('/', { replace: true });
        }
      } else {
        setError('Credenciales incorrectas. Verifica tu correo y contraseña.');
      }
    } catch (err) {
      // Distingue credenciales inválidas (401) de un problema de conexión con el servidor,
      // para que el mensaje sea útil al diagnosticar.
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 401) {
        setError('Correo o contraseña incorrectos.');
      } else if (status === 400) {
        setError('Revisa que el correo tenga un formato válido y la contraseña no esté vacía.');
      } else if (status === undefined) {
        setError('No se pudo conectar con el servidor. Verifica que el backend esté encendido.');
      } else {
        setError(`No se pudo iniciar sesión (error ${status}). Inténtalo de nuevo.`);
      }
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
        login(data.accessToken, data.refreshToken);
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
          <div className="auth-mark">PN</div>
          <div className="auth-brand-text">
            <div className="auth-brand-name">PuntoNube</div>
            <div className="auth-brand-tag">CLOUD POS · MÉXICO</div>
          </div>
        </div>
        <ThemeToggle />
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
                  <div className="field-password">
                    <input
                      type={showNewPassword ? 'text' : 'password'}
                      value={newPassword}
                      onChange={(e) => setNewPassword(e.target.value)}
                      placeholder="Mínimo 8 caracteres"
                      autoComplete="new-password"
                      required
                      autoFocus
                    />
                    <button
                      type="button"
                      className="field-eye"
                      onClick={() => setShowNewPassword((v) => !v)}
                      aria-label={showNewPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                      aria-pressed={showNewPassword}
                      title={showNewPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                    >
                      {showNewPassword ? <EyeOff size={20} strokeWidth={1.75} /> : <Eye size={20} strokeWidth={1.75} />}
                    </button>
                  </div>
                </label>

                <label className="field">
                  <span>Confirma la contraseña</span>
                  <div className="field-password">
                    <input
                      type={showConfirmPassword ? 'text' : 'password'}
                      value={confirmPassword}
                      onChange={(e) => setConfirmPassword(e.target.value)}
                      placeholder="Repite la contraseña"
                      autoComplete="new-password"
                      required
                    />
                    <button
                      type="button"
                      className="field-eye"
                      onClick={() => setShowConfirmPassword((v) => !v)}
                      aria-label={showConfirmPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                      aria-pressed={showConfirmPassword}
                      title={showConfirmPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                    >
                      {showConfirmPassword ? <EyeOff size={20} strokeWidth={1.75} /> : <Eye size={20} strokeWidth={1.75} />}
                    </button>
                  </div>
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
                  <div className="field-password">
                    <input
                      type={showPassword ? 'text' : 'password'}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="••••••••"
                      autoComplete="current-password"
                      required
                    />
                    <button
                      type="button"
                      className="field-eye"
                      onClick={() => setShowPassword((v) => !v)}
                      aria-label={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                      aria-pressed={showPassword}
                      title={showPassword ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                    >
                      {showPassword ? <EyeOff size={20} strokeWidth={1.75} /> : <Eye size={20} strokeWidth={1.75} />}
                    </button>
                  </div>
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
          PuntoNube © 2026 · Punto de venta en la nube para México
        </p>
      </main>
    </div>
  );
}
