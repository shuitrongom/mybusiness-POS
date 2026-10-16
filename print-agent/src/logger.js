/**
 * logger.js
 * Logger simple del agente de impresion. Escribe a consola y a un archivo
 * diario en print-agent/logs/agent-YYYY-MM-DD.log. Crea la carpeta logs si
 * no existe. Ninguna de sus funciones lanza excepciones: si falla el disco,
 * se degrada a solo-consola para no tumbar el servidor.
 */

const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');

// Carpeta donde viven los logs. En DEV (node src/server.js) escribe en
// print-agent/logs (un nivel arriba de src/, igual que antes). Empaquetado con
// pkg (process.pkg truthy) __dirname apunta al snapshot virtual de solo
// lectura (C:\snapshot\...), que NO se puede escribir; por eso bajo pkg los
// logs van a una carpeta REAL en disco: %LOCALAPPDATA%\PuntoNubePrint\logs (o
// os.tmpdir() si no existe LOCALAPPDATA). __dirname/__filename son nativos de
// CommonJS, no requieren import.meta.
const BASE_DIR = process.pkg
  ? path.join(process.env.LOCALAPPDATA || os.tmpdir(), 'PuntoNubePrint')
  : path.join(__dirname, '..');
const LOG_DIR = path.join(BASE_DIR, 'logs');

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
function info(msg) {
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
function error(msg, err) {
  let detalle = '';
  if (err) {
    detalle = err instanceof Error ? ` -> ${err.message}` : ` -> ${String(err)}`;
  }
  const linea = `[${sello()}] [ERROR] ${msg}${detalle}`;
  // eslint-disable-next-line no-console
  console.error(linea);
  aArchivo(linea);
}

module.exports = { info, error };
