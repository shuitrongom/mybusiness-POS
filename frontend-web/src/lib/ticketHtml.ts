import type { TicketSettings, TicketData } from '@/components/Ticket';
import type { VoucherData } from '@/components/Voucher';

const money = (n: number) =>
  new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(n || 0);

/** Escapa texto para HTML (evita romper el markup del ticket). */
function esc(s: unknown): string {
  return String(s ?? '').replace(/[&<>"']/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c] as string));
}

/** HTML del ticket de VENTA (clases t-* definidas en thermalPrint). */
export function saleTicketHtml(settings: TicketSettings, d: TicketData): string {
  const itemCount = d.lines.reduce((s, l) => s + l.quantity, 0);
  const store = settings.businessName || d.branchName;
  const dt = d.dateTime.toLocaleString('es-MX', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
  let h = '<div class="t-center">';
  if (settings.showLogo && settings.logoUrl) h += `<img class="t-logo" src="${esc(settings.logoUrl)}" />`;
  h += `<div class="t-store">${esc(store)}</div>`;
  if (settings.headerMessage) h += `<div class="t-tag">${esc(settings.headerMessage)}</div>`;
  if (settings.showAddress && settings.address) h += `<div class="t-info">${esc(settings.address)}</div>`;
  if (settings.showPhone && settings.phone) h += `<div class="t-info">Tel. ${esc(settings.phone)}</div>`;
  if (settings.showRfc && settings.rfc) h += `<div class="t-info">RFC: ${esc(settings.rfc)}</div>`;
  h += '</div>';

  h += '<div class="t-meta">';
  if (settings.showFolio) h += `<span>Folio: ${esc(d.folio)}</span>`;
  h += `<span>${esc(dt)}</span></div>`;
  if (settings.showCashier && d.cashier) {
    h += `<div class="t-meta"><span>Atendió: ${esc(d.cashier)}</span><span>${esc(d.branchName)}</span></div>`;
  }

  h += '<div class="t-div"></div>';
  for (const l of d.lines) {
    h += `<div class="t-line"><span class="d"><b>${esc(l.quantity)}×</b> ${esc(l.description)}</span><span class="a">${esc(money(l.lineTotal))}</span></div>`;
  }
  h += '<div class="t-div"></div>';

  h += `<div class="t-row t-grand"><span>Total (${itemCount} art.)</span><span>${esc(money(d.total))}</span></div>`;
  h += `<div class="t-row"><span>${esc(d.methodLabel)}</span><span>${esc(money(d.received ?? d.total))}</span></div>`;
  if (d.change != null && d.change > 0) h += `<div class="t-row"><span>Cambio</span><span>${esc(money(d.change))}</span></div>`;

  h += '<div class="t-div"></div><div class="t-foot">';
  if (settings.footerMessage) h += `<div>${esc(settings.footerMessage)}</div>`;
  h += '<div class="t-foot-sm">Este comprobante no es una factura fiscal (CFDI).</div></div>';
  return h;
}

/** HTML de un COMPROBANTE genérico (corte, movimiento, devolución, traspaso). */
export function voucherHtml(d: VoucherData): string {
  const dt = d.dateTime.toLocaleString('es-MX', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  });
  let h = `<div class="t-center"><div class="t-store">${esc(d.storeName)}</div><div class="t-tag">${esc(d.title)}</div></div>`;
  h += '<div class="t-meta">';
  if (d.folio) h += `<span>Folio: ${esc(d.folio)}</span>`;
  h += `<span>${esc(dt)}</span></div>`;
  if (d.cashier || d.branchName) {
    h += '<div class="t-meta">';
    if (d.cashier) h += `<span>Responsable: ${esc(d.cashier)}</span>`;
    if (d.branchName) h += `<span>${esc(d.branchName)}</span>`;
    h += '</div>';
  }
  if (d.items && d.items.length) {
    h += '<div class="t-div"></div>';
    for (const it of d.items) {
      h += `<div class="t-line"><span class="d"><b>${esc(it.quantity)}×</b> ${esc(it.description)}</span>`;
      h += it.amount != null ? `<span class="a">${esc(money(it.amount))}</span></div>` : '</div>';
    }
  }
  h += '<div class="t-div"></div>';
  for (const r of d.rows) {
    const cls = r.strong ? 't-row t-grand' : 't-row';
    const style = r.danger ? ' style="color:#b00"' : '';
    h += `<div class="${cls}"><span>${esc(r.label)}</span><span${style}>${esc(r.value)}</span></div>`;
  }
  if (d.note) h += `<div class="t-div"></div><div class="t-info">${esc(d.note)}</div>`;
  if (d.signature) h += '<div class="t-sign"><div class="line">Firma</div></div>';
  h += '<div class="t-div"></div><div class="t-foot">';
  if (d.footer) h += `<div>${esc(d.footer)}</div>`;
  h += '<div class="t-foot-sm">Comprobante interno de respaldo · no es factura fiscal (CFDI).</div></div>';
  return h;
}
