import axios, { type AxiosInstance } from 'axios';

/**
 * Cliente HTTP central. Adjunta el token de acceso (Bearer) a cada petición. Solo cierra la
 * sesión si el token realmente venció o falta; un 401/403 por permisos de un recurso concreto
 * NO expulsa al usuario (sigue autenticado, solo no puede ver ese recurso).
 */
const TOKEN_KEY = 'mbs.accessToken';

/** @returns true si el token existe y no ha expirado (según su claim exp). */
function tokenIsValid(): boolean {
  const token = getToken();
  if (!token) return false;
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));
    return typeof payload.exp === 'number' && payload.exp * 1000 > Date.now();
  } catch {
    return false;
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null): void {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
}

export const api: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    // Solo se cierra la sesión si el token realmente venció o falta. Si el token sigue vigente,
    // un 401/403 significa "sin permiso para ESTE recurso" y no debe expulsar al usuario.
    const status = error.response?.status;
    if ((status === 401 || status === 403) && !tokenIsValid()) {
      setToken(null);
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  },
);
