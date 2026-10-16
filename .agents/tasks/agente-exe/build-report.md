# Reporte de build — Agente PuntoNube Print robusto + .exe

Rama: `fix/agente-exe-robusto` (worktree `.worktrees/agente-exe`).
Commit base del fix: `bf877a0` — *fix(print-agent): CommonJS + copia del worker a disco real para .exe robusto*.
Entorno de build: Windows, Node 22+, `@yao-pkg/pkg@6.23.0`.

Este reporte resume el trabajo ya hecho y verificado. Los resultados de las
suites se toman de `verification-note.md` y del historial de commits; NO se
re-corrieron las pruebas salvo verificaciones puntuales.

---

## (a) Conversión a CommonJS — archivos tocados

El bug original (`ERR_MODULE_NOT_FOUND` / `import.meta may appear only with
sourceType: "module"`) venía de empaquetar con `pkg` un agente escrito en ESM:
`pkg` no resuelve bien `import` dinámico ni `import.meta.url` dentro del
snapshot virtual. La solución fue convertir el agente a CommonJS (`require` /
`module.exports`, `__dirname`/`__filename` nativos) para que `@yao-pkg/pkg`
produzca un `.exe` de un solo archivo sin resolución ESM en runtime.

Archivos del commit `bf877a0`:

| Archivo | Cambio |
|---|---|
| `print-agent/package.json` | `"type": "commonjs"`, target `node22-win-x64`, `script build:exe`, `@yao-pkg/pkg` como devDependency, bloque `pkg.assets` con `config.json` y `src/raw-print-worker.ps1` |
| `print-agent/src/server.js` | ESM → CJS (`require`/`module.exports`), sin `import.meta` |
| `print-agent/src/escpos.js` | ESM → CJS (`module.exports = { buildTicketBuffer, buildOpenDrawer... }`) |
| `print-agent/src/escpos.test.js` | ESM → CJS (`require`) para `node --test` |
| `print-agent/src/logger.js` | ESM → CJS; además `BASE_DIR` condicional a `process.pkg` (logs a `%LOCALAPPDATA%\PuntoNubePrint\logs` bajo el `.exe`, a `print-agent/logs` en dev) |
| `print-agent/src/rawPrint.js` | ESM → CJS; copia robusta del worker `.ps1` a disco real (ver sección b) |
| `print-agent/package-lock.json` | nuevo, generado por `npm install` de `@yao-pkg/pkg` |
| `print-agent/instalar-agente.bat` | nuevo (ver sección d) |
| `print-agent/desinstalar-agente.bat` | nuevo (ver sección d) |
| `print-agent/README.md` | instrucciones de build e instalación actualizadas |

La conversión fue mecánica: no cambió ningún comportamiento ni contrato
(puerto 9100, CORS, `GET /status`, `POST /print`, formato ESC/POS y timeouts
intactos). Los 7 unit tests ESC/POS lo confirman.

## (b) Manejo del asset `raw-print-worker.ps1` (copia a disco real)

### El bug del snapshot path

Empaquetado con `pkg`, los archivos del proyecto quedan dentro de un snapshot
virtual de solo lectura montado en `C:\snapshot\print-agent\...`. Node (y
`fs.readFileSync`) sí pueden LEER de ese snapshot, pero **`powershell.exe` NO
puede abrir una ruta del snapshot con `-File`** (el snapshot no es un archivo
real del sistema de archivos de Windows). El error original
`Cannot find module 'C:\snapshot\print-agent\src\server.js'` era la cara ESM de
ese mismo problema; el worker `.ps1` lo habría sufrido igual al pasarle a
PowerShell una ruta `C:\snapshot\...\raw-print-worker.ps1`.

### La solución robusta

En `src/rawPrint.js`:

1. El `.ps1` se declara como **asset de pkg** (`pkg.assets` en `package.json`),
   de modo que queda incrustado en el `.exe`.
2. `WORKER_SRC_PATH = path.join(__dirname, 'raw-print-worker.ps1')` se usa SOLO
   para **leer el contenido** con `fs.readFileSync` (funciona en dev y desde el
   snapshot).
3. `resolverWorkerEnDisco()` escribe ese contenido a una carpeta REAL y
   escribible: `%LOCALAPPDATA%\PuntoNubePrint\raw-print-worker.ps1` (con
   `os.tmpdir()` como respaldo si no existe `LOCALAPPDATA`). Se sobrescribe
   siempre, así una actualización del `.exe` trae un worker nuevo.
4. A `powershell.exe -File` se le pasa **únicamente esa ruta real**, nunca una
   ruta del snapshot.

El resultado es idéntico en dev (`node src/server.js`) y empaquetado: PowerShell
siempre ejecuta un `.ps1` que existe físicamente en disco. `logger.js` aplica la
misma idea con `process.pkg` para que los logs y `config.json` también vivan en
la carpeta real. Esto es lo que elimina de raíz el bug del snapshot path.

## (c) Resultados de verificación

Tomados de `verification-note.md` (corrida 2026-10-16) y del cuerpo del commit
`bf877a0`. Todas las verificaciones pasaron.

| Verificación | Comando | Resultado |
|---|---|---|
| Unit tests ESC/POS | `node --test` (`npm test`) | PASS 7/7, 0 fallos |
| Arranque DEV | `node src/server.js` + `GET /status` | HTTP 200 `{"ok":true,"version":"1.0.0","printers":[...]}` |
| Instalación de deps | `npm install` | OK (node_modules + package-lock.json) |
| Empaquetado | `npm run build:exe` (`pkg . --targets node22-win-x64 --output dist/puntonube-print.exe --fallback-to-source`) | OK, `dist/puntonube-print.exe` ~57.6 MB, SIN errores ESM/`ERR_MODULE_NOT_FOUND` |
| Ejecución del `.exe` | `dist\puntonube-print.exe` + `GET /status` | HTTP 200, SIN `ERR_MODULE_NOT_FOUND` |
| Copia real del worker | `POST /print` desde el `.exe` | `WORKER_COPIED_AFTER_PRINT=true`: el `.ps1` se copió a `%LOCALAPPDATA%\PuntoNubePrint\raw-print-worker.ps1` |
| Worker P/Invoke | `POST /print` a impresora inexistente | Error legible del spooler (código 1801 "nombre de impresora inválido"), proceso NO cae |

Notas:
- La prueba a una impresora inexistente (`__NoExiste__`) es intencional: fuerza
  el camino de impresión y demuestra que el worker PowerShell arrancó desde el
  `.exe`, compiló el helper P/Invoke (`Add-Type`), recibió el trabajo por stdin,
  llamó a `winspool.drv` y devolvió un error legible sin tumbar el agente.
- `dist/` y `*.exe` están en `.gitignore`: el ejecutable NO se versiona, se
  regenera con `npm run build:exe`.
- El script `build:exe` conserva `--fallback-to-source`: si en algún entorno
  `pkg` no logra generar el bytecode V8 de un archivo, lo empaqueta como fuente
  en lugar de abortar. Es una red de seguridad para que la generación del `.exe`
  nunca falle en una máquina del cliente; el resultado sigue siendo un único
  `.exe` autocontenido.

## (d) `instalar-agente.bat` y la tarea programada

`instalar-agente.bat` es la instalación de **un clic**, idempotente (se puede
correr varias veces sin problema):

1. Crea `%LOCALAPPDATA%\PuntoNubePrint` y copia ahí `dist\puntonube-print.exe`.
   Si no encuentra el `.exe`, aborta pidiendo correr `npm install` y
   `npm run build:exe` primero.
2. Copia `config.json` solo si aún no existe en destino (no pisa ajustes del
   cliente). Si no hay, el agente usa sus defaults.
3. Registra una **Tarea Programada** `PuntoNube Print` con
   `schtasks /create ... /sc onlogon /rl limited /f`. El disparador `onlogon`
   hace que el agente **arranque solo cada vez que el usuario inicia sesión**:
   responde directamente a la "pregunta del millón" del cliente — si se apaga y
   se prende el equipo, el agente **vuelve a arrancar solo**, NO es ejecución
   única. El `/f` sobrescribe la tarea si ya existe (idempotencia).
4. Arranca el agente de inmediato: mata cualquier instancia previa
   (`taskkill`), lanza la tarea (`schtasks /run`) y, como respaldo, hace
   `start` directo del `.exe`.

Si `schtasks /create` falla por permisos, el `.bat` avisa que hay que
ejecutarlo como Administrador.

`desinstalar-agente.bat` (complemento) detiene la tarea y el proceso, borra la
tarea `PuntoNube Print` y elimina `%LOCALAPPDATA%\PuntoNubePrint` (exe, config,
logs). Es tolerante a que algo ya no exista.

**Efecto neto:** instalación una sola vez, arranque automático permanente con
Windows, cero mantenimiento diario — exactamente lo que pidió el cliente para no
tener que atender el agente en cada reinicio.

## (e) Pasos EXACTOS para el cliente

### 1. Generar el `.exe` final (una vez, en la máquina de build / dev)

```cmd
cd "c:\Users\Serch\Documents\My Bussines Pro\mybusiness-POS\.worktrees\agente-exe\print-agent"
npm install
npm run build:exe
```

Esto genera `print-agent\dist\puntonube-print.exe`.

### 2. Instalar en la caja del cliente (una sola vez)

Copiar la carpeta `print-agent` (con `dist\puntonube-print.exe`,
`instalar-agente.bat`, `desinstalar-agente.bat` y `config.json`) a la caja del
cliente y:

```cmd
instalar-agente.bat
```

(Si Windows pide permisos para crear la tarea: clic derecho →
"Ejecutar como administrador").

Verificar en el navegador de la caja: `http://127.0.0.1:9100/status` → debe
responder `200`.

A partir de ahí el agente arranca solo con Windows en cada reinicio. Para
quitarlo: `desinstalar-agente.bat`.
