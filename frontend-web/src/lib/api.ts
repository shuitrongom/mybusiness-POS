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

/**
 * Lee el timestamp de expiración (exp, en segundos) del JWT sin verificar firma. Devuelve 0 si
 * no se puede leer. Solo se usa para decidir si conviene renovar ANTES de enviar la petición.
 */
function tokenExpSeconds(token: string | null): number {
  if (!token) return 0;
  try {
    const payload = token.split('.')[1];
    const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
    return typeof json.exp === 'number' ? json.exp : 0;
  } catch {
    return 0;
  }
}

/** @returns true si al token le quedan menos de 60s de vida (o ya expiró). */
function tokenCasiExpirado(token: string | null): boolean {
  const exp = tokenExpSeconds(token);
  if (!exp) return false; // si no se puede leer, no forzamos refresh aquí (lo hará el 401)
  return exp * 1000 - Date.now() < 60_000;
}

// Interceptor de request PROACTIVO: si el access token está por expirar (o ya expiró) y hay
// refresh token, lo renueva ANTES de enviar la petición. Así NINGUNA petición (incluidas
// POST/PUT/DELETE de escritura) sale con un token muerto, que era la causa de los 401 al operar
// tras un rato. El reintento ante 401 (abajo) queda como segunda red de seguridad.
api.interceptors.request.use(async (config) => {
  const url = config.url ?? '';
  const isAuthCall = url.includes('/auth/refresh') || url.includes('/auth/login')
    || url.includes('/auth/superadmin/login');

  let token = getToken();
  if (!isAuthCall && tokenCasiExpirado(token) && getRefreshToken()) {
    refreshPromise = refreshPromise ?? renewAccessToken();
    const fresh = await refreshPromise;
    refreshPromise = null;
    if (fresh) token = fresh;
  }
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
        // Persiste el token nuevo (por si renewAccessToken no lo guardó aún) y fuerza su uso en el
        // reintento, tanto en el header del request original como vía getToken() del interceptor
        // de request. Así el reintento NUNCA viaja con el token viejo (causa del 401 persistente).
        setToken(newToken);
        original.headers = original.headers ?? {};
        (original.headers as Record<string, string>).Authorization = `Bearer ${newToken}`;
        return api(original); // reintenta la petición original con el token renovado
      }
      // No se pudo renovar: la sesión terminó de verdad.
      forceLogout();
    }

    // 403 (sin permiso para el recurso) NO expulsa: el usuario sigue autenticado.
    return Promise.reject(error);
  },
);
