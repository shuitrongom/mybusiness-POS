import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '@/lib/api';
import { useSession } from '@/store/session';
import './login.css';

/**
 * Pantalla de inicio de sesión premium: panel de marca (showcase) a la izquierda y formulario a
 * la derecha. Diseño enterprise para transmitir confianza y ser una carta de presentación.
 */
export function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [mfaCode, setMfaCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const login = useSession((s) => s.login);
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const { data } = await api.post('/auth/superadmin/login', {
        email,
        password,
        mfaCode: mfaCode || undefined,
      });
      if (data.accessToken) {
        login(data.accessToken);
        navigate('/', { replace: true });
      } else {
        setError('Credenciales inválidas');
      }
    } catch {
      setError('No se pudo iniciar sesión. Verifica tus datos.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-shell">
      {/* Panel de marca */}
      <div className="login-hero">
        <div className="hero-top">
          <div className="hero-mark">MS</div>
          <div>
            <div className="hero-brand-name">MyBusiness Silva</div>
            <div className="hero-brand-tag">CLOUD POS</div>
          </div>
        </div>

        <div className="hero-center">
          <h1 className="hero-headline">
            El punto de venta en la nube <span>que hace crecer tu negocio</span>
          </h1>
          <p className="hero-sub">
            Vende más rápido, controla tu inventario en tiempo real y factura con CFDI 4.0.
            Todo desde un solo lugar, seguro y accesible desde donde estés.
          </p>
        </div>

        <div className="hero-badges">
          <span className="hero-badge">☁️ 100% en la nube</span>
          <span className="hero-badge">🔒 Seguridad bancaria</span>
          <span className="hero-badge">📊 Inteligencia de negocio</span>
          <span className="hero-badge">🧾 CFDI 4.0</span>
        </div>
      </div>

      {/* Formulario */}
      <div className="login-form-side">
        <form className="login-card" onSubmit={handleSubmit}>
          <div className="login-card-mark">
            <div className="m">MS</div>
            <div>
              <div style={{ fontWeight: 700, color: 'var(--brand-800)' }}>MyBusiness Silva</div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Cloud POS</div>
            </div>
          </div>

          <div className="login-welcome">Bienvenido</div>
          <div className="login-hint">Ingresa tus credenciales para continuar</div>

          <label className="field">
            <span>Correo electrónico</span>
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="tu@correo.com"
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
              required
            />
          </label>

          <label className="field">
            <span>Código de doble factor (opcional)</span>
            <input
              type="text"
              value={mfaCode}
              onChange={(e) => setMfaCode(e.target.value)}
              placeholder="Si tienes MFA activo"
              inputMode="numeric"
            />
          </label>

          {error && <div className="login-error">{error}</div>}

          <button type="submit" className="btn-primary login-submit" disabled={loading}>
            {loading ? 'Ingresando…' : 'Entrar al sistema'}
          </button>

          <div className="login-foot">Acceso seguro · MyBusiness Silva © 2026</div>
        </form>
      </div>
    </div>
  );
}
