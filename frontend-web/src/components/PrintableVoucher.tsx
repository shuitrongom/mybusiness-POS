import { useEffect } from 'react';
import { type VoucherData } from '@/components/Voucher';
import { printTicketHtml } from '@/lib/thermalPrint';
import { voucherHtml } from '@/lib/ticketHtml';

/**
 * Imprime un comprobante en el rollo térmico (58/80 mm) usando una ventana aislada: NO renderiza
 * nada en pantalla, construye el HTML del comprobante y lo manda al rollo con el tamaño correcto.
 * Tras disparar la impresión avisa con onDone.
 */
export function PrintableVoucher({ data, onDone }: { data: VoucherData; onDone: () => void }) {
  useEffect(() => {
    printTicketHtml(voucherHtml(data));
    const t = setTimeout(onDone, 1200);
    return () => clearTimeout(t);
  }, [data, onDone]);

  return null;
}
