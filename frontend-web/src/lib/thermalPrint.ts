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
    .t-test { font-size: ${fontSize + 1}px; font-weight: 800; letter-spacing: 1px; margin: 2px 0; border: 1px dashed #000; padding: 2px 0; }
    .t-tag { font-size: ${fontSize - 1}px; }
    .t-info { font-size: ${fontSize - 1}px; }
    .t-meta { display: flex; justify-content: space-between; gap: 6px; font-size: ${fontSize - 1}px; }
    .t-customer { justify-content: flex-start; }
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
 * Imprime un ticket/comprobante en el rollo térmico abriendo una VENTANA nueva aislada.
 *
 * Por qué ventana nueva y no iframe oculto: el iframe oculto (height:0 / fuera de pantalla) hace
 * que el motor de impresión del navegador no tenga contenido con alto real que renderizar y
 * saque una página en blanco. Una ventana real SÍ renderiza y mide el contenido correctamente,
 * así que el preview sale al tamaño del rollo con el ticket visible. Es el método más compatible.
 *
 * La ventana se abre, imprime y se cierra sola. El diálogo del navegador es inevitable por
 * seguridad; la impresión 100% silenciosa la da el agente local (src/lib/localPrint).
 *
 * NO BLOQUEANTE: el window.print() ocurre SOLO dentro de la ventana aislada (vía su window.onload),
 * nunca sobre el documento principal del POS, así que esta función retorna al instante sin esperar
 * al diálogo. Si un bloqueador de pop-ups impide abrir la ventana, devuelve false (sin lanzar) para
 * que el llamador avise con un toast y la caja siga usable; devuelve true si la ventana se abrió.
 *
 * @param innerHtml  HTML del cuerpo del ticket (clases t-* de ticketCss).
 * @returns true si se abrió la ventana de impresión; false si un bloqueador de pop-ups lo impidió.
 */
export function printTicketHtml(innerHtml: string): boolean {
  const w = paperWidthMm;
  const html = `<!doctype html><html><head><meta charset="utf-8">`
    + `<title>Ticket</title><style>${ticketCss(w)}</style>`
    + `<script>
         function __doPrint(){ try { window.focus(); window.print(); } catch(e){} }
         window.onload = function(){
           // Espera a que las imágenes (logo) carguen antes de imprimir.
           var imgs = Array.prototype.slice.call(document.images);
           var pending = imgs.filter(function(i){ return !i.complete; });
           if (pending.length === 0) { setTimeout(__doPrint, 60); return; }
           var left = pending.length;
           pending.forEach(function(i){
             i.addEventListener('load', done); i.addEventListener('error', done);
           });
           function done(){ if (--left <= 0) setTimeout(__doPrint, 60); }
           // Respaldo por si una imagen nunca resuelve.
           setTimeout(__doPrint, 1500);
         };
         // Cierra la ventana cuando el diálogo de impresión termina (imprimir o cancelar).
         window.onafterprint = function(){ setTimeout(function(){ window.close(); }, 300); };
       </script>`
    + `</head><body>${innerHtml}</body></html>`;

  // Ventana pequeña, fuera del foco visual del cajero. Se cierra sola tras imprimir.
  const win = window.open('', '_blank', `width=380,height=640`);
  if (!win) {
    // Si un bloqueador de pop-ups impide abrir la ventana, se informa en consola y se devuelve
    // false para que el llamador avise con un toast. NO se lanza: el POS debe seguir usable.
    console.warn('No se pudo abrir la ventana de impresión (¿bloqueador de pop-ups?).');
    return false;
  }
  win.document.open();
  win.document.write(html);
  win.document.close();
  return true;
}
