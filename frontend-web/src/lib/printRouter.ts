/**
 * Enrutador ÚNICO de impresión (ENTREGABLE 2).
 *
 * Cada punto de impresión del POS llama aquí. La estrategia es:
 *  1) Si el agente local está disponible, imprime vía agente (RAW ESC/POS, SIN diálogo).
 *  2) Si el agente no está o la impresión por agente falla, cae al método por navegador
 *     (printTicketHtml), que es el fallback existente e intacto.
 *
 * NO BLOQUEANTE (FEAT-002): la impresión es "fire-and-forget". Las funciones públicas
 * (printSale/printVoucher) devuelven void DE INMEDIATO y hacen la detección + envío al agente en
 * segundo plano. Así la caja NUNCA se congela esperando al agente ni al diálogo del navegador. El
 * resultado se comunica por toast; un fallo de impresión jamás afecta a la venta (que ya se
 * registró antes con await api.post('/sales')).
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
import { toast } from '@/store/toast';

/** Normaliza el ancho actual del rollo (58/80) para el agente. */
function anchoActual(): 58 | 80 {
  return getPaperWidth() === 58 ? 58 : 80;
}

/**
 * Fallback por navegador NO bloqueante: abre la ventana aislada de impresión. Si un bloqueador de
 * pop-ups lo impide, avisa con un toast y la caja sigue usable. Nunca lanza.
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
 * Ejecuta la impresión en segundo plano: intenta el agente local; si no está o falla, cae al
 * navegador. Toda la lógica async queda encapsulada aquí para que los llamadores no la esperen.
 */
async function ejecutarEnSegundoPlano(
  intentarAgente: () => Promise<boolean>,
  htmlFallback: () => string,
): Promise<void> {
  try {
    if (await isAgentAvailable()) {
      const ok = await intentarAgente();
      if (ok) return; // Impresión directa por el agente: silenciosa (sin diálogo ni toast).
      // El agente estaba disponible pero el envío falló/expiró: avisar y seguir al fallback.
      toast.error('Falló la impresión directa', 'Se intentará imprimir por el navegador.');
    } else {
      toast.info('Impresión por el navegador', 'No se detectó el agente de impresión directa.');
    }
  } catch {
    // isAgentAvailable/printViaAgent no lanzan, pero por robustez tratamos cualquier error como
    // "sin agente" y caemos al navegador. La caja nunca se bloquea por esto.
    toast.info('Impresión por el navegador', 'No se detectó el agente de impresión directa.');
  }
  fallbackNavegador(htmlFallback());
}

/**
 * Imprime un ticket de VENTA en segundo plano (fire-and-forget). Retorna void de inmediato: la
 * detección del agente y el envío ocurren sin que la UI los espere, así la caja nunca se congela.
 */
export function printSale(
  settings: TicketSettings,
  d: TicketData,
  opts?: { cashDrawer?: boolean },
): void {
  void ejecutarEnSegundoPlano(
    () => printViaAgent(mapSaleToAgent(settings, d), anchoActual(), { cashDrawer: opts?.cashDrawer }),
    () => saleTicketHtml(settings, d),
  );
}

/**
 * Imprime un COMPROBANTE genérico en segundo plano (fire-and-forget). Retorna void de inmediato.
 */
export function printVoucher(v: VoucherData): void {
  void ejecutarEnSegundoPlano(
    () => printViaAgent(mapVoucherToAgent(v), anchoActual()),
    () => voucherHtml(v),
  );
}
