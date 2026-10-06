/* =====================================================================
   brandArt — "logo marks" ORIGINALES por compañía para el catálogo de pagos.

   IMPORTANTE: NO son los logotipos registrados de cada empresa (eso requiere
   licencia). Son distintivos propios inspirados en el estilo de cada marca
   (color oficial + forma/tipografía), suficientes para reconocerlas de un
   vistazo sin infringir derechos. El día que tengas los logotipos OFICIALES
   autorizados, se muestran vía la columna logo_url y estos quedan de respaldo.

   Cada entrada devuelve un SVG cuadrado (viewBox 0 0 64 64) autocontenido.
   La clave se normaliza (minúsculas, sin acentos, sin paréntesis).
   ===================================================================== */

import type { ReactNode } from 'react';

/** Normaliza el nombre de la compañía para buscar su arte. */
export function brandKey(name: string): string {
  return name
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/\(.*?\)/g, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

/** Wordmark genérico: fondo de marca + texto corto centrado. */
function wordmark(bg: string, text: string, fg = '#ffffff', fontSize = 20): ReactNode {
  return (
    <svg viewBox="0 0 64 64" width="100%" height="100%" role="img">
      <rect width="64" height="64" rx="16" fill={bg} />
      <text x="32" y="33" textAnchor="middle" dominantBaseline="central"
        fontFamily="Inter, Arial, sans-serif" fontWeight="800" fontSize={fontSize} fill={fg}
        letterSpacing="-1">{text}</text>
    </svg>
  );
}

/** Glyph con círculo/figura + inicial, para marcas telefónicas. */
function orbMark(bg: string, ring: string, letter: string): ReactNode {
  return (
    <svg viewBox="0 0 64 64" width="100%" height="100%" role="img">
      <rect width="64" height="64" rx="16" fill={bg} />
      <circle cx="32" cy="32" r="19" fill="none" stroke={ring} strokeWidth="4" opacity="0.9" />
      <text x="32" y="33" textAnchor="middle" dominantBaseline="central"
        fontFamily="Inter, Arial, sans-serif" fontWeight="800" fontSize="22" fill="#ffffff">{letter}</text>
    </svg>
  );
}

/**
 * Mapa de arte por marca. Las claves están normalizadas con brandKey().
 * Colores tomados de las guías públicas de color de cada marca.
 */
const ART: Record<string, ReactNode> = {
  // ---- Telefonía (recargas) ----
  'telcel': (
    <svg viewBox="0 0 64 64" width="100%" height="100%" role="img">
      <rect width="64" height="64" rx="16" fill="#0093d0" />
      <path d="M20 26c6-7 18-7 24 0" fill="none" stroke="#fff" strokeWidth="4" strokeLinecap="round" />
      <path d="M25 33c4-4 10-4 14 0" fill="none" stroke="#fff" strokeWidth="4" strokeLinecap="round" opacity="0.85" />
      <circle cx="32" cy="42" r="3.6" fill="#fff" />
    </svg>
  ),
  'telcel sin limite': (
    <svg viewBox="0 0 64 64" width="100%" height="100%" role="img">
      <rect width="64" height="64" rx="16" fill="#0093d0" />
      <path d="M20 26c6-7 18-7 24 0" fill="none" stroke="#fff" strokeWidth="4" strokeLinecap="round" />
      <path d="M25 33c4-4 10-4 14 0" fill="none" stroke="#fff" strokeWidth="4" strokeLinecap="round" opacity="0.85" />
      <circle cx="32" cy="42" r="3.6" fill="#fff" />
    </svg>
  ),
  'movistar': orbMark('#00a9e0', '#ffffff', 'M'),
  'at t': wordmark('#00a8e0', 'AT&T', '#ffffff', 16),
  'unefon': wordmark('#e2001a', 'u', '#ffffff', 30),
  'bait': wordmark('#e30613', 'bait', '#ffffff', 17),
  'virgin mobile': wordmark('#e10a0a', 'V', '#ffffff', 28),
  'oui movil': wordmark('#ff6a13', 'OUI', '#ffffff', 17),
  'pillofon': wordmark('#7b2ff7', 'Pf', '#ffffff', 22),
  'diri movil': wordmark('#ec008c', 'diri', '#ffffff', 17),
  'soriana movil': wordmark('#d5121a', 'S', '#ffffff', 28),
  'freedompop': wordmark('#00b3a4', 'FP', '#ffffff', 20),
  'weex': wordmark('#00d1b2', 'weex', '#0f172a', 15),
  'flash mobile': wordmark('#f7941e', 'F', '#ffffff', 28),
  'cierto movil': wordmark('#22c55e', 'C', '#ffffff', 28),
  'alo chedraui': wordmark('#e30613', 'aló', '#ffffff', 18),
  'maxcom movil': wordmark('#0055a5', 'mx', '#ffffff', 22),
  'simpati walmart': orbMark('#0071ce', '#ffde00', 'S'),
  'qbocel': wordmark('#ff5a00', 'QB', '#ffffff', 20),

  // ---- TV de paga ----
  'sky': wordmark('#e2001a', 'SKY', '#ffffff', 17),
  'dish': wordmark('#ec1c24', 'DISH', '#ffffff', 15),

  // ---- Servicios: luz/agua/gas ----
  'cfe': (
    <svg viewBox="0 0 64 64" width="100%" height="100%" role="img">
      <rect width="64" height="64" rx="16" fill="#009540" />
      <path d="M35 14 L23 36 h9 l-3 14 16-22 h-9z" fill="#fff" />
    </svg>
  ),
  'sacmex agua cdmx': wordmark('#0088c7', '💧', '#ffffff', 22),
  'agua de toluca': wordmark('#0088c7', 'AT', '#ffffff', 20),
  'caem edomex': wordmark('#0088c7', 'CAEM', '#ffffff', 13),
  'agua organismo local': wordmark('#0088c7', 'H₂O', '#ffffff', 16),
  'naturgy': wordmark('#ff7a00', 'ng', '#ffffff', 22),
  'gas express lp': wordmark('#e30613', 'GAS', '#ffffff', 15),

  // ---- Telecom fija / internet ----
  'telmex': wordmark('#005baa', 'telmex', '#ffffff', 12),
  'izzi': wordmark('#00b0e6', 'izzi', '#ffffff', 18),
  'totalplay': wordmark('#e2231a', 'TP', '#ffffff', 20),
  'megacable': wordmark('#ee2a24', 'mega', '#ffffff', 15),
  'axtel': wordmark('#00a19a', 'axtel', '#ffffff', 14),

  // ---- Gobierno / créditos ----
  'infonavit': wordmark('#b01e2e', 'IN', '#ffffff', 20),
  'fonacot': wordmark('#611232', 'FC', '#ffffff', 20),
  'sat dpa': wordmark('#611232', 'SAT', '#ffffff', 17),
  'imss cuotas': wordmark('#026937', 'IMSS', '#ffffff', 14),
  'coppel': wordmark('#004a97', 'Coppel', '#ffde00', 12),
  'elektra': wordmark('#004a97', 'E', '#ffffff', 28),
  'banco azteca': wordmark('#00a94f', 'BA', '#ffffff', 20),
  'famsa': wordmark('#e30613', 'famsa', '#ffffff', 14),
  'liverpool': wordmark('#e5007d', 'L', '#ffffff', 28),
  'palacio de hierro': wordmark('#111111', 'PH', '#ffffff', 20),
  'bbva': wordmark('#004481', 'BBVA', '#ffffff', 15),
  'santander': wordmark('#ec0000', 'S', '#ffffff', 28),
  'banorte': wordmark('#eb0029', 'B', '#ffffff', 28),
  'hsbc': wordmark('#db0011', 'HSBC', '#ffffff', 14),
  'citibanamex': wordmark('#004990', 'Citi', '#ffffff', 16),
  'nu nubank': wordmark('#820ad1', 'nu', '#ffffff', 22),
  'mercado pago': orbMark('#00b1ea', '#ffde00', '$'),

  // ---- Streaming / gift cards ----
  'netflix': wordmark('#000000', 'N', '#e50914', 30),
  'spotify': (
    <svg viewBox="0 0 64 64" width="100%" height="100%" role="img">
      <rect width="64" height="64" rx="16" fill="#1db954" />
      <circle cx="32" cy="32" r="17" fill="none" />
      <path d="M20 26c9-3 18-2 26 3" fill="none" stroke="#0b3d1e" strokeWidth="3.6" strokeLinecap="round" />
      <path d="M21 33c8-2 15-1 21 2.6" fill="none" stroke="#0b3d1e" strokeWidth="3.4" strokeLinecap="round" />
      <path d="M22 40c6-1.6 12-1 17 1.6" fill="none" stroke="#0b3d1e" strokeWidth="3.2" strokeLinecap="round" />
    </svg>
  ),
  'disney': wordmark('#113ccf', 'D+', '#ffffff', 20),
  'hbo max': wordmark('#5b1fa8', 'HBO', '#ffffff', 16),
  'google play': (
    <svg viewBox="0 0 64 64" width="100%" height="100%" role="img">
      <rect width="64" height="64" rx="16" fill="#ffffff" />
      <path d="M22 18 L44 32 L22 46 z" fill="#0f9d58" />
      <path d="M22 18 L34 25 L22 46 z" fill="#4285f4" opacity="0.85" />
    </svg>
  ),
  'playstation': wordmark('#003791', 'PS', '#ffffff', 22),
  'xbox': orbMark('#107c10', '#ffffff', 'X'),
  'free fire garena': wordmark('#f57f17', 'FF', '#ffffff', 20),

  // ---- Transporte / otros ----
  'tarjeta mi metro metrobus cdmx': wordmark('#e5007d', 'MI', '#ffffff', 20),
  'peaje tag televia': wordmark('#0ea5e9', 'TAG', '#ffffff', 16),
  'peaje iave capufe': wordmark('#0f4c81', 'IAVE', '#ffffff', 14),
  'gasolineras vale': wordmark('#e30613', '⛽', '#ffffff', 22),
};

/** Devuelve el arte SVG propio de la marca, o null si no hay una entrada específica. */
export function brandArtFor(provider: string): ReactNode | null {
  return ART[brandKey(provider)] ?? null;
}
