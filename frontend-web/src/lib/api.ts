import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';

/**
 * Cliente HTTP central.
 *
 * - Adjunta el token de acceso (Bearer) a cada petición.
 * - Cuando el access token expira (401), intenta RENOVARLO automáticamente con el refresh token
 *   contra /auth/refresh y reintenta la petición original una sola vez. Así el panel no "deja de
 *   funcionar" a los 15 minutos: la sesión se mantiene mientras el refresh siga vigente.
 * - Solo cierra la sesión (redirige a /login) si la renovación también falla.
 */
const TOKEN_KEY = 'mbs.accessToken';
const REFRESH_KEY = 'mbs.refreshToken';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null): void {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  return localStorage.getItem(REFRESH_KEY);
}

export function setRefreshToken(token: string | null): void {
  if (token) localStorage.setItem(REFRESH_KEY, token);
  else localStorage.removeItem(REFRESH_KEY);
}

// URL base del API. En desarrollo usa '/api/v1' (proxy de Vite al backend local). En producción
// se define VITE_API_URL (ej. https://api.tudominio.com) en el build del frontend.
const API_BASE = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '');

export const api: AxiosInstance = axios.create({
  baseURL: API_BASE ? `${API_BASE}/api/v1` : '/api/v1',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** Cierra la sesión local y lleva al login (cuando la renovación no es posible). */
function forceLogout(): void {
  setToken(null);
  setRefreshToken(null);
  if (window.location.pathname !== '/login') {
    window.location.href = '/login';
  }
}

// Control de una única renovación concurrente: si varias peticiones fallan a la vez con 401,
// todas esperan al mismo refresh en curso en vez de disparar múltiples renovaciones.
let refreshPromise: Promise<string | null> | null = null;

/** Intenta obtener un access token nuevo con el refresh token. Devuelve el nuevo access o null. */
async function renewAccessToken(): Promise<string | null> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return null;
  try {
    // Se usa una instancia axios "cruda" (sin interceptores) para evitar recursión.
    const refreshUrl = API_BASE ? `${API_BASE}/api/v1/auth/refresh` : '/api/v1/auth/refresh';
    const { data } = await axios.post<{ accessToken: string | null; refreshToken: string | null }>(
      refreshUrl,
      { refreshToken },
      { headers: { 'Content-Type': 'application/json' } },
    );
    if (data.accessToken) {
      setToken(data.accessToken);
      if (data.refreshToken) setRefreshToken(data.refreshToken);
      return data.accessToken;
    }
    return null;
  } catch {
    return null;
  }
}

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const status = error.response?.status;
    const original = error.config as (InternalAxiosRequestConfig & { _retried?: boolean }) | undefined;

    // Solo intentamos renovar ante un 401, una sola vez por petición, y nunca sobre el propio
    // endpoint de refresh o de login (para no entrar en bucle).
    const url = original?.url ?? '';
    const isAuthCall = url.includes('/auth/refresh') || url.includes('/auth/login')
      || url.includes('/auth/superadmin/login');

    if (status === 401 && original && !original._retried && !isAuthCall) {
      original._retried = true;
      // Reutiliza una renovación en curso si ya hay una.
      refreshPromise = refreshPromise ?? renewAccessToken();
      const newToken = await refreshPromise;
      refreshPromise = null;

      if (newToken) {
        original.headers.Authorization = `Bearer ${newToken}`;
        return api(original); // reintenta la petición original con el token renovado
      }
      // No se pudo renovar: la sesión terminó de verdad.
      forceLogout();
    }

    // 403 (sin permiso para el recurso) NO expulsa: el usuario sigue autenticado.
    return Promise.reject(error);
  },
);
