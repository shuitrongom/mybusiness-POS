package com.mybusinesssilva.printing.domain;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/**
 * Constructor de comandos ESC/POS, el estándar que entienden la mayoría de las impresoras
 * térmicas (de marca y genéricas/chinas). Genera el flujo de bytes que el agente de impresión
 * local envía a la impresora.
 *
 * <p>Cubre lo esencial de un ticket de punto de venta: inicialización, alineación, énfasis,
 * tamaño de texto, corte de papel y apertura del cajón de dinero.
 *
 * <p>Usa la página de códigos CP437/Latin por defecto; el agente local puede ajustar la
 * codificación según el modelo si algún carácter acentuado no se imprime bien.
 */
public class EscPosBuilder {

    // Comandos ESC/POS básicos.
    private static final byte ESC = 0x1B;
    private static final byte GS = 0x1D;
    private static final byte LF = 0x0A;

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final Charset charset;

    public EscPosBuilder() {
        this(Charset.forName("CP437"));
    }

    public EscPosBuilder(Charset charset) {
        this.charset = charset;
        // ESC @ : inicializa la impresora (reset).
        out.write(ESC);
        out.write('@');
    }

    /** Alineación: 0 izquierda, 1 centro, 2 derecha. ESC a n */
    public EscPosBuilder align(int mode) {
        out.write(ESC);
        out.write('a');
        out.write(mode);
        return this;
    }

    public EscPosBuilder alignLeft() {
        return align(0);
    }

    public EscPosBuilder alignCenter() {
        return align(1);
    }

    public EscPosBuilder alignRight() {
        return align(2);
    }

    /** Activa/desactiva negritas. ESC E n */
    public EscPosBuilder bold(boolean on) {
        out.write(ESC);
        out.write('E');
        out.write(on ? 1 : 0);
        return this;
    }

    /**
     * Tamaño de texto. width/height 0..7 (0 = normal). GS ! n
     */
    public EscPosBuilder textSize(int width, int height) {
        int w = Math.max(0, Math.min(7, width));
        int h = Math.max(0, Math.min(7, height));
        out.write(GS);
        out.write('!');
        out.write((w << 4) | h);
        return this;
    }

    public EscPosBuilder normalSize() {
        return textSize(0, 0);
    }

    /** Escribe texto (sin salto de línea). */
    public EscPosBuilder text(String value) {
        if (value != null) {
            byte[] bytes = value.getBytes(charset);
            out.write(bytes, 0, bytes.length);
        }
        return this;
    }

    /** Escribe una línea de texto con salto. */
    public EscPosBuilder line(String value) {
        text(value);
        return newLine();
    }

    public EscPosBuilder newLine() {
        out.write(LF);
        return this;
    }

    /** Alimenta n líneas. ESC d n */
    public EscPosBuilder feed(int lines) {
        out.write(ESC);
        out.write('d');
        out.write(Math.max(0, Math.min(255, lines)));
        return this;
    }

    /** Corte de papel (total). GS V 0 */
    public EscPosBuilder cut() {
        out.write(GS);
        out.write('V');
        out.write(0);
        return this;
    }

    /**
     * Pulso para abrir el cajón de dinero conectado a la impresora. ESC p m t1 t2
     * Usa el pin 0 y tiempos estándar.
     */
    public EscPosBuilder openDrawer() {
        out.write(ESC);
        out.write('p');
        out.write(0);   // pin 0
        out.write(25);  // t1
        out.write(250); // t2
        return this;
    }

    /** @return el flujo de bytes ESC/POS listo para enviar a la impresora. */
    public byte[] build() {
        return out.toByteArray();
    }
}
