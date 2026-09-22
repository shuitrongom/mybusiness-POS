/**
 * Utilidades para leer los datos del token JWT en el cliente (sin verificar la firma; solo para
 * decidir qué UI mostrar — la autorización real la hace siempre el backend).
 */

export interface TokenClaims {
  subject: string;
  roles: string[];
  modules: string[];
  tenant: string;
  exp: number;
}

/** Decodifica el payload de un JWT. Devuelve null si es inválido. */
export function decodeToken(token: string | null): TokenClaims | null {
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    const json = JSON.parse(
      decodeURIComponent(
        atob(payload.replace(/-/g, '+').replace(/_/g, '/'))
          .split('')
          .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
          .join(''),
      ),
    );
    return {
      subject: json.sub ?? '',
      roles: Array.isArray(json.roles) ? json.roles : [],
      modules: Array.isArray(json.modules) ? json.modules : [],
      tenant: json.tenant ?? '',
      exp: json.exp ?? 0,
    };
  } catch {
    return null;
  }
}

/** @returns true si el token corresponde a un Super Admin (proveedor del SaaS). */
export function isSuperAdmin(claims: TokenClaims | null): boolean {
  return !!claims && claims.roles.includes('SUPER_ADMIN');
}

/** @returns true si el usuario es Dueño o Administrador del negocio. */
export function isBusinessAdmin(claims: TokenClaims | null): boolean {
  return !!claims && (claims.roles.includes('OWNER') || claims.roles.includes('ADMIN'));
}

/** @returns true si el usuario es cajero (rol CASHIER). */
export function isCashier(claims: TokenClaims | null): boolean {
  return !!claims && claims.roles.includes('CASHIER') && !isBusinessAdmin(claims);
}

/** @returns true si el negocio del usuario tiene habilitado el módulo dado. */
export function hasModule(claims: TokenClaims | null, moduleKey: string): boolean {
  return !!claims && claims.modules.includes(moduleKey);
}
