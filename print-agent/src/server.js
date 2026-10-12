#!/usr/bin/env node
/**
 * server.js
 * Servidor HTTP del agente 'PuntoNube Print'. Escucha SOLO en 127.0.0.1
 * (loopback) para que el POS en https://app.puntonubepos.com pueda hablarle
 * sin disparar mixed-content (los navegadores modernos tratan 127.0.0.1 como
 * secure context). Expone:
 *   GET  /status -> { ok:true, version, printers:[...] }
 *   POST /print  -> imprime un ticket ESC/POS o abre el cajon
 *
 * Sin dependencias runtime: solo modulos nativos de Node.
 */

import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildTicketBuffer, buildOpenDrawerBuffer } from './escpos.js';
import { printRaw, listPrinters } from './rawPrint.js';
import { info, error } from './logger.js';

const VERSION = '1.0.0';
const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * Carga config.json (un nivel arriba de src/) si existe. Nunca lanza.
 * @returns {{ port:number, defaultPrinter:string, widthMm:number }}
 */
function cargarConfig() {
  const defaults = { port: 9100, defaultPrinter: 'POS-80', widthMm: 80 };
  try {
    const ruta = path.join(__dirname, '..', 'config.json');
    if (fs.existsSync(ruta)) {
      const raw = JSON.parse(fs.readFileSync(ruta, 'utf8'));
      return { ...defaults, ...raw };
    }
  } catch (err) {
    error('No se pudo leer config.json, usando valores por defecto', err);
  }
  return defaults;
}

const config = cargarConfig();
// Override por variables de entorno.
const PORT = Number(process.env.PORT) || config.port || 9100;
const DEFAULT_PRINTER = process.env.PRINTER || config.defaultPrinter || 'POS-80';
const DEFAULT_WIDTH_MM = Number(process.env.WIDTH_MM) || config.widthMm || 80;
const HOST = '127.0.0.1';

// Allowlist de origenes para CORS.
const ORIGENES_FIJOS = new Set([
  'https://app.puntonubepos.com',
  'https://puntonubepos.com',
  'https://www.puntonubepos.com',
]);

/**
 * Determina si un Origin esta permitido por CORS.
 * Acepta la allowlist fija y cualquier puerto de http://localhost y
 * http://127.0.0.1.
 * @param {string|undefined} origin
 * @returns {boolean}
 */
function origenPermitido(origin) {
  if (!origin) return false;
  if (ORIGENES_FIJOS.has(origin)) return true;
  // http://localhost[:puerto] o http://127.0.0.1[:puerto]
  return /^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(origin);
}

/**
 * Aplica los headers CORS a la respuesta si el Origin esta permitido.
 * @param {http.IncomingMessage} req
 * @param {http.ServerResponse} res
 */
function aplicarCors(req, res) {
  const origin = req.headers.origin;
  if (origenPermitido(origin)) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Vary', 'Origin');
  }
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
}

/**
 * Envia una respuesta JSON.
 * @param {http.ServerResponse} res
 * @param {number} status
 * @param {object} obj
 */
function enviarJson(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(body);
}

/**
 * Lee el body completo de la peticion como string (limite defensivo de 1MB).
 * @param {http.IncomingMessage} req
 * @returns {Promise<string>}
 */
function leerBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    req.on('data', (c) => {
      total += c.length;
      if (total > 1_000_000) {
        reject(new Error('Body demasiado grande'));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

/**
 * Maneja GET /status.
 * @param {http.ServerResponse} res
 */
async function manejarStatus(res) {
  let printers = [];
  try {
    printers = await listPrinters();
  } catch (err) {
    error('GET /status: listPrinters fallo, devolviendo lista vacia', err);
    printers = [];
  }
  enviarJson(res, 200, { ok: true, version: VERSION, printers });
}

/**
 * Maneja POST /print.
 * @param {http.IncomingMessage} req
 * @param {http.ServerResponse} res
 */
async function manejarPrint(req, res) {
  let raw;
  try {
    raw = await leerBody(req);
  } catch (err) {
    error('POST /print: error leyendo body', err);
    enviarJson(res, 400, { ok: false, error: 'No se pudo leer el cuerpo de la peticion' });
    return;
  }

  let body;
  try {
    body = JSON.parse(raw);
  } catch {
    enviarJson(res, 400, { ok: false, error: 'JSON invalido' });
    return;
  }

  try {
    const printer = (body && body.printer) || DEFAULT_PRINTER || 'POS-80';
    const widthMm = body && body.widthMm === 58 ? 58 : (body && body.widthMm === 80 ? 80 : DEFAULT_WIDTH_MM);

    let buffer;
    if (body && body.openDrawerOnly) {
      buffer = buildOpenDrawerBuffer();
      info(`POST /print: abrir cajon en '${printer}'`);
    } else {
      buffer = buildTicketBuffer(body ? body.ticket : undefined, widthMm);
      info(`POST /print: ticket (${widthMm}mm) en '${printer}'`);
    }

    await printRaw(printer, buffer);
    enviarJson(res, 200, { ok: true });
  } catch (err) {
    error('POST /print: fallo la impresion', err);
    enviarJson(res, 500, { ok: false, error: err instanceof Error ? err.message : String(err) });
  }
}

const server = http.createServer((req, res) => {
  aplicarCors(req, res);

  // Preflight.
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const url = (req.url || '').split('?')[0];

  if (req.method === 'GET' && url === '/status') {
    manejarStatus(res).catch((err) => {
      error('GET /status: error inesperado', err);
      enviarJson(res, 500, { ok: false, error: 'Error interno' });
    });
    return;
  }

  if (req.method === 'POST' && url === '/print') {
    manejarPrint(req, res).catch((err) => {
      error('POST /print: error inesperado', err);
      enviarJson(res, 500, { ok: false, error: 'Error interno' });
    });
    return;
  }

  enviarJson(res, 404, { ok: false, error: 'Ruta no encontrada' });
});

// Evitar que un error no manejado tumbe el proceso.
process.on('uncaughtException', (err) => error('uncaughtException', err));
process.on('unhandledRejection', (err) => error('unhandledRejection', err));

server.listen(PORT, HOST, () => {
  info(`PuntoNube Print v${VERSION} escuchando en http://${HOST}:${PORT}`);
  info(`Impresora por defecto: '${DEFAULT_PRINTER}' | Ancho: ${DEFAULT_WIDTH_MM}mm`);
});

export { server, origenPermitido };
