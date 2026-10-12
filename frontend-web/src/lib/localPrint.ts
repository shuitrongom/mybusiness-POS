/**
 * Capa de impresión vía el AGENTE LOCAL "PuntoNube Print" (ENTREGABLE 2).
 *
 * El agente corre en la PC del cliente escuchando en http://127.0.0.1:9100 (loopback). Los
 * navegadores modernos tratan 127.0.0.1 como contexto seguro, así que el POS (https) puede
 * hablarle sin que el navegador lo bloquee como "mixed content". Este módulo:
 *  - Detecta si el agente está disponible (GET /status) con caché corta.
 *  - Envía el ticket al agente (POST /print) para impresión RAW ESC/POS SIN diálogo.
 *  - Convierte los datos del frontend (TicketSettings+TicketData y VoucherData) al ESQUEMA JSON
 *    EXACTO que define el agente (FEAT-001), sin renombrar campos.
 *
 * Si el agente no responde, el enrutador (printRouter.ts) cae al método por navegador
 * (printTicketHtml). Este módulo nunca lanza: cualquier error de red/timeout -> false.
 */
import type { TicketSettings, TicketData } from '@/components/Ticket';
import type { VoucherData } from '@/components/Voucher';

/** URL del agente local. Se usa 127.0.0.1 (no 'localhost') por consistencia de secure-context. */
export const AGENT_URL = 'http://127.0.0.1:9100';

/** Ticket en el ESQUEMA JSON EXACTO del agente (FEAT-001). No renombrar campos. */
export interface AgentTicket {
  header: {
    nombreNegocio: string;
    direccion?: string | null;
    telefono?: string | null;
    rfc?: string | null;
    mensajeEncabezado?: string | null;
  };
  meta: {
    folio?: string | null;
    fechaHora: string;
    cajero?: string | null;
    sucursal?: string | null;
  };
  lineas: { cantidad: number; descripcion: string; importe: number }[];
  totales: {
    total: number;
    metodoPago: string;
    recibido?: number | null;
    cambio?: number | null;
    filasExtra?: { etiqueta: string; valor: string }[];
  };
  footer: {
    mensajePie?: string | null;
    leyendaNoFiscal?: string | null;
  };
  flags?: {
    logo?: boolean;
    cashDrawer?: boolean;
  };
}

/** Formato de fecha/hora común a ticket de venta y comprobante (es-MX). */
function formatoFecha(d: Date): string {
  return d.toLocaleString('es-MX', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
}

// Caché en memoria del estado del agente: evita golpear /status en cada impresión.
let estadoCache: { disponible: boolean; ts: number } | null = null;
const CACHE_MS = 5000;

/**
 * Indica si el agente local está disponible (GET /status). Cachea el resultado ~5s. Cualquier
 * error o timeout (~1200ms) devuelve false.
 */
export async function isAgentAvailable(): Promise<boolean> {
  const ahora = Date.now();
  if (estadoCache && ahora - estadoCache.ts < CACHE_MS) return estadoCache.disponible;

  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), 1200);
  try {
    const res = await fetch(`${AGENT_URL}/status`, { method: 'GET', signal: ctrl.signal });
    const ok = res.ok;
    estadoCache = { disponible: ok, ts: ahora };
    return ok;
  } catch {
    estadoCache = { disponible: false, ts: ahora };
    return false;
  } finally {
    clearTimeout(t);
  }
}

/**
 * Envía el ticket al agente para impresión RAW (sin diálogo). Devuelve true solo si el agente
 * respondió ok. Cualquier error/timeout (~8s) -> false (el enrutador caerá al navegador).
 */
export async function printViaAgent(
  ticket: AgentTicket,
  widthMm: 58 | 80,
  opts?: { printer?: string; cashDrawer?: boolean },
): Promise<boolean> {
  const payload: AgentTicket = {
    ...ticket,
    flags: { ...ticket.flags, cashDrawer: opts?.cashDrawer ?? ticket.flags?.cashDrawer ?? false },
  };

  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), 8000);
  try {
    const res = await fetch(`${AGENT_URL}/print`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ printer: opts?.printer, widthMm, ticket: payload }),
      signal: ctrl.signal,
    });
    if (!res.ok) return false;
    const data = (await res.json()) as { ok?: boolean };
    return data.ok === true;
  } catch {
    return false;
  } finally {
    clearTimeout(t);
  }
}

/**
 * Convierte la configuración + datos de una VENTA al esquema del agente, respetando los mismos
 * toggles de TicketSettings que usa saleTicketHtml.
 */
export function mapSaleToAgent(settings: TicketSettings, d: TicketData): AgentTicket {
  return {
    header: {
      nombreNegocio: settings.businessName || d.branchName,
      direccion: settings.showAddress ? settings.address : null,
      telefono: settings.showPhone ? settings.phone : null,
      rfc: settings.showRfc ? settings.rfc : null,
      mensajeEncabezado: settings.headerMessage,
    },
    meta: {
      folio: settings.showFolio ? d.folio : null,
      fechaHora: formatoFecha(d.dateTime),
      cajero: settings.showCashier ? (d.cashier ?? null) : null,
      sucursal: d.branchName,
    },
    lineas: d.lines.map((l) => ({
      cantidad: l.quantity,
      descripcion: l.description,
      importe: l.lineTotal,
    })),
    totales: {
      total: d.total,
      metodoPago: d.methodLabel,
      recibido: d.received ?? d.total,
      cambio: d.change != null && d.change > 0 ? d.change : null,
    },
    footer: {
      mensajePie: settings.footerMessage,
      leyendaNoFiscal: 'Este comprobante no es una factura fiscal (CFDI).',
    },
    flags: { logo: settings.showLogo, cashDrawer: false },
  };
}

/**
 * Convierte un COMPROBANTE genérico (corte, movimiento, devolución, traspaso) al esquema del
 * agente. Como el comprobante no tiene un total único, la fila "fuerte" (strong) se mapea a
 * totales.total y el resto de las filas va a totales.filasExtra (valores ya formateados por el
 * frontend; el agente los imprime tal cual).
 */
export function mapVoucherToAgent(v: VoucherData): AgentTicket {
  const filaFuerte = v.rows.find((r) => r.strong);
  // El value del comprobante viene formateado (p.ej. "$88.00"); extraemos el número si se puede.
  const totalNum = filaFuerte
    ? Number(filaFuerte.value.replace(/[^0-9.-]/g, '')) || 0
    : 0;
  const filasExtra = v.rows
    .filter((r) => r !== filaFuerte)
    .map((r) => ({ etiqueta: r.label, valor: r.value }));

  return {
    header: {
      nombreNegocio: v.storeName,
      direccion: null,
      telefono: null,
      rfc: null,
      mensajeEncabezado: v.title,
    },
    meta: {
      folio: v.folio ?? null,
      fechaHora: formatoFecha(v.dateTime),
      cajero: v.cashier ?? null,
      sucursal: v.branchName ?? null,
    },
    lineas: (v.items ?? []).map((it) => ({
      cantidad: it.quantity,
      descripcion: it.description,
      importe: it.amount ?? 0,
    })),
    totales: {
      total: totalNum,
      metodoPago: filaFuerte ? filaFuerte.label : '',
      filasExtra,
    },
    footer: {
      mensajePie: v.footer ?? null,
      leyendaNoFiscal: 'Comprobante interno de respaldo. No es factura fiscal (CFDI).',
    },
    flags: { cashDrawer: false },
  };
}
