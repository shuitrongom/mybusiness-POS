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
    html, body {
      width: ${widthMm}mm;
      height: auto;
      display: block;
      background: #fff; color: #000;
    }
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
 * Imprime un ticket/comprobante en el rollo térmico usando un iframe AISLADO.
 *
 * Técnica robusta y determinista:
 *  - El HTML completo (con sus estilos) se inyecta por `srcdoc`, de modo que el iframe tiene su
 *    propio documento con el @page del rollo y NADA de la app. Así el navegador imprime solo el
 *    ticket, al ancho exacto del rollo.
 *  - Se espera al evento `load` REAL del iframe (no a timeouts adivinados) antes de imprimir, y
 *    además se esperan las imágenes (logo) con `img.decode()`. Esto evita el bug de imprimir el
 *    iframe vacío (hoja en blanco) cuando el print se dispara antes de que el contenido exista.
 *  - Se imprime sobre el contentWindow del iframe, no sobre la página; el diálogo del navegador
 *    es inevitable por seguridad, pero el preview ya sale al tamaño del ticket.
 *
 * @param innerHtml  HTML del cuerpo del ticket (clases t-* de ticketCss).
 */
export function printTicketHtml(innerHtml: string) {
  const w = paperWidthMm;
  const html = `<!doctype html><html><head><meta charset="utf-8">`
    + `<title>Ticket</title><style>${ticketCss(w)}</style></head>`
    + `<body>${innerHtml}</body></html>`;

  const frame = document.createElement('iframe');
  frame.setAttribute('aria-hidden', 'true');
  frame.style.position = 'fixed';
  frame.style.left = '-9999px';
  frame.style.top = '0';
  frame.style.width = `${w}mm`;
  frame.style.height = '0';
  frame.style.border = '0';
  // srcdoc: el contenido existe desde que el iframe carga; dispara un `load` fiable.
  frame.srcdoc = html;

  let done = false;
  const cleanup = () => {
    if (frame.parentNode) frame.parentNode.removeChild(frame);
  };

  const doPrint = async () => {
    if (done) return;
    done = true;
    const win = frame.contentWindow;
    const doc = frame.contentDocument;
    if (!win || !doc) { cleanup(); return; }
    try {
      // Espera a que las imágenes (logo) estén decodificadas, si las hay.
      const imgs = Array.from(doc.images);
      await Promise.all(imgs.map((img) =>
        img.complete ? Promise.resolve()
          : img.decode().catch(() => undefined)));
    } catch { /* si falla la decodificación, imprime igual */ }
    win.focus();
    win.print();
    // Da tiempo a que el diálogo se abra antes de retirar el iframe.
    setTimeout(cleanup, 1500);
  };

  // El `load` del iframe garantiza que srcdoc ya se parseó y renderizó.
  frame.addEventListener('load', () => { void doPrint(); });
  // Respaldo defensivo por si algún navegador no dispara load con srcdoc.
  setTimeout(() => { void doPrint(); }, 1200);

  document.body.appendChild(frame);
}
