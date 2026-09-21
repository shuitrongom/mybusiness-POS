/** Utilidades de formato compartidas por las pantallas. */

/** Formatea un número como moneda mexicana. */
export const money = (n: number): string =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

/** Formatea una fecha ISO a formato local corto. */
export const shortDate = (iso: string | null | undefined): string =>
  iso ? new Date(iso).toLocaleDateString('es-MX') : '—';

/** Formatea una fecha/hora ISO a formato local. */
export const dateTime = (iso: string | null | undefined): string =>
  iso ? new Date(iso).toLocaleString('es-MX') : '—';
