import { create } from 'zustand';
import { getToken, setToken, setRefreshToken, setOnTokenRefreshed } from '@/lib/api';

/**
 * Estado de sesión del usuario en el cliente. Guarda el token de acceso y expone acciones
 * para iniciar y cerrar sesión. Los tokens persisten en localStorage vía la capa de API.
 */
interface SessionState {
  token: string | null;
  isAuthenticated: boolean;
  login: (accessToken: string, refreshToken?: string) => void;
  logout: () => void;
}

export const useSession = create<SessionState>((set) => ({
  token: getToken(),
  isAuthenticated: Boolean(getToken()),
  login: (accessToken: string, refreshToken?: string) => {
    setToken(accessToken);
    if (refreshToken) {
      setRefreshToken(refreshToken);
    }
    set({ token: accessToken, isAuthenticated: true });
  },
  logout: () => {
    setToken(null);
    setRefreshToken(null);
    set({ token: null, isAuthenticated: false });
  },
}));

// Mantiene el token en memoria sincronizado cuando api.ts lo RENUEVA de forma transparente
// (refresh proactivo o reintento tras 401). Sin esto, componentes que derivan datos del token
// (p. ej. el POS: isBakery/hasModule) se quedarían con el token viejo hasta recargar. El callback
// se registra desde el store para evitar que api.ts importe session.ts (ciclo de imports).
setOnTokenRefreshed((accessToken: string) => {
  useSession.setState({ token: accessToken, isAuthenticated: true });
});
