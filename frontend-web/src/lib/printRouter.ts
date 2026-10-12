/**
 * Enrutador ÚNICO de impresión (ENTREGABLE 2).
 *
 * Cada punto de impresión del POS llama aquí. La estrategia es:
 *  1) Si el agente local está disponible, imprime vía agente (RAW ESC/POS, SIN diálogo).
 *  2) Si el agente no está o la impresión por agente falla, cae al método por navegador
 *     (printTicketHtml), que es el fallback existente e intacto.
 *
 * Este módulo NO elimina ni modifica thermalPrint.ts ni ticketHtml.ts: solo los orquesta.
 */
import type { TicketSettings, TicketData } from '@/components/Ticket';
import type { VoucherData } from '@/components/Voucher';
import { getPaperWidth, printTicketHtml } from '@/lib/thermalPrint';
import { saleTicketHtml, voucherHtml } from '@/lib/ticketHtml';
import {
  isAgentAvailable,
  printViaAgent,
  mapSaleToAgent,
  mapVoucherToAgent,
} from '@/lib/localPrint';

/** Normaliza el ancho actual del rollo (58/80) para el agente. */
function anchoActual(): 58 | 80 {
  return getPaperWidth() === 58 ? 58 : 80;
}

/**
 * Imprime un ticket de VENTA. Intenta primero el agente local; si no está disponible o falla,
 * cae al método por navegador.
 */
export async function printSale(
  settings: TicketSettings,
  d: TicketData,
  opts?: { cashDrawer?: boolean },
): Promise<void> {
  if (await isAgentAvailable()) {
    const ok = await printViaAgent(mapSaleToAgent(settings, d), anchoActual(), {
      cashDrawer: opts?.cashDrawer,
    });
    if (ok) return;
  }
  printTicketHtml(saleTicketHtml(settings, d));
}

/**
 * Imprime un COMPROBANTE genérico. Intenta primero el agente local; si no está disponible o
 * falla, cae al método por navegador.
 */
export async function printVoucher(v: VoucherData): Promise<void> {
  if (await isAgentAvailable()) {
    const ok = await printViaAgent(mapVoucherToAgent(v), anchoActual());
    if (ok) return;
  }
  printTicketHtml(voucherHtml(v));
}
