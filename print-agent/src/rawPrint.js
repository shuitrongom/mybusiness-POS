/**
 * rawPrint.js
 * Envio RAW de un buffer ESC/POS a una impresora de Windows POR NOMBRE.
 *
 * METODO ELEGIDO (justificado en README): P/Invoke a winspool.drv via
 * PowerShell + .NET (Add-Type). Se abre la impresora por nombre con
 * OpenPrinter, se inicia un documento RAW con StartDocPrinter (datatype
 * "RAW"), se escribe el buffer con WritePrinter y se cierra con
 * EndDocPrinter/ClosePrinter. Es el metodo mas robusto para imprimir bytes
 * crudos a una termica sin compilar binarios nativos (solo PowerShell y el
 * .NET del sistema, presentes en todo Windows moderno). No depende de que la
 * impresora este compartida en red.
 *
 * El buffer se pasa por un archivo temporal .prn (os.tmpdir()) para evitar
 * problemas de codificacion al inyectar bytes por la linea de comandos; el
 * script PowerShell lo lee con -Encoding Byte / ReadAllBytes.
 */

import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { info, error } from './logger.js';

const PS_TIMEOUT_MS = 15000;

/**
 * Ejecuta PowerShell con un script y devuelve { code, stdout, stderr }.
 * Aplica un timeout; si expira, mata el proceso y rechaza.
 * @param {string} script contenido del script de PowerShell
 * @param {number} [timeoutMs]
 * @param {Record<string,string>} [extraEnv] variables de entorno extra para el proceso
 * @returns {Promise<{ code:number, stdout:string, stderr:string }>}
 */
function runPowerShell(script, timeoutMs = PS_TIMEOUT_MS, extraEnv = {}) {
  return new Promise((resolve, reject) => {
    const ps = spawn(
      'powershell.exe',
      ['-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-Command', script],
      { windowsHide: true, env: { ...process.env, ...extraEnv } }
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
 * Lista los nombres de impresoras instaladas en Windows.
 * Intenta Get-Printer (moderno) y cae a WMIC si falla. Nunca lanza: si todo
 * falla devuelve [].
 * @returns {Promise<string[]>}
 */
export async function listPrinters() {
  // Metodo 1: Get-Printer (modulo PrintManagement, Windows 8+/Server 2012+)
  try {
    const r = await runPowerShell('Get-Printer | Select-Object -ExpandProperty Name');
    if (r.code === 0 && r.stdout) {
      const nombres = r.stdout.split(/\r?\n/).map((s) => s.trim()).filter(Boolean);
      if (nombres.length) return nombres;
    }
  } catch (err) {
    error('listPrinters: fallo Get-Printer', err);
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
    error('listPrinters: fallo WMIC', err);
  }

  return [];
}

/**
 * Script PowerShell que imprime RAW un archivo a una impresora por nombre
 * usando P/Invoke a winspool.drv. Recibe las variables por el entorno:
 *   $env:PN_PRINTER (nombre), $env:PN_FILE (ruta .prn).
 */
const RAW_PRINT_SCRIPT = `
$ErrorActionPreference = 'Stop'
$printerName = $env:PN_PRINTER
$filePath = $env:PN_FILE

$signature = @'
using System;
using System.IO;
using System.Runtime.InteropServices;

public class RawPrinterHelper {
  [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
  public struct DOCINFOW {
    [MarshalAs(UnmanagedType.LPWStr)] public string pDocName;
    [MarshalAs(UnmanagedType.LPWStr)] public string pOutputFile;
    [MarshalAs(UnmanagedType.LPWStr)] public string pDatatype;
  }

  [DllImport("winspool.drv", EntryPoint = "OpenPrinterW", SetLastError = true, CharSet = CharSet.Unicode)]
  public static extern bool OpenPrinter(string src, out IntPtr hPrinter, IntPtr pd);
  [DllImport("winspool.drv", EntryPoint = "ClosePrinter", SetLastError = true)]
  public static extern bool ClosePrinter(IntPtr hPrinter);
  [DllImport("winspool.drv", EntryPoint = "StartDocPrinterW", SetLastError = true, CharSet = CharSet.Unicode)]
  public static extern bool StartDocPrinter(IntPtr hPrinter, int level, ref DOCINFOW di);
  [DllImport("winspool.drv", EntryPoint = "EndDocPrinter", SetLastError = true)]
  public static extern bool EndDocPrinter(IntPtr hPrinter);
  [DllImport("winspool.drv", EntryPoint = "StartPagePrinter", SetLastError = true)]
  public static extern bool StartPagePrinter(IntPtr hPrinter);
  [DllImport("winspool.drv", EntryPoint = "EndPagePrinter", SetLastError = true)]
  public static extern bool EndPagePrinter(IntPtr hPrinter);
  [DllImport("winspool.drv", EntryPoint = "WritePrinter", SetLastError = true)]
  public static extern bool WritePrinter(IntPtr hPrinter, IntPtr pBytes, int dwCount, out int dwWritten);

  public static void SendBytes(string printerName, byte[] bytes) {
    IntPtr hPrinter;
    if (!OpenPrinter(printerName, out hPrinter, IntPtr.Zero)) {
      throw new Exception("No se pudo abrir la impresora '" + printerName + "' (codigo " + Marshal.GetLastWin32Error() + ")");
    }
    try {
      DOCINFOW di = new DOCINFOW();
      di.pDocName = "PuntoNube Ticket";
      di.pDatatype = "RAW";
      if (!StartDocPrinter(hPrinter, 1, ref di)) {
        throw new Exception("StartDocPrinter fallo (codigo " + Marshal.GetLastWin32Error() + ")");
      }
      try {
        if (!StartPagePrinter(hPrinter)) {
          throw new Exception("StartPagePrinter fallo (codigo " + Marshal.GetLastWin32Error() + ")");
        }
        IntPtr pUnmanaged = Marshal.AllocCoTaskMem(bytes.Length);
        try {
          Marshal.Copy(bytes, 0, pUnmanaged, bytes.Length);
          int written;
          if (!WritePrinter(hPrinter, pUnmanaged, bytes.Length, out written)) {
            throw new Exception("WritePrinter fallo (codigo " + Marshal.GetLastWin32Error() + ")");
          }
        } finally {
          Marshal.FreeCoTaskMem(pUnmanaged);
        }
        EndPagePrinter(hPrinter);
      } finally {
        EndDocPrinter(hPrinter);
      }
    } finally {
      ClosePrinter(hPrinter);
    }
  }
}
'@

Add-Type -TypeDefinition $signature -Language CSharp
$bytes = [System.IO.File]::ReadAllBytes($filePath)
[RawPrinterHelper]::SendBytes($printerName, $bytes)
Write-Output "OK"
`;

/**
 * Envia un buffer RAW a la impresora de Windows por nombre.
 * Escribe el buffer a un .prn temporal, invoca el P/Invoke via PowerShell,
 * captura resultado y borra el temporal. Rechaza con error legible si la
 * impresora no existe, esta apagada o sin papel (errores del spooler).
 * @param {string} printerName nombre de la impresora en Windows (p.ej. 'POS-80')
 * @param {Buffer} buffer bytes ESC/POS a imprimir
 * @returns {Promise<void>}
 */
export async function printRaw(printerName, buffer) {
  if (!printerName || typeof printerName !== 'string') {
    throw new Error('Nombre de impresora invalido');
  }
  if (!Buffer.isBuffer(buffer) || buffer.length === 0) {
    throw new Error('Buffer de impresion vacio');
  }

  const tmp = path.join(os.tmpdir(), `puntonube-${randomUUID()}.prn`);
  try {
    fs.writeFileSync(tmp, buffer);
    info(`printRaw: enviando ${buffer.length} bytes a '${printerName}' (temp ${tmp})`);

    const r = await runPowerShell(RAW_PRINT_SCRIPT, PS_TIMEOUT_MS, {
      PN_PRINTER: printerName,
      PN_FILE: tmp,
    });

    if (r.code !== 0 || !/OK/.test(r.stdout)) {
      const detalle = r.stderr || r.stdout || `codigo ${r.code}`;
      throw new Error(`Fallo la impresion RAW: ${detalle}`);
    }
    info(`printRaw: impresion enviada a '${printerName}'`);
  } finally {
    try { fs.unlinkSync(tmp); } catch { /* el temporal puede no existir */ }
  }
}

export default { printRaw, listPrinters };
