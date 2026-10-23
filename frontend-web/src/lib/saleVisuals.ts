/**
 * Helpers visuales compartidos por las pantallas de ventas (Devoluciones y Reimpresión):
 * avatar estable (color + iniciales), fecha/hora apilada y chips de método de pago. Un solo
 * lugar para "mismo helper de color/iniciales en toda la app" (directrices de diseño) y cero
 * duplicación. Funciones puras, sin estado ni dependencias de React.
 */

// Avatar del artículo/cliente: color estable derivado del nombre + iniciales, coherente con el
// resto de la app (BakeryDayStockPage / InventoryDocPage).
export const AVATAR_COLORS = ['#2e6ef2', '#12b886', '#f59e0b', '#e11d48', '#7c5cff', '#0ea5e9', '#d97706', '#0891b2'];

export function avatarColor(text: string): string {
  let hash = 0;
  for (let i = 0; i < text.length; i++) hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
  return AVATAR_COLORS[hash % AVATAR_COLORS.length];
}

export function avatarInitials(name: string): string {
  const w = name.trim().split(/\s+/).filter(Boolean);
  if (w.length === 0) return '?';
  return (w.length === 1 ? w[0].slice(0, 2) : w[0][0] + w[1][0]).toUpperCase();
}

// Fecha + hora del registro, apiladas (es-MX), coherente con el historial de Entradas/Salidas.
export function fmtDate(iso: string | null): { date: string; time: string } {
  if (!iso) return { date: '—', time: '' };
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return { date: '—', time: '' };
  return {
    date: d.toLocaleDateString('es-MX', { day: '2-digit', month: 'short', year: 'numeric' }),
    time: d.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' }),
  };
}

// Método de pago de la venta → etiqueta es-MX + tono de chip por jerarquía (verde=efectivo,
// sky=tarjeta, morado=transferencia, ámbar=vale). El CSV viene del backend (sale_payment.method).
export const PAY_META: Record<string, { label: string; tone: string }> = {
  CASH: { label: 'Efectivo', tone: 'is-cash' },
  CARD: { label: 'Tarjeta', tone: 'is-card' },
  TRANSFER: { label: 'Transferencia', tone: 'is-transfer' },
  VOUCHER: { label: 'Vale', tone: 'is-voucher' },
};

export function payMethods(csv: string | null): { label: string; tone: string }[] {
  if (!csv) return [];
  return csv.split(',').map((m) => m.trim()).filter(Boolean)
    .map((m) => PAY_META[m] || { label: m, tone: 'is-neutral' });
}
