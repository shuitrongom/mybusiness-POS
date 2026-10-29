package com.mybusinesssilva.support.application;

import com.mybusinesssilva.support.adapters.in.rest.dto.ResendInboundEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser PURO (sin BD, sin red) de los correos inbound (Fase 2 §2 y §5): correlación del
 * {@code publicId}, normalización del remitente para la allowlist y limpieza determinista de cita y
 * firma. Toda la lógica es estática y unit-testeable con vectores de ejemplo.
 */
public final class InboundEmailParser {

    private static final Logger log = LoggerFactory.getLogger(InboundEmailParser.class);

    // Correlación (§2.1.1): reply+<uuid>@ en el destinatario; [PN-<uuid>] en el asunto.
    private static final Pattern REPLY_PLUS = Pattern.compile("reply\\+([0-9a-fA-F-]{36})@");
    private static final Pattern SUBJECT_TOKEN = Pattern.compile("\\[PN-([0-9a-fA-F-]{36})\\]");

    // Encabezados "duros" de cita (§5.2), case-insensitive, español e inglés.
    private static final Pattern QUOTE_HEADER_WROTE =
            Pattern.compile("^\\s*El .*escribió:\\s*$|^\\s*On .*wrote:\\s*$",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern QUOTE_HEADER_ORIGINAL =
            Pattern.compile("^\\s*-+\\s*Original Message\\s*-+\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_FROM =
            Pattern.compile("^\\s*(De|From):\\s.*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADER_SECONDARY =
            Pattern.compile("^\\s*(Enviado|Sent|Para|To|Asunto|Subject):\\s.*$",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern SIGNATURE_SEP = Pattern.compile("^-- \\s*$");
    private static final Pattern OUTLOOK_SEP = Pattern.compile("^_{10,}\\s*$");

    private InboundEmailParser() {
    }

    // =====================================================================================
    // Correlación (barrera 3)
    // =====================================================================================

    /**
     * Resuelve el {@code publicId} del ticket en orden determinista (§2.1.1): lista {@code to} →
     * {@code recipient} → token del asunto. El primer UUID válido gana.
     */
    public static Optional<UUID> resolvePublicId(ResendInboundEvent event) {
        if (event == null) {
            return Optional.empty();
        }
        Optional<UUID> fromTo = scanReplyPlusList(event.to());
        if (fromTo.isPresent()) {
            return fromTo;
        }
        Optional<UUID> fromRecipient = scanReplyPlus(event.recipient());
        if (fromRecipient.isPresent()) {
            return fromRecipient;
        }
        return extractFromSubject(event.subject());
    }

    private static Optional<UUID> scanReplyPlusList(List<String> candidates) {
        if (candidates == null) {
            return Optional.empty();
        }
        Optional<UUID> first = Optional.empty();
        int matches = 0;
        for (String c : candidates) {
            Optional<UUID> m = scanReplyPlus(c);
            if (m.isPresent()) {
                matches++;
                if (first.isEmpty()) {
                    first = m;
                }
            }
        }
        if (matches > 1) {
            log.warn("[INBOUND] múltiples tokens reply+ en destinatarios; gana el primero.");
        }
        return first;
    }

    private static Optional<UUID> scanReplyPlus(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return Optional.empty();
        }
        Matcher m = REPLY_PLUS.matcher(candidate);
        while (m.find()) {
            Optional<UUID> uuid = toUuid(m.group(1));
            if (uuid.isPresent()) {
                return uuid;
            }
        }
        return Optional.empty();
    }

    private static Optional<UUID> extractFromSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return Optional.empty();
        }
        Matcher m = SUBJECT_TOKEN.matcher(subject);
        while (m.find()) {
            Optional<UUID> uuid = toUuid(m.group(1));
            if (uuid.isPresent()) {
                return uuid;
            }
        }
        return Optional.empty();
    }

    private static Optional<UUID> toUuid(String raw) {
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    // =====================================================================================
    // Allowlist (barrera 2)
    // =====================================================================================

    /** Normaliza una dirección a {@code local@dominio} lowercase (quita {@code "Nombre" <...>}). */
    public static String normalizeAddress(String from) {
        if (from == null) {
            return "";
        }
        String s = from.trim();
        int lt = s.indexOf('<');
        int gt = s.indexOf('>');
        if (lt >= 0 && gt > lt) {
            s = s.substring(lt + 1, gt).trim();
        }
        return s.toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * ¿El remitente está en la allowlist? La allowlist vacía/null ⇒ {@code false} (fail-closed). La
     * comparación es por dirección exacta, normalizada en ambos lados.
     */
    public static boolean isSenderAllowed(String from, String allowlistCsv) {
        if (allowlistCsv == null || allowlistCsv.isBlank()) {
            return false;
        }
        String sender = normalizeAddress(from);
        if (sender.isEmpty()) {
            return false;
        }
        for (String entry : allowlistCsv.split(",")) {
            String normalized = entry.trim().toLowerCase(java.util.Locale.ROOT);
            if (!normalized.isEmpty() && normalized.equals(sender)) {
                return true;
            }
        }
        return false;
    }

    // =====================================================================================
    // Parseo de cuerpo (barrera 4): quitar cita y firma, sin perder el texto del humano
    // =====================================================================================

    /**
     * Elige la fuente ({@code text} preferido sobre {@code html}), la trunca al tope de trabajo, quita
     * la cita/firma SOLO si están al final, y aplica la red de seguridad "no cortar" si el corte
     * dejaría vacío un original no vacío. Devuelve el texto limpio (sin saneo final; de eso se ocupa
     * {@code postMessage}).
     */
    public static String extractNewText(ResendInboundEvent event, int maxTextChars) {
        if (event == null) {
            return "";
        }
        String source = event.text();
        if (source == null || source.isBlank()) {
            source = htmlToText(event.html());
        }
        if (source == null) {
            return "";
        }
        String truncated = truncate(source, maxTextChars);
        String cleaned = stripQuotedAndSignature(truncated);
        // Red de seguridad: si el corte dejó vacío pero el original tenía contenido, no cortar.
        if (cleaned.isBlank() && !truncated.isBlank()) {
            return truncated.strip();
        }
        return cleaned;
    }

    /**
     * Corte de cita/firma SOLO cuando están al final (§5.2). {@code >}/{@code ____} en medio con
     * contenido después NO cortan. Devuelve el texto por encima del bloque final de cita.
     */
    static String stripQuotedAndSignature(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] lines = text.split("\n", -1);
        int n = lines.length;

        // Regla 1: primer encabezado-de-cita "duro"; si no hay contenido después, cortar ahí.
        for (int i = 0; i < n; i++) {
            if (isHardQuoteHeader(lines, i) && !hasContentAtOrAfter(lines, i + 1)) {
                return joinUpTo(lines, i).strip();
            }
        }

        // Regla 3 (firma RFC): separador '-- ' en su propia línea. Por el invariante D-PARSE-2
        // ("jamás de menos"), la firma SOLO corta cuando pertenece al bloque final: es decir, cuando
        // NO hay ninguna línea de contenido humano después del '-- '. Un '-- ' intercalado con texto
        // real debajo NO corta (se prefiere conservar algo de firma a perder texto del humano).
        for (int i = 0; i < n; i++) {
            if (isSignatureSep(lines[i]) && !hasContentAtOrAfter(lines, i + 1)) {
                return joinUpTo(lines, i).strip();
            }
        }

        // Regla 2: bloque final compuesto solo de cita/separador-firma/separador-outlook/vacías (sin
        // contenido humano después). Un '>' o '____' en MEDIO con respuesta debajo NO corta.
        int cut = n;
        for (int i = n - 1; i >= 0; i--) {
            String line = lines[i];
            if (isContent(line)) {
                break;
            }
            if (isQuote(line) || isSignatureSep(line) || isOutlookSep(line) || line.isBlank()) {
                cut = i;
            }
        }
        if (cut < n) {
            return joinUpTo(lines, cut).strip();
        }
        return text.strip();
    }

    private static boolean isHardQuoteHeader(String[] lines, int i) {
        String line = lines[i];
        if (QUOTE_HEADER_WROTE.matcher(line).matches()
                || QUOTE_HEADER_ORIGINAL.matcher(line).matches()) {
            return true;
        }
        // "De:"/"From:" solo cuenta como encabezado si va seguida de otra cabecera en ≤4 líneas.
        if (HEADER_FROM.matcher(line).matches()) {
            int limit = Math.min(lines.length, i + 5);
            for (int j = i + 1; j < limit; j++) {
                if (HEADER_SECONDARY.matcher(lines[j]).matches()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** ¿Hay alguna línea de CONTENIDO (texto del humano, no cita/firma) de {@code from} en adelante? */
    private static boolean hasContentAtOrAfter(String[] lines, int from) {
        for (int i = from; i < lines.length; i++) {
            if (isContent(lines[i])) {
                return true;
            }
        }
        return false;
    }

    private static boolean isContent(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        return !isQuote(line) && !isSignatureSep(line) && !isOutlookSep(line)
                && !QUOTE_HEADER_WROTE.matcher(line).matches()
                && !QUOTE_HEADER_ORIGINAL.matcher(line).matches();
    }

    private static boolean isQuote(String line) {
        return line != null && line.stripLeading().startsWith(">");
    }

    private static boolean isSignatureSep(String line) {
        return line != null && SIGNATURE_SEP.matcher(line).matches();
    }

    private static boolean isOutlookSep(String line) {
        return line != null && OUTLOOK_SEP.matcher(line).matches();
    }

    private static String joinUpTo(String[] lines, int exclusiveEnd) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < exclusiveEnd; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    /** Conversión mínima HTML→texto: quita tags y colapsa espacios (degradación si no hay {@code text}). */
    static String htmlToText(String html) {
        if (html == null) {
            return null;
        }
        String noTags = html
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</p>", "\n")
                .replaceAll("(?is)<[^>]+>", "");
        return noTags
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private static String truncate(String s, int maxChars) {
        if (maxChars <= 0 || s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars);
    }
}
