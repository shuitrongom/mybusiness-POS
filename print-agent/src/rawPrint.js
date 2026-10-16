/**
 * rawPrint.js
 * Envio RAW de un buffer ESC/POS a una impresora de Windows POR NOMBRE.
 *
 * METODO ELEGIDO (justificado en README): P/Invoke a winspool.drv via
 * PowerShell + .NET. La diferencia clave respecto a la version anterior es que
 * ya NO se compila C# (Add-Type) en CADA impresion: eso costaba 2-5 s por
 * ticket. Ahora un PROCESO PowerShell PERSISTENTE (src/raw-print-worker.ps1)
 * hace el Add-Type del helper RawPrinterHelper UNA sola vez al arrancar y
 * luego recibe trabajos por stdin (una linea JSON por trabajo) y responde por
 * stdout. El P/Invoke a winspool.drv es identico al anterior.
 *
 * Arquitectura:
 *   - Worker singleton: se lanza powershell.exe con el .ps1 la primera vez que
 *     se necesita y se mantiene vivo. Si muere, se relanza en la siguiente
 *     impresion.
 *   - Cola FIFO en serie: printRaw() encola el trabajo y se procesa de a uno,
 *     con un timeout por trabajo. Un trabajo que falla o expira NO bloquea los
 *     siguientes.
 *   - Cache de impresoras: listPrinters() devuelve de inmediato una lista
 *     cacheada que un refresco en background actualiza al arrancar y cada ~5
 *     min. Nunca lanza PowerShell en el camino de /status.
 *
 * El buffer se pasa por un archivo temporal .prn (os.tmpdir()) que el worker
 * lee con [System.IO.File]::ReadAllBytes, para evitar problemas de codificacion
 * al inyectar bytes crudos.
 */

const { spawn } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { randomUUID } = require('node:crypto');
const { info, error } = require('./logger.js');

// __dirname/__filename son nativos de CommonJS (no se necesita import.meta).
// En DEV __dirname es print-agent/src. Empaquetado con pkg apunta al snapshot
// virtual de solo lectura (C:\snapshot\print-agent\src): fs.readFileSync SI
// puede leer de ahi (el .ps1 va como asset en package.json), pero PowerShell
// NO puede abrir una ruta del snapshot con -File. Por eso NO se le pasa a
// PowerShell una ruta dentro del snapshot: se COPIA el .ps1 a disco real.

// Carpeta de datos REAL en disco (escribible) usada para dejar la copia del
// worker .ps1 que PowerShell si puede ejecutar. Igual en dev y en .exe.
const DATA_DIR = path.join(process.env.LOCALAPPDATA || os.tmpdir(), 'PuntoNubePrint');

// Ruta de origen del worker: en dev y bajo pkg se LEE con fs desde el
// directorio del modulo (asset empacado). Nunca se pasa directa a PowerShell.
const WORKER_SRC_PATH = path.join(__dirname, 'raw-print-worker.ps1');

// Ruta REAL del worker ya copiado a disco (cacheada tras la primera copia).
let rutaWorkerReal = null;

/**
 * Resuelve el worker raw-print-worker.ps1 a una ruta REAL en disco que
 * PowerShell pueda ejecutar con -File.
 *
 * Por que: empaquetado con pkg el .ps1 vive dentro del snapshot virtual
 * (C:\snapshot\...). fs.readFileSync lo lee sin problema, pero powershell.exe
 * no puede abrir rutas del snapshot. La solucion robusta es leer el contenido
 * del asset con fs y escribirlo a %LOCALAPPDATA%\PuntoNubePrint (o os.tmpdir()
 * como respaldo). Funciona identico en dev (node src/server.js) y empaquetado.
 * Se sobrescribe siempre para tomar versiones nuevas tras actualizar el .exe.
 * @returns {string} ruta absoluta del .ps1 en disco real
 */
function resolverWorkerEnDisco() {
  // (a) Leer el contenido del worker empacado (funciona en dev y en snapshot).
  const contenido = fs.readFileSync(WORKER_SRC_PATH, 'utf8');
  // (b) Asegurar la carpeta de datos real.
  fs.mkdirSync(DATA_DIR, { recursive: true });
  // (c) Escribir la copia a disco real (sobrescribe para tomar nuevas versiones).
  const destino = path.join(DATA_DIR, 'raw-print-worker.ps1');
  fs.writeFileSync(destino, contenido, 'utf8');
  // (d) Devolver la ruta real que si puede abrir PowerShell.
  return destino;
}

// Timeout por trabajo de impresion (ms). Si un trabajo no responde en este
// tiempo se rechaza con error legible y la cola continua con el siguiente.
const JOB_TIMEOUT_MS = 20000;
// Timeout para los comandos de listado de impresoras (refresco de cache).
const LIST_TIMEOUT_MS = 15000;
// Intervalo de refresco de la cache de impresoras (~5 min).
const PRINTER_REFRESH_MS = 5 * 60 * 1000;

// ---------------------------------------------------------------------------
// Worker PowerShell persistente (singleton)
// ---------------------------------------------------------------------------

/** @type {import('node:child_process').ChildProcessWithoutNullStreams | null} */
let worker = null;
// Buffer de stdout del worker para parsear por lineas.
let workerStdoutBuf = '';
// Trabajos pendientes de respuesta, por id -> { resolve, reject, timer }.
const pending = new Map();

/**
 * Rechaza todos los trabajos pendientes (p. ej. si el worker muere).
 * @param {Error} err
 */
function rechazarPendientes(err) {
  for (const [, p] of pending) {
    clearTimeout(p.timer);
    p.reject(err);
  }
  pending.clear();
}

/**
 * Procesa el stdout acumulado del worker: cada linea completa es un JSON
 * { id, ok, error? }. Resuelve o rechaza el trabajo con ese id.
 */
function procesarStdout() {
  let idx;
  while ((idx = workerStdoutBuf.indexOf('\n')) >= 0) {
    const linea = workerStdoutBuf.slice(0, idx).trim();
    workerStdoutBuf = workerStdoutBuf.slice(idx + 1);
    if (!linea) continue;

    let msg;
    try {
      msg = JSON.parse(linea);
    } catch {
      // Linea no-JSON (ruido). Ignorar.
      continue;
    }
    if (!msg || typeof msg.id !== 'string') continue;

    const p = pending.get(msg.id);
    if (!p) continue;
    clearTimeout(p.timer);
    pending.delete(msg.id);
    if (msg.ok) {
      p.resolve();
    } else {
      p.reject(new Error(msg.error || 'Fallo la impresion RAW'));
    }
  }
}

/**
 * Garantiza que el worker persistente este vivo; lo lanza si hace falta.
 * @returns {import('node:child_process').ChildProcessWithoutNullStreams}
 */
function asegurarWorker() {
  if (worker && !worker.killed && worker.exitCode === null) {
    return worker;
  }

  // Resolver (una sola vez) la copia REAL en disco del worker .ps1. Si la copia
  // falla, se lanza: el trabajo actual se rechaza con mensaje legible sin
  // tumbar el proceso (enviarTrabajo captura el throw de asegurarWorker).
  if (rutaWorkerReal === null) {
    try {
      rutaWorkerReal = resolverWorkerEnDisco();
      info(`rawPrint: worker copiado a disco real -> ${rutaWorkerReal}`);
    } catch (err) {
      error('rawPrint: no se pudo copiar raw-print-worker.ps1 a disco real', err);
      throw new Error(`No se pudo preparar el worker de impresion: ${err.message}`);
    }
  }

  info('rawPrint: lanzando worker PowerShell persistente');
  const ps = spawn(
    'powershell.exe',
    ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-File', rutaWorkerReal],
    { windowsHide: true }
  );

  ps.stdout.setEncoding('utf8');
  ps.stdout.on('data', (d) => {
    workerStdoutBuf += d;
    procesarStdout();
  });
  ps.stderr.on('data', (d) => {
    const s = String(d).trim();
    if (s) error(`rawPrint worker (stderr): ${s}`);
  });
  ps.on('error', (err) => {
    error('rawPrint worker: error de proceso', err);
    if (worker === ps) worker = null;
    rechazarPendientes(new Error(`Worker de impresion fallo: ${err.message}`));
  });
  ps.on('exit', (code, signal) => {
    info(`rawPrint worker: termino (code=${code}, signal=${signal})`);
    if (worker === ps) worker = null;
    workerStdoutBuf = '';
    rechazarPendientes(new Error('El worker de impresion termino inesperadamente'));
  });

  worker = ps;
  return ps;
}

// ---------------------------------------------------------------------------
// Cola FIFO en serie
// ---------------------------------------------------------------------------

/** @type {Array<() => Promise<void>>} */
const cola = [];
let procesando = false;

/**
 * Procesa la cola de a un trabajo (serie). Reentrante-seguro.
 */
async function bombearCola() {
  if (procesando) return;
  procesando = true;
  try {
    while (cola.length > 0) {
      const tarea = cola.shift();
      // Cada tarea maneja su propio exito/fallo; no se propaga aqui para no
      // detener la cola.
      // eslint-disable-next-line no-await-in-loop
      await tarea();
    }
  } finally {
    procesando = false;
  }
}

/**
 * Envia UN trabajo al worker y espera la respuesta de su id, con timeout.
 * @param {string} id
 * @param {string} printer
 * @param {string} file ruta del .prn temporal
 * @returns {Promise<void>}
 */
function enviarTrabajo(id, printer, file) {
  return new Promise((resolve, reject) => {
    let ps;
    try {
      ps = asegurarWorker();
    } catch (err) {
      reject(err instanceof Error ? err : new Error(String(err)));
      return;
    }

    const timer = setTimeout(() => {
      if (pending.has(id)) {
        pending.delete(id);
        reject(new Error(`Tiempo de espera agotado (${JOB_TIMEOUT_MS} ms) al imprimir`));
      }
    }, JOB_TIMEOUT_MS);

    pending.set(id, { resolve, reject, timer });

    const payload = JSON.stringify({ id, printer, file }) + '\n';
    ps.stdin.write(payload, 'utf8', (err) => {
      if (err) {
        const p = pending.get(id);
        if (p) {
          clearTimeout(p.timer);
          pending.delete(id);
          reject(new Error(`No se pudo enviar el trabajo al worker: ${err.message}`));
        }
      }
    });
  });
}

/**
 * Envia un buffer RAW a la impresora de Windows por nombre.
 * Escribe el buffer a un .prn temporal, lo encola, se procesa en serie por el
 * worker persistente y se espera la respuesta de ese trabajo con timeout. En
 * finally borra el temporal. Rechaza con error legible si la impresora no
 * existe, esta apagada o sin papel (errores del spooler) o si expira.
 * @param {string} printerName nombre de la impresora en Windows (p.ej. 'POS-80')
 * @param {Buffer} buffer bytes ESC/POS a imprimir
 * @returns {Promise<void>}
 */
async function printRaw(printerName, buffer) {
  if (!printerName || typeof printerName !== 'string') {
    throw new Error('Nombre de impresora invalido');
  }
  if (!Buffer.isBuffer(buffer) || buffer.length === 0) {
    throw new Error('Buffer de impresion vacio');
  }

  const id = randomUUID();
  const tmp = path.join(os.tmpdir(), `puntonube-${id}.prn`);

  return new Promise((resolveExterno, rejectExterno) => {
    cola.push(async () => {
      const inicio = Date.now();
      try {
        fs.writeFileSync(tmp, buffer);
        info(`printRaw: enviando ${buffer.length} bytes a '${printerName}' (temp ${tmp})`);
        await enviarTrabajo(id, printerName, tmp);
        const ms = Date.now() - inicio;
        info(`printRaw: impresion enviada a '${printerName}' en ${ms} ms`);
        resolveExterno();
      } catch (err) {
        const ms = Date.now() - inicio;
        error(`printRaw: fallo tras ${ms} ms`, err);
        rejectExterno(err instanceof Error ? err : new Error(String(err)));
      } finally {
        try { fs.unlinkSync(tmp); } catch { /* el temporal puede no existir */ }
      }
    });
    bombearCola();
  });
}

// ---------------------------------------------------------------------------
// Cache de impresoras
// ---------------------------------------------------------------------------

/** @type {string[]} */
let printerCache = [];
let refreshTimer = null;

/**
 * Ejecuta PowerShell con un script y devuelve { code, stdout, stderr }.
 * Solo lo usa el refresco de cache de impresoras (no el camino de impresion).
 * @param {string} script
 * @param {number} [timeoutMs]
 * @returns {Promise<{ code:number, stdout:string, stderr:string }>}
 */
function runPowerShell(script, timeoutMs = LIST_TIMEOUT_MS) {
  return new Promise((resolve, reject) => {
    const ps = spawn(
      'powershell.exe',
      ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-Command', script],
      { windowsHide: true }
    );

    let stdout = '';
    let stderr = '';
    let finalizado = false;

    const timer = setTimeout(() => {
      if (finalizado) return;
      finalizado = true;
      try { ps.kill(); } catch { /* noop */ }
      reject(new Error(`Tiempo de espera agotado (${timeoutMs} ms) al ejecutar PowerShell`));
    }, timeoutMs);

    ps.stdout.on('data', (d) => { stdout += d.toString(); });
    ps.stderr.on('data', (d) => { stderr += d.toString(); });

    ps.on('error', (err) => {
      if (finalizado) return;
      finalizado = true;
      clearTimeout(timer);
      reject(err);
    });

    ps.on('close', (code) => {
      if (finalizado) return;
      finalizado = true;
      clearTimeout(timer);
      resolve({ code: code ?? -1, stdout: stdout.trim(), stderr: stderr.trim() });
    });
  });
}

/**
 * Consulta las impresoras instaladas (Get-Printer, fallback WMIC). Devuelve []
 * si todo falla. Es la operacion lenta que la cache evita en el camino caliente.
 * @returns {Promise<string[]>}
 */
async function consultarImpresoras() {
  // Metodo 1: Get-Printer (modulo PrintManagement, Windows 8+/Server 2012+)
  try {
    const r = await runPowerShell('Get-Printer | Select-Object -ExpandProperty Name');
    if (r.code === 0 && r.stdout) {
      const nombres = r.stdout.split(/\r?\n/).map((s) => s.trim()).filter(Boolean);
      if (nombres.length) return nombres;
    }
  } catch (err) {
    error('consultarImpresoras: fallo Get-Printer', err);
  }

  // Metodo 2 (fallback): WMIC
  try {
    const r = await runPowerShell('wmic printer get name');
    if (r.code === 0 && r.stdout) {
      const nombres = r.stdout
        .split(/\r?\n/)
        .map((s) => s.trim())
        .filter((s) => s && s.toLowerCase() !== 'name');
      return nombres;
    }
  } catch (err) {
    error('consultarImpresoras: fallo WMIC', err);
  }

  return [];
}

/**
 * Refresca la cache de impresoras en background. Nunca lanza.
 * @returns {Promise<void>}
 */
async function refrescarCache() {
  try {
    const nombres = await consultarImpresoras();
    printerCache = nombres;
    info(`listPrinters: cache actualizada (${nombres.length} impresoras)`);
  } catch (err) {
    error('refrescarCache: error inesperado', err);
  }
}

/**
 * Arranca el refresco de la cache de impresoras: un refresco inmediato y luego
 * cada ~5 min. Idempotente (no crea intervalos duplicados). El intervalo se
 * desreferencia para no mantener vivo el proceso.
 */
function startPrinterCache() {
  // Refresco inicial (no bloquea; corre en background).
  refrescarCache();
  if (!refreshTimer) {
    refreshTimer = setInterval(refrescarCache, PRINTER_REFRESH_MS);
    if (typeof refreshTimer.unref === 'function') refreshTimer.unref();
  }
}

/**
 * Devuelve la lista de impresoras desde la cache en memoria, de inmediato.
 * Nunca lanza y nunca ejecuta PowerShell en el camino de /status: si la cache
 * aun no se ha poblado devuelve []. Se mantiene async por compatibilidad con
 * el contrato de server.js.
 * @returns {Promise<string[]>}
 */
async function listPrinters() {
  return printerCache;
}

module.exports = { printRaw, listPrinters, startPrinterCache };
