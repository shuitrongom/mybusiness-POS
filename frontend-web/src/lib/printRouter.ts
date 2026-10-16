/**
 * Enrutador ÚNICO de impresión.
 *
 * TICKET DE VENTA (printSale): SOLO imprime por el agente local (RAW ESC/POS, SIN diálogo). Si el
 * agente no está o no hay impresora disponible, NO cae al diálogo del navegador (decisión de
 * producto: ese diálogo confunde al cajero). En su lugar DEVUELVE el resultado para que el POS
 * muestre su propio modal premium ("cambio a entregar" + reimprimir después desde Reimpresión).
 *
 * COMPROBANTES (printVoucher: cortes, movimientos, devoluciones): conservan el fallback por
 * navegador intacto, porque los usa administración y no es el flujo de caja del cajero.
 *
 * La impresión del ticket de venta ya NO es fire-and-forget: printSale es async y el llamador
 * puede await-earlo para decidir el modal. Aun así nunca lanza ni bloquea la venta (que se
 * registró antes con await api.post('/sales')); cualquier error se resuelve a un PrintResult.
 */
import type { TicketSettings, TicketData } from '@/components/Ticket';
import type { VoucherData } from '@/components/Voucher';
import { getPaperWidth, printTicketHtml } from '@/lib/thermalPrint';
import { voucherHtml } from '@/lib/ticketHtml';
import {
  isAgentAvailable,
  printViaAgent,
  mapSaleToAgent,
  mapVoucherToAgent,
} from '@/lib/localPrint';
import { toast } from '@/store/toast';

/**
 * Resultado de imprimir el ticket de venta por el agente:
 *  - 'printed'    : el agente imprimió correctamente (impresora conectada y lista).
 *  - 'no-printer' : no se pudo imprimir (agente ausente, impresora apagada/desconectada o error
 *                   del spooler). La venta ya quedó guardada; el ticket se puede reimprimir luego.
 */
export type PrintResult = 'printed' | 'no-printer';

/** Normaliza el ancho actual del rollo (58/80) para el agente. */
function anchoActual(): 58 | 80 {
  return getPaperWidth() === 58 ? 58 : 80;
}

/**
 * Fallback por navegador NO bloqueante (SOLO comprobantes): abre la ventana aislada de impresión.
 * Si un bloqueador de pop-ups lo impide, avisa con un toast y la caja sigue usable. Nunca lanza.
 */
function fallbackNavegador(html: string): void {
  const abierta = printTicketHtml(html);
  if (!abierta) {
    toast.error(
      'No se pudo abrir la impresión',
      'Tu navegador bloqueó la ventana. Permite las ventanas emergentes de este sitio e inténtalo de nuevo.',
    );
  }
}

/**
 * Imprime el ticket de VENTA SOLO por el agente local y devuelve el resultado. Nunca lanza: ante
 * cualquier error/timeout o ausencia de impresora, resuelve 'no-printer' para que el POS muestre
 * el modal de "cambio a entregar". NO usa el diálogo del navegador.
 */
export async function printSale(
  settings: TicketSettings,
  d: TicketData,
  opts?: { cashDrawer?: boolean },
): Promise<PrintResult> {
  try {
    if (!(await isAgentAvailable())) return 'no-printer';
    const ok = await printViaAgent(mapSaleToAgent(settings, d), anchoActual(), { cashDrawer: opts?.cashDrawer });
    return ok ? 'printed' : 'no-printer';
  } catch {
    // isAgentAvailable/printViaAgent no lanzan, pero por robustez cualquier error = sin impresora.
    return 'no-printer';
  }
}

/**
 * Imprime un COMPROBANTE genérico (corte, movimiento, devolución, traspaso). Intenta el agente y,
 * si no está disponible o falla, cae al navegador (flujo de administración, intacto). Retorna
 * void de inmediato (fire-and-forget).
 */
export function printVoucher(v: VoucherData): void {
  void (async () => {
    try {
      if (await isAgentAvailable()) {
        const ok = await printViaAgent(mapVoucherToAgent(v), anchoActual());
        if (ok) return;
        toast.error('Falló la impresión directa', 'Se intentará imprimir por el navegador.');
      } else {
        toast.info('Impresión por el navegador', 'No se detectó el agente de impresión directa.');
      }
    } catch {
      toast.info('Impresión por el navegador', 'No se detectó el agente de impresión directa.');
    }
    fallbackNavegador(voucherHtml(v));
  })();
}
