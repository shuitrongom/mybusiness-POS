/**
 * escpos.test.js
 * Pruebas de los formateadores ESC/POS con node:test. No requieren red ni
 * impresora fisica.
 */

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { buildTicketBuffer, buildOpenDrawerBuffer, colsFor } = require('./escpos.js');

// Secuencias clave.
const INIT = Buffer.from([0x1b, 0x40]); // ESC @
const CUT_PREFIX = Buffer.from([0x1d, 0x56]); // GS V
const ESC_P = Buffer.from([0x1b, 0x70]); // ESC p (pulso de cajon)

/**
 * Ticket de ejemplo valido conforme al esquema congelado de FEAT-001.
 */
function ticketEjemplo() {
  return {
    header: { nombreNegocio: 'Panaderia La Espiga', direccion: 'Av. Siempre Viva 123' },
    meta: { folio: 'A-001', fechaHora: '2026-10-12 13:00', cajero: 'Serch' },
    lineas: [
      { cantidad: 2, descripcion: 'Pan dulce', importe: 24.0 },
      { cantidad: 1, descripcion: 'Cafe americano grande', importe: 35.5 },
    ],
    totales: {
      total: 59.5,
      metodoPago: 'Efectivo',
      recibido: 100,
      cambio: 40.5,
      filasExtra: [{ etiqueta: 'Subtotal', valor: '$59.50' }],
    },
    footer: { mensajePie: 'Gracias por su compra', leyendaNoFiscal: 'Ticket no fiscal' },
    flags: { cashDrawer: false },
  };
}

test('buildTicketBuffer devuelve un Buffer no vacio que inicia con INIT y termina con corte GS V', () => {
  const buf = buildTicketBuffer(ticketEjemplo(), 80);
  assert.ok(Buffer.isBuffer(buf), 'debe ser un Buffer');
  assert.ok(buf.length > 0, 'no debe estar vacio');
  assert.deepEqual(buf.subarray(0, 2), INIT, 'debe iniciar con ESC @');
  // El corte GS V esta al final (seguido de los 2 bytes del modo y nivel).
  const idxCut = buf.lastIndexOf(CUT_PREFIX);
  assert.ok(idxCut >= 0, 'debe contener GS V');
  assert.ok(idxCut >= buf.length - 4, 'el corte debe estar al final del buffer');
});

test('las lineas quedan alineadas al ancho de 48 chars para 80mm', () => {
  const buf = buildTicketBuffer(ticketEjemplo(), 80);
  assert.equal(colsFor(80), 48);
  const texto = buf.toString('latin1');
  // La fila de producto 'cantidad x descripcion ... importe' debe ocupar 48 cols.
  const lineaProducto = texto.split('\n').find((l) => l.includes('x Pan dulce'));
  assert.ok(lineaProducto, 'debe existir la linea del producto');
  assert.equal(lineaProducto.length, 48, 'la fila debe tener exactamente 48 columnas');
  assert.ok(lineaProducto.endsWith('$24.00'), 'el importe debe quedar pegado a la derecha');
});

test('las lineas quedan alineadas al ancho de 32 chars para 58mm', () => {
  const buf = buildTicketBuffer(ticketEjemplo(), 58);
  assert.equal(colsFor(58), 32);
  const texto = buf.toString('latin1');
  const lineaProducto = texto.split('\n').find((l) => l.includes('x Pan dulce'));
  assert.ok(lineaProducto, 'debe existir la linea del producto');
  assert.equal(lineaProducto.length, 32, 'la fila debe tener exactamente 32 columnas');
  assert.ok(lineaProducto.endsWith('$24.00'), 'el importe debe quedar pegado a la derecha');
});

test('con flags.cashDrawer=true el buffer contiene la secuencia ESC p', () => {
  const t = ticketEjemplo();
  t.flags.cashDrawer = true;
  const buf = buildTicketBuffer(t, 80);
  assert.ok(buf.indexOf(ESC_P) >= 0, 'debe contener ESC p cuando cashDrawer es true');
});

test('sin cashDrawer el buffer NO contiene ESC p', () => {
  const buf = buildTicketBuffer(ticketEjemplo(), 80);
  assert.equal(buf.indexOf(ESC_P), -1, 'no debe contener ESC p');
});

test('un ticket con campos faltantes no lanza y produce un Buffer valido', () => {
  assert.doesNotThrow(() => {
    const buf = buildTicketBuffer({}, 80);
    assert.ok(Buffer.isBuffer(buf));
    assert.deepEqual(buf.subarray(0, 2), INIT);
  });
  assert.doesNotThrow(() => buildTicketBuffer(undefined, 58));
  assert.doesNotThrow(() => buildTicketBuffer(null, 80));
});

test('buildOpenDrawerBuffer inicia con INIT y contiene ESC p', () => {
  const buf = buildOpenDrawerBuffer();
  assert.ok(Buffer.isBuffer(buf));
  assert.deepEqual(buf.subarray(0, 2), INIT);
  assert.ok(buf.indexOf(ESC_P) >= 0, 'debe contener el pulso de cajon');
});
