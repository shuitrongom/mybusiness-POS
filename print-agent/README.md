# PuntoNube Print — Agente de impresion local

Agente ligero en Node.js que recibe tickets desde el POS web (PuntoNube) y los
imprime en una impresora termica **POS-80** (80 mm) conectada por USB a la
computadora de la caja, en Windows. Convierte el ticket a comandos **ESC/POS**
y los envia en **RAW** directo al spooler de Windows por nombre de impresora.

El agente escucha **solo en `127.0.0.1:9100`** (loopback), por lo que nadie en
la red puede alcanzarlo: unicamente el navegador de esa misma maquina.

---

## Que resuelve

El POS corre en `https://app.puntonubepos.com`. Imprimir directo a una termica
desde el navegador no es confiable (dialogos, margenes, lentitud). Este agente
hace la impresion nativa y silenciosa: el POS le manda el ticket por `fetch` y
el agente lo saca por la impresora al instante, con corte de papel y apertura
de cajon opcional.

---

## Instalacion para el cliente (un solo clic)

El agente se entrega como un **ejecutable unico** `puntonube-print.exe` (no hace
falta instalar Node en la caja). El instalador lo copia y lo deja arrancando
solo con Windows.

### Pasos

1. En la carpeta del agente ejecuta **`instalar-agente.bat`** (de preferencia
   clic derecho ▸ **Ejecutar como administrador**, porque registra una tarea de
   Windows).
2. Eso es todo. El `.bat`:
   - copia `puntonube-print.exe` y `config.json` a `%LOCALAPPDATA%\PuntoNubePrint\`,
   - registra una **Tarea Programada** llamada `PuntoNube Print` que arranca el
     agente **cada vez que se inicia sesion en Windows**,
   - y arranca el agente de inmediato.

> **La "pregunta del millon" (¿se apaga y ya no funciona?):** NO. Gracias a la
> tarea programada `onlogon`, el agente **vuelve a arrancar solo** cada vez que
> se prende el equipo / se inicia sesion. No es una ejecucion unica: queda
> instalado como tarea de Windows. El cliente no tiene que abrir nada a mano.

Para verificar que esta corriendo, abre en el navegador de esa maquina:
`http://127.0.0.1:9100/status` (debe responder un JSON con `ok: true`).

### Desinstalar

Ejecuta **`desinstalar-agente.bat`** (como administrador). Detiene el agente,
borra la tarea programada y elimina la carpeta `%LOCALAPPDATA%\PuntoNubePrint\`.

### Para desarrollo (con Node.js instalado)

Requiere Node.js v18 o superior (probado con v24). El agente **no tiene
dependencias de runtime** (solo modulos nativos de Node):

```cmd
cd C:\ruta\a\print-agent
npm start
```

---

## Configuracion

Cuando corre como `.exe` instalado, el agente lee `config.json` desde
`%LOCALAPPDATA%\PuntoNubePrint\config.json` (el instalador deja ahi una copia
editable). En desarrollo (`npm start`) lee el `config.json` del proyecto.

```json
{
  "port": 9100,
  "defaultPrinter": "POS-80",
  "widthMm": 80
}
```

- `port`: puerto del agente (default `9100`).
- `defaultPrinter`: nombre EXACTO de la impresora en Windows
  (Panel de control → Dispositivos e impresoras). Por defecto `POS-80`.
- `widthMm`: ancho del papel, `80` o `58`.

Tambien se puede sobrescribir por variables de entorno: `PORT`, `PRINTER`,
`WIDTH_MM`.

---

## Metodo de impresion RAW (por que P/Invoke a winspool.drv)

Para imprimir bytes crudos ESC/POS en una termica se necesita enviar al spooler
sin que Windows "renderice" nada. Las opciones evaluadas:

- `Out-Printer` de PowerShell **no sirve** para RAW (procesa el texto como
  documento).
- Compartir la impresora y `copy /b archivo \\localhost\recurso` funciona solo
  si el share esta habilitado, cosa que no siempre pasa.
- Binarios nativos obligarian a compilar por arquitectura.

El metodo elegido es **P/Invoke a `winspool.drv`** (la API del spooler de
Windows) desde PowerShell: se abre la impresora por nombre con `OpenPrinter`,
se inicia un documento con datatype `"RAW"` (`StartDocPrinter`), se escribe el
buffer con `WritePrinter` y se cierra. Es el metodo mas robusto **sin compilar
binarios nativos** (solo PowerShell + el .NET del sistema, que ya vienen en
Windows) e imprime **por nombre de impresora**, sin depender de que este
compartida.

El buffer se escribe primero a un archivo temporal `.prn` en `%TEMP%`, el
worker lo lee con `ReadAllBytes` y el temporal se borra al terminar.

## Arquitectura: worker persistente + cola en serie + cache de /status

La version anterior compilaba el helper C# (`Add-Type`) en **cada** impresion,
lo que agregaba 2-5 s por ticket y, bajo carga, hacia que el POS pareciera
"colgado". Esta version lo elimina:

- **Worker PowerShell persistente** (`src/raw-print-worker.ps1`): un proceso de
  larga vida que hace `Add-Type` del helper `RawPrinterHelper` **una sola vez**
  al arrancar. Luego recibe trabajos por `stdin` (una linea JSON
  `{ id, printer, file }` por trabajo) y responde por `stdout`
  (`{ id, ok }` o `{ id, ok:false, error }`). `rawPrint.js` lanza el worker la
  primera vez que se necesita y lo mantiene vivo; si el proceso muere, se
  relanza en la siguiente impresion. Tras el arranque, cada impresion solo paga
  el envio al spooler (tipicamente < 1 s).
- **Cola FIFO en serie con timeout por trabajo**: `printRaw()` encola el
  trabajo y los procesa de a uno. Cada trabajo tiene un timeout (20 s) que lo
  rechaza con un mensaje legible **sin bloquear los siguientes**: un ticket que
  falla, expira o va a una impresora apagada no detiene la cola.
- **Cache de impresoras**: `listPrinters()` devuelve de inmediato una lista en
  memoria. Un refresco en background ejecuta `Get-Printer`/`wmic` al arrancar
  (via `startPrinterCache()`, llamado al escuchar) y cada ~5 min, por lo que
  `GET /status` responde en milisegundos y nunca lanza PowerShell en linea.

### IMPORTANTE: reiniciar el agente para tomar esta version

El agente carga el worker y el helper C# **al arrancar**. Para que el cliente
tome esta version debe **detener y volver a arrancar** el agente:

- Opcion B (Node): cierra la ventana / proceso `npm start` y vuelve a ejecutar
  `npm start`.
- Opcion A (`.exe`): cierra el `puntonube-print.exe` en ejecucion y vuelve a
  abrirlo. Si el `.exe` se re-empaqueta, hay que **reinstalar/re-empaquetar**
  (`npm run build:exe`) y reemplazar el ejecutable; el `.ps1` del worker ya se
  incluye como asset del paquete.

Errores del spooler (impresora inexistente, apagada o sin papel) se siguen
devolviendo como mensaje legible por trabajo.

---

## Mixed content (por que https puede hablar con http://127.0.0.1)

Normalmente una pagina `https://` no puede hacer `fetch` a un `http://` (mixed
content). La excepcion: los navegadores modernos (Chrome, Edge, Firefox)
tratan `http://127.0.0.1` y `http://localhost` como **secure context /
"potentially trustworthy"**, por lo que **no** bloquean la peticion desde
`https://app.puntonubepos.com` hacia `http://127.0.0.1:9100`.

Se usa `127.0.0.1` (no `localhost`) por consistencia de DNS. Si algun navegador
o politica corporativa llegara a bloquear la peticion, el POS **cae
automaticamente** al metodo de impresion por navegador (el fallback HTML sigue
intacto en el frontend).

---

## API

### `GET /status`

```json
{ "ok": true, "version": "1.0.0", "printers": ["POS-80", "Microsoft Print to PDF"] }
```

Devuelve la lista de impresoras instaladas. Si no se pueden listar, responde
`printers: []` (no falla).

### `POST /print`

Cuerpo (esquema congelado — contrato con el frontend):

```json
{
  "printer": "POS-80",
  "widthMm": 80,
  "openDrawerOnly": false,
  "ticket": {
    "header": {
      "nombreNegocio": "Panaderia La Espiga",
      "direccion": "Av. Siempre Viva 123",
      "telefono": "555-123-4567",
      "rfc": "XAXX010101000",
      "mensajeEncabezado": "Bienvenido"
    },
    "meta": {
      "folio": "A-001",
      "fechaHora": "2026-10-12 13:00",
      "cajero": "Serch",
      "sucursal": "Centro"
    },
    "lineas": [
      { "cantidad": 2, "descripcion": "Pan dulce", "importe": 24.0 },
      { "cantidad": 1, "descripcion": "Cafe americano", "importe": 35.5 }
    ],
    "totales": {
      "total": 59.5,
      "metodoPago": "Efectivo",
      "recibido": 100,
      "cambio": 40.5,
      "filasExtra": [{ "etiqueta": "Subtotal", "valor": "$59.50" }]
    },
    "footer": {
      "mensajePie": "Gracias por su compra",
      "leyendaNoFiscal": "Ticket no fiscal"
    },
    "flags": { "logo": false, "cashDrawer": true }
  }
}
```

Respuestas:

- `200 { "ok": true }` — impreso.
- `400 { "ok": false, "error": "JSON invalido" }` — el cuerpo no es JSON valido.
- `500 { "ok": false, "error": "..." }` — fallo del spooler (impresora apagada,
  sin papel, nombre incorrecto, etc.).

Para abrir solo el cajon: `{ "openDrawerOnly": true, "printer": "POS-80" }`.

---

## Empaquetado a .exe

El agente esta escrito en **CommonJS** (no ESM) para que `pkg` pueda generar un
`.exe` de un solo archivo 100% funcional. Se empaqueta con
**[@yao-pkg/pkg](https://www.npmjs.com/package/@yao-pkg/pkg)** (el fork
mantenido del `pkg` de Vercel, con soporte para Node 20/22; el `pkg` original
quedo en Node 18). Esta declarado como `devDependency`, asi que:

```cmd
npm install
npm run build:exe
```

Genera `dist/puntonube-print.exe` con target **node22-win-x64**. El `.exe`, la
carpeta `logs/`, los `.prn` y `node_modules/` **no se versionan**
(ver `.gitignore`); el ejecutable se regenera con el comando de arriba.

### Arquitectura CommonJS + pkg + asset del worker

- **CommonJS:** todos los `.js` usan `require`/`module.exports` y `__dirname`
  nativo (sin `import`/`export` ni `import.meta.url`). Esto evita los errores
  `ERR_MODULE_NOT_FOUND` que daba el `.exe` cuando el codigo era ESM.
- **Asset `raw-print-worker.ps1` bajo pkg:** empaquetado, los assets viven en un
  "snapshot" virtual de solo lectura (`C:\snapshot\...`) que `fs` puede leer
  pero `powershell.exe -File` **no** puede ejecutar. Por eso, al necesitar el
  worker por primera vez, el agente **lee** el `.ps1` del snapshot con `fs` y lo
  **copia a disco real** (`%LOCALAPPDATA%\PuntoNubePrint\raw-print-worker.ps1`),
  y lanza PowerShell apuntando a esa copia real. Funciona igual en desarrollo
  (`npm start`) y empaquetado.
- **Logs y config:** cuando corre como `.exe` se escriben/leen en
  `%LOCALAPPDATA%\PuntoNubePrint\` (carpeta real escribible), no en el snapshot.
- **Arranque automatico:** `instalar-agente.bat` registra una Tarea Programada
  `onlogon`, de modo que el agente arranca solo al iniciar sesion. El reinicio
  del worker PowerShell si muere ya es interno (`rawPrint.js` lo relanza en la
  siguiente impresion).

---

## Pruebas

```cmd
npm test
```

Corre las pruebas de `node:test` sobre los formateadores ESC/POS (no requieren
red ni impresora). El envio RAW real a la **POS-80** se valida con la impresora
fisica del cliente.

---

## Logs

El agente escribe a consola y a un archivo diario `agent-YYYY-MM-DD.log`. En
desarrollo (`npm start`) vive en `print-agent/logs/`; como `.exe` instalado vive
en `%LOCALAPPDATA%\PuntoNubePrint\logs\`. Util para diagnosticar impresiones
fallidas en la caja del cliente.
