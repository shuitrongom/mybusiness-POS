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

## Instalacion para el cliente

### Opcion A — Ejecutable (recomendada, sin instalar Node)

1. Copia `puntonube-print.exe` (generado con `npm run build:exe`) a una carpeta
   fija, por ejemplo `C:\PuntoNube\`.
2. Haz doble clic. Se abre una ventana que indica que esta escuchando en
   `http://127.0.0.1:9100`.

### Opcion B — Con Node.js instalado

Requiere Node.js v18 o superior (probado con v24).

```cmd
cd C:\ruta\a\print-agent
npm install
npm start
```

> El agente **no tiene dependencias de runtime**: usa solo modulos nativos de
> Node. `npm install` no descarga nada obligatorio. `pkg` (para el `.exe`) es
> opcional; instalalo con `npm install -g pkg` solo si vas a empaquetar.

---

## Dejarlo iniciando con Windows

Para que el agente este siempre disponible al prender la caja:

- **Carpeta de Inicio**: presiona `Win + R`, escribe `shell:startup` y Enter.
  Pega ahi un acceso directo a `puntonube-print.exe` (o a un `.bat` que haga
  `cd` + `npm start`).
- **Programador de tareas**: crea una tarea que ejecute el `.exe` al iniciar
  sesion, con "Ejecutar aunque el usuario no haya iniciado sesion" si aplica.

---

## Configuracion

El agente lee `config.json` (misma carpeta que el ejecutable / el proyecto):

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
Windows) desde PowerShell con `Add-Type`: se abre la impresora por nombre con
`OpenPrinter`, se inicia un documento con datatype `"RAW"` (`StartDocPrinter`),
se escribe el buffer con `WritePrinter` y se cierra. Es el metodo mas robusto
**sin compilar binarios nativos** (solo PowerShell + el .NET del sistema, que
ya vienen en Windows) e imprime **por nombre de impresora**, sin depender de
que este compartida.

El buffer se escribe primero a un archivo temporal `.prn` en `%TEMP%` y el
script PowerShell lo lee con `ReadAllBytes`, para evitar problemas de
codificacion al pasar bytes por la linea de comandos. El temporal se borra al
terminar. Hay un timeout de 15 s; errores del spooler (impresora inexistente,
apagada o sin papel) se devuelven como mensaje legible.

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

## Empaquetado a .exe (opcional, no bloqueante)

```cmd
npm install -g pkg
npm run build:exe
```

Genera `dist/puntonube-print.exe`. El `.exe`, la carpeta `logs/`, los `.prn` y
`node_modules/` **no se versionan** (ver `.gitignore`).

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

El agente escribe a consola y a `logs/agent-YYYY-MM-DD.log`. Util para
diagnosticar impresiones fallidas en la caja del cliente.
