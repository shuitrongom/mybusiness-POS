import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '@/lib/api';
import { useSession } from '@/store/session';
import './login.css';

/**
 * Pantalla de inicio de sesión. Autentica contra el endpoint del Super Admin y guarda el token.
 * Diseño centrado y limpio, con la marca.
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
    <div className="login-wrap">
      <form className="login-card" onSubmit={handleSubmit}>
        <div className="login-brand">
          <div className="login-mark">MS</div>
          <div>
            <div className="login-name">MyBusiness Silva</div>
            <div className="login-tag">Punto de venta en la nube</div>
          </div>
        </div>

        <h2 className="login-title">Iniciar sesión</h2>

        <label className="field">
          <span>Correo</span>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
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
            required
          />
        </label>

        <label className="field">
          <span>Código de doble factor (si aplica)</span>
          <input
            type="text"
            value={mfaCode}
            onChange={(e) => setMfaCode(e.target.value)}
            placeholder="Opcional"
            inputMode="numeric"
          />
        </label>

        {error && <div className="login-error">{error}</div>}

        <button type="submit" className="btn-primary login-submit" disabled={loading}>
          {loading ? 'Ingresando…' : 'Entrar'}
        </button>
      </form>
    </div>
  );
}
