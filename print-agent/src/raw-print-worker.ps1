# raw-print-worker.ps1
# Worker PowerShell PERSISTENTE para impresion RAW ESC/POS.
#
# Objetivo: eliminar la compilacion de C# (Add-Type) en CADA impresion. Este
# proceso de larga vida compila el helper RawPrinterHelper UNA sola vez al
# arrancar (P/Invoke a winspool.drv, el MISMO que estaba inline en rawPrint.js)
# y luego entra en un bucle leyendo trabajos por stdin, uno por linea.
#
# Protocolo (una linea JSON por mensaje):
#   ENTRADA  (stdin) : { "id": "<uuid>", "printer": "<nombre>", "file": "<ruta .prn>" }
#   SALIDA   (stdout): { "id": "<uuid>", "ok": true }
#                      { "id": "<uuid>", "ok": false, "error": "<detalle>" }
#
# El bucle NO termina cuando un trabajo falla: captura el error, responde
# ok:false y sigue esperando el siguiente trabajo. Node mantiene este proceso
# vivo y le manda trabajos en serie por stdin.

$ErrorActionPreference = 'Stop'

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

# Compilacion UNICA del helper (lo costoso) al arrancar el worker.
Add-Type -TypeDefinition $signature -Language CSharp

# Bucle principal: una linea de stdin = un trabajo JSON. Termina solo cuando
# stdin se cierra (ReadLine devuelve $null), es decir cuando Node mata el proceso.
while ($true) {
  $line = [Console]::In.ReadLine()
  if ($null -eq $line) { break }
  $line = $line.Trim()
  if ($line.Length -eq 0) { continue }

  $id = $null
  try {
    $ErrorActionPreference = 'Stop'
    $job = $line | ConvertFrom-Json
    $id = $job.id
    $bytes = [System.IO.File]::ReadAllBytes($job.file)
    [RawPrinterHelper]::SendBytes($job.printer, $bytes)
    $resp = @{ id = $id; ok = $true } | ConvertTo-Json -Compress
  } catch {
    $msg = $_.Exception.Message
    $resp = @{ id = $id; ok = $false; error = $msg } | ConvertTo-Json -Compress
  }

  [Console]::Out.WriteLine($resp)
  [Console]::Out.Flush()
}
