/**
 * escpos.js
 * Construccion del buffer ESC/POS a partir del JSON del ticket (esquema
 * congelado definido en FEAT-001 acceptance_criteria, consumido por FEAT-002).
 *
 * Esquema del ticket:
 *   {
 *     printer?: string, widthMm?: 58|80, openDrawerOnly?: boolean,
 *     ticket: {
 *       header:  { nombreNegocio, direccion?, telefono?, rfc?, mensajeEncabezado? },
 *       meta:    { folio?, fechaHora, cajero?, sucursal? },
 *       lineas:  [ { cantidad, descripcion, importe } ],
 *       totales: { total, metodoPago, recibido?, cambio?, filasExtra?:[{etiqueta,valor}] },
 *       footer:  { mensajePie?, leyendaNoFiscal? },
 *       flags?:  { logo?, cashDrawer? }
 *     }
 *   }
 *
 * Codificacion: el texto se emite en 'latin1' (ISO-8859-1). Los acentos del
 * espanol (a,e,i,o,u,n,U) se mapean a CP437 que es el code page por defecto de
 * la mayoria de impresoras termicas POS-80. Si el modelo del cliente usa otro
 * code page, los acentos podrian verse distintos; esta documentado en README.
 */

// ---- Comandos ESC/POS crudos ----
const ESC = 0x1b;
const GS = 0x1d;

const INIT = [ESC, 0x40]; // ESC @  -> reinicia la impresora
const CUT = [GS, 0x56, 0x42, 0x00]; // GS V 66 0 -> corte total con avance
// ESC p 0 25 250 -> pulso de apertura de cajon (pin 2, on=25ms, off=250ms)
const OPEN_DRAWER = [ESC, 0x70, 0x00, 0x19, 0xfa];

const ALIGN_LEFT = [ESC, 0x61, 0x00];
const ALIGN_CENTER = [ESC, 0x61, 0x01];
// const ALIGN_RIGHT = [ESC, 0x61, 0x02]; // disponible si se requiere

const BOLD_ON = [ESC, 0x45, 0x01];
const BOLD_OFF = [ESC, 0x45, 0x00];

const SIZE_NORMAL = [GS, 0x21, 0x00]; // GS ! 0  -> tamano 1x1
const SIZE_DOUBLE = [GS, 0x21, 0x11]; // GS ! 0x11 -> doble alto y doble ancho

const LF = [0x0a];

/**
 * Ancho en caracteres segun el papel: 48 cols para 80mm, 32 para 58mm.
 * @param {number} widthMm
 * @returns {number}
 */
function colsFor(widthMm) {
  return widthMm === 58 ? 32 : 48;
}

/**
 * Mapea una cadena a bytes CP437 (base latin1 + reemplazo de acentos y
 * simbolos comunes del espanol). Caracteres fuera de rango -> '?'.
 * @param {string} str
 * @returns {Buffer}
 */
function toCp437(str) {
  // Tabla CP437 para los caracteres acentuados del espanol y signos usuales.
  const map = {
    'Ç': 0x80, 'ü': 0x81, 'é': 0x82, 'â': 0x83, 'ä': 0x84, 'à': 0x85,
    'å': 0x86, 'ç': 0x87, 'ê': 0x88, 'ë': 0x89, 'è': 0x8a, 'ï': 0x8b,
    'î': 0x8c, 'ì': 0x8d, 'Ä': 0x8e, 'Å': 0x8f, 'É': 0x90, 'ô': 0x93,
    'ö': 0x94, 'ò': 0x95, 'û': 0x96, 'ù': 0x97, 'Ö': 0x99, 'Ü': 0x9a,
    'á': 0xa0, 'í': 0xa1, 'ó': 0xa2, 'ú': 0xa3, 'ñ': 0xa4, 'Ñ': 0xa5,
    'ª': 0xa6, 'º': 0xa7, '¿': 0xa8, '¡': 0xad, '«': 0xae, '»': 0xaf,
  };
  const bytes = [];
  for (const ch of String(str)) {
    if (Object.prototype.hasOwnProperty.call(map, ch)) {
      bytes.push(map[ch]);
    } else {
      const code = ch.charCodeAt(0);
      // ASCII imprimible 0x20-0x7E, mas control util (LF, CR)
      if (code === 0x0a || code === 0x0d || (code >= 0x20 && code <= 0x7e)) {
        bytes.push(code);
      } else {
        bytes.push(0x3f); // '?'
      }
    }
  }
  return Buffer.from(bytes);
}

/**
 * Acumulador de bytes para construir el buffer de impresion de forma legible.
 */
class Writer {
  constructor() {
    /** @type {Buffer[]} */
    this.parts = [];
  }
  /** @param {number[]} arr comando ESC/POS crudo */
  cmd(arr) {
    this.parts.push(Buffer.from(arr));
    return this;
  }
  /** @param {string} text texto a codificar CP437 */
  text(text) {
    this.parts.push(toCp437(text ?? ''));
    return this;
  }
  /** salto de linea */
  nl() {
    this.parts.push(Buffer.from(LF));
    return this;
  }
  /** @param {string} text linea de texto + salto */
  line(text = '') {
    return this.text(text).nl();
  }
  /** @returns {Buffer} */
  toBuffer() {
    return Buffer.concat(this.parts);
  }
}

/**
 * Formatea una fila de dos columnas: etiqueta a la izquierda, importe pegado
 * a la derecha, rellenando con espacios en medio. Trunca la etiqueta si no cabe.
 * @param {string} left
 * @param {string} right
 * @param {number} cols
 * @returns {string}
 */
function twoCols(left, right, cols) {
  const r = String(right ?? '');
  const maxLeft = Math.max(0, cols - r.length - 1);
  let l = String(left ?? '');
  if (l.length > maxLeft) l = l.slice(0, maxLeft);
  const espacios = Math.max(1, cols - l.length - r.length);
  return l + ' '.repeat(espacios) + r;
}

/**
 * Formatea un numero como importe en pesos (es-MX) con 2 decimales.
 * @param {number} n
 * @returns {string}
 */
function money(n) {
  const v = Number.isFinite(n) ? n : 0;
  return '$' + v.toFixed(2);
}

/**
 * Linea separadora de guiones del ancho de columna.
 * @param {number} cols
 */
function sep(cols) {
  return '-'.repeat(cols);
}

/**
 * Construye el buffer ESC/POS completo del ticket.
 * Tolera campos faltantes: nunca lanza por datos incompletos.
 * @param {object} ticket  objeto `ticket` del esquema (no el envoltorio)
 * @param {number} widthMm 58 o 80
 * @returns {Buffer}
 */
function buildTicketBuffer(ticket, widthMm) {
  const cols = colsFor(widthMm);
  const t = ticket && typeof ticket === 'object' ? ticket : {};
  const header = t.header && typeof t.header === 'object' ? t.header : {};
  const meta = t.meta && typeof t.meta === 'object' ? t.meta : {};
  const lineas = Array.isArray(t.lineas) ? t.lineas : [];
  const totales = t.totales && typeof t.totales === 'object' ? t.totales : {};
  const footer = t.footer && typeof t.footer === 'object' ? t.footer : {};
  const flags = t.flags && typeof t.flags === 'object' ? t.flags : {};

  const w = new Writer();
  w.cmd(INIT);

  // ---- Encabezado centrado ----
  w.cmd(ALIGN_CENTER);
  if (header.nombreNegocio) {
    w.cmd(BOLD_ON).cmd(SIZE_DOUBLE).line(header.nombreNegocio).cmd(SIZE_NORMAL).cmd(BOLD_OFF);
  }
  if (header.direccion) w.line(header.direccion);
  if (header.telefono) w.line(`Tel: ${header.telefono}`);
  if (header.rfc) w.line(`RFC: ${header.rfc}`);
  if (header.mensajeEncabezado) w.line(header.mensajeEncabezado);

  // ---- Meta (folio, fecha, cajero, sucursal) alineada a la izquierda ----
  w.cmd(ALIGN_LEFT);
  w.line(sep(cols));
  if (meta.folio) w.line(`Folio: ${meta.folio}`);
  if (meta.fechaHora) w.line(`Fecha: ${meta.fechaHora}`);
  if (meta.cajero) w.line(`Cajero: ${meta.cajero}`);
  if (meta.sucursal) w.line(`Sucursal: ${meta.sucursal}`);
  w.line(sep(cols));

  // ---- Lineas de productos: 'cantidad x descripcion' ... importe ----
  for (const ln of lineas) {
    if (!ln || typeof ln !== 'object') continue;
    const cant = Number.isFinite(ln.cantidad) ? ln.cantidad : 0;
    const desc = ln.descripcion != null ? String(ln.descripcion) : '';
    const importe = money(ln.importe);
    const etiqueta = `${cant} x ${desc}`;
    w.line(twoCols(etiqueta, importe, cols));
  }
  w.line(sep(cols));

  // ---- Totales ----
  // Filas extra (p.ej. subtotal, descuentos, propina, rows de voucher).
  if (Array.isArray(totales.filasExtra)) {
    for (const fila of totales.filasExtra) {
      if (!fila || typeof fila !== 'object') continue;
      w.line(twoCols(String(fila.etiqueta ?? ''), String(fila.valor ?? ''), cols));
    }
  }

  // Total en negritas y tamano doble. Como el tamano doble ocupa el doble de
  // ancho por caracter, el total se formatea sobre la mitad de columnas.
  w.cmd(BOLD_ON).cmd(SIZE_DOUBLE);
  w.line(twoCols('TOTAL', money(totales.total), Math.floor(cols / 2)));
  w.cmd(SIZE_NORMAL).cmd(BOLD_OFF);

  if (totales.metodoPago) w.line(twoCols('Pago', String(totales.metodoPago), cols));
  if (Number.isFinite(totales.recibido)) w.line(twoCols('Recibido', money(totales.recibido), cols));
  if (Number.isFinite(totales.cambio)) w.line(twoCols('Cambio', money(totales.cambio), cols));

  // ---- Pie centrado ----
  w.cmd(ALIGN_CENTER);
  w.line(sep(cols));
  if (footer.mensajePie) w.line(footer.mensajePie);
  if (footer.leyendaNoFiscal) w.line(footer.leyendaNoFiscal);

  // Avance de papel antes del corte.
  w.nl().nl().nl();

  // ---- Cajon opcional antes del corte ----
  if (flags && flags.cashDrawer === true) {
    w.cmd(OPEN_DRAWER);
  }

  // ---- Corte ----
  w.cmd(CUT);

  return w.toBuffer();
}

/**
 * Construye un buffer que solo abre el cajon (INIT + pulso + corte minimo no
 * necesario; se omite el corte para no desperdiciar papel).
 * @returns {Buffer}
 */
function buildOpenDrawerBuffer() {
  const w = new Writer();
  w.cmd(INIT);
  w.cmd(OPEN_DRAWER);
  return w.toBuffer();
}

module.exports = { buildTicketBuffer, buildOpenDrawerBuffer, colsFor };
