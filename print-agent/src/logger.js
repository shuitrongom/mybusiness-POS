/**
 * logger.js
 * Logger simple del agente de impresion. Escribe a consola y a un archivo
 * diario en print-agent/logs/agent-YYYY-MM-DD.log. Crea la carpeta logs si
 * no existe. Ninguna de sus funciones lanza excepciones: si falla el disco,
 * se degrada a solo-consola para no tumbar el servidor.
 */

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
// logs viven en print-agent/logs (un nivel arriba de src/)
const LOG_DIR = path.join(__dirname, '..', 'logs');

/**
 * Devuelve la fecha en formato YYYY-MM-DD (hora local).
 * @returns {string}
 */
function hoy() {
  const d = new Date();
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, '0');
  const dd = String(d.getDate()).padStart(2, '0');
  return `${yyyy}-${mm}-${dd}`;
}

/**
 * Marca de tiempo ISO local para el prefijo de cada linea.
 * @returns {string}
 */
function sello() {
  return new Date().toISOString();
}

/**
 * Escribe una linea en el archivo de log del dia. Nunca lanza.
 * @param {string} linea
 */
function aArchivo(linea) {
  try {
    fs.mkdirSync(LOG_DIR, { recursive: true });
    const archivo = path.join(LOG_DIR, `agent-${hoy()}.log`);
    fs.appendFileSync(archivo, linea + '\n', 'utf8');
  } catch {
    // Degradar silenciosamente a solo-consola; no debe tumbar el agente.
  }
}

/**
 * Registra un mensaje informativo.
 * @param {string} msg
 */
export function info(msg) {
  const linea = `[${sello()}] [INFO] ${msg}`;
  // eslint-disable-next-line no-console
  console.log(linea);
  aArchivo(linea);
}

/**
 * Registra un error. Acepta un Error opcional para incluir su mensaje/stack.
 * @param {string} msg
 * @param {unknown} [err]
 */
export function error(msg, err) {
  let detalle = '';
  if (err) {
    detalle = err instanceof Error ? ` -> ${err.message}` : ` -> ${String(err)}`;
  }
  const linea = `[${sello()}] [ERROR] ${msg}${detalle}`;
  // eslint-disable-next-line no-console
  console.error(linea);
  aArchivo(linea);
}

export default { info, error };
