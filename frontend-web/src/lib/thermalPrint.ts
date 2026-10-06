/**
 * Impresión térmica de rollo (58 / 80 mm) de raíz: ventana de impresión AISLADA.
 *
 * Por qué así y no window.print() de la app: al imprimir la página principal, el navegador
 * arrastra todo el layout y saca una hoja Carta/A4 enorme con el ticket pequeño. La forma
 * robusta —la que usan los sistemas serios— es abrir una ventana que contiene ÚNICAMENTE el
 * ticket, con @page fijado al ancho del rollo y margen 0. Esa ventana no tiene nada más que
 * imprimir, así que el navegador imprime exactamente el ancho del rollo y el alto del contenido.
 *
 * El diálogo del navegador NO se puede evitar desde la web (restricción de seguridad del navegador,
 * ninguna página puede imprimir sin confirmación). Pero con el @page correcto el preview YA sale
 * angosto (tamaño rollo) y el cajero solo confirma. Impresión 100% silenciosa requiere un agente
 * local (pendiente para el futuro).
 */

let paperWidthMm = 80;

export function setPaperWidth(mm: number) {
  paperWidthMm = mm === 58 ? 58 : 80;
}

export function getPaperWidth(): number {
  return paperWidthMm;
}

/** Estilos del ticket embebidos en la ventana de impresión (independientes de la app). */
function ticketCss(widthMm: number): string {
  const fontSize = widthMm === 58 ? 10 : 12;
  const pad = widthMm === 58 ? 2 : 3;
  return `
    @page { size: ${widthMm}mm auto; margin: 0; }
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body { width: ${widthMm}mm; background: #fff; color: #000; }
    body {
      font-family: "Consolas", "Courier New", monospace;
      font-size: ${fontSize}px; line-height: 1.35;
      padding: ${pad}mm;
    }
    .t-center { text-align: center; }
    .t-store { font-size: ${fontSize + 4}px; font-weight: 800; }
    .t-tag { font-size: ${fontSize - 1}px; }
    .t-info { font-size: ${fontSize - 1}px; }
    .t-meta { display: flex; justify-content: space-between; gap: 6px; font-size: ${fontSize - 1}px; }
    .t-div { border-top: 1px dashed #000; margin: ${pad}mm 0; }
    .t-line { display: flex; justify-content: space-between; gap: 6px; }
    .t-line .d { flex: 1; }
    .t-line .a { white-space: nowrap; }
    .t-row { display: flex; justify-content: space-between; }
    .t-grand { font-size: ${fontSize + 3}px; font-weight: 800; }
    .t-foot { text-align: center; font-size: ${fontSize - 1}px; margin-top: ${pad}mm; }
    .t-foot-sm { font-size: ${fontSize - 2}px; color: #333; }
    img.t-logo { max-width: 60%; max-height: 60px; display: block; margin: 0 auto 4px; }
    .t-sign { margin-top: 32px; text-align: center; }
    .t-sign .line { border-top: 1px solid #000; margin: 0 10mm; padding-top: 3px; font-size: ${fontSize - 1}px; }
  `;
}

/**
 * Imprime un ticket/comprobante en el rollo térmico abriendo una ventana aislada con su HTML.
 * @param innerHtml  HTML del cuerpo del ticket (usar las clases t-* de ticketCss).
 */
export function printTicketHtml(innerHtml: string) {
  const w = paperWidthMm;
  // iframe oculto: imprime sin abrir una pestaña visible y sin bloqueadores de pop-ups.
  const frame = document.createElement('iframe');
  frame.style.position = 'fixed';
  frame.style.right = '0';
  frame.style.bottom = '0';
  frame.style.width = '0';
  frame.style.height = '0';
  frame.style.border = '0';
  document.body.appendChild(frame);

  const doc = frame.contentWindow?.document;
  if (!doc) { document.body.removeChild(frame); return; }

  doc.open();
  doc.write(`<!doctype html><html><head><meta charset="utf-8">
    <title>Ticket</title><style>${ticketCss(w)}</style></head>
    <body>${innerHtml}</body></html>`);
  doc.close();

  const cleanup = () => { if (frame.parentNode) document.body.removeChild(frame); };
  const win = frame.contentWindow!;
  // Espera a que carguen imágenes (logo) antes de imprimir.
  const run = () => {
    win.focus();
    win.print();
    setTimeout(cleanup, 1000);
  };
  if (doc.readyState === 'complete') {
    setTimeout(run, 150);
  } else {
    win.onload = () => setTimeout(run, 150);
    setTimeout(run, 500); // respaldo por si onload no dispara
  }
}
