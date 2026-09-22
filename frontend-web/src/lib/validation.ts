/**
 * Validaciones y normalizaciones de datos mexicanos (RFC, correo, WhatsApp).
 * Se usan en los formularios de alta (negocio, cajeros) para dar retroalimentación en vivo.
 */

/** Formato de correo electrónico razonable para uso en formularios. */
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

/** RFC del SAT: 3-4 letras iniciales, 6 dígitos de fecha, 3 de homoclave. */
const RFC_RE = /^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$/;

/** @returns true si el correo tiene un formato válido. */
export function isValidEmail(value: string): boolean {
  return EMAIL_RE.test(value.trim());
}

/** Normaliza el RFC: quita espacios y lo pasa a mayúsculas. */
export function normalizeRfc(value: string): string {
  return value.trim().toUpperCase().replace(/\s+/g, '');
}

/**
 * Valida un RFC ya normalizado (persona moral = 12, física = 13 caracteres).
 * Un RFC vacío se considera válido porque el campo es opcional.
 */
export function isValidRfc(value: string): boolean {
  const rfc = normalizeRfc(value);
  if (rfc === '') return true;
  return RFC_RE.test(rfc);
}

/** Extrae solo los dígitos de un texto. */
function digits(value: string): string {
  return value.replace(/\D/g, '');
}

/**
 * Valida un número de WhatsApp de México: debe quedar en 10 dígitos nacionales.
 * Acepta que el usuario escriba la lada +52 (o 52) al inicio, que se ignora para el conteo.
 */
export function isValidMxPhone(value: string): boolean {
  let d = digits(value);
  if (d.startsWith('52') && d.length > 10) {
    d = d.slice(d.length - 10); // conserva los últimos 10 (número nacional)
  }
  return d.length === 10;
}

/**
 * Normaliza el teléfono a formato internacional de México: {@code +52XXXXXXXXXX}.
 * Devuelve cadena vacía si no hay número.
 */
export function normalizeMxPhone(value: string): string {
  let d = digits(value);
  if (d === '') return '';
  if (d.startsWith('52') && d.length > 10) {
    d = d.slice(d.length - 10);
  }
  if (d.length === 10) return `+52${d}`;
  return value.trim();
}

/** Formatea 10 dígitos como "55 1234 5678" para mostrar. Deja el resto tal cual. */
export function formatMxPhoneDisplay(value: string): string {
  const d = digits(value).slice(-10);
  if (d.length !== 10) return value;
  return `${d.slice(0, 2)} ${d.slice(2, 6)} ${d.slice(6)}`;
}
