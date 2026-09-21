import { create } from 'zustand';
import { getToken, setToken } from '@/lib/api';

/**
 * Estado de sesión del usuario en el cliente. Guarda el token de acceso y expone acciones
 * para iniciar y cerrar sesión. El token persiste en localStorage vía la capa de API.
 */
interface SessionState {
  token: string | null;
  isAuthenticated: boolean;
  login: (accessToken: string) => void;
  logout: () => void;
}

export const useSession = create<SessionState>((set) => ({
  token: getToken(),
  isAuthenticated: Boolean(getToken()),
  login: (accessToken: string) => {
    setToken(accessToken);
    set({ token: accessToken, isAuthenticated: true });
  },
  logout: () => {
    setToken(null);
    set({ token: null, isAuthenticated: false });
  },
}));
