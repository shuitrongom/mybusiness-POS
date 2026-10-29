package com.mybusinesssilva.support.adapters.in.webhook;

import com.mybusinesssilva.support.config.NotificationsProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Verificador de firma de los webhooks de Resend (esquema <b>Svix</b>) mediante HMAC-SHA256 nativo,
 * SIN SDK (Fase 2 §3.2). <b>Fail-closed por construcción:</b> cualquier condición que no sea "firma
 * válida verificada" produce rechazo; nunca hay paso silencioso.
 *
 * <p>Pasos: (1) secret presente; (2) los tres headers {@code <prefix>-id/-timestamp/-signature}
 * obligatorios; (3) anti-replay por timestamp (tolerancia 5 min); (4) {@code signedContent =
 * id + "." + ts + "." + rawBodyUtf8}; (5) quitar prefijo {@code whsec_} y Base64-decode del secret;
 * (6) {@code HMAC-SHA256} → Base64; (7) comparar contra cada firma {@code v1,<b64>} del header en
 * tiempo constante ({@link MessageDigest#isEqual}).
 */
@Component
public class ResendWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(ResendWebhookVerifier.class);

    private static final Duration TOLERANCE = Duration.ofMinutes(5);
    private static final String SECRET_PREFIX = "whsec_";

    private final NotificationsProperties props;

    public ResendWebhookVerifier(NotificationsProperties props) {
        this.props = props;
    }

    /** Resultado de la verificación: distingue firma válida de inválida (401). */
    public enum Result {
        VALID,
        INVALID
    }

    /**
     * Verifica la firma del webhook sobre el cuerpo CRUDO.
     *
     * @param rawBody cuerpo crudo recibido (bytes), ya acotado por la barrera 0
     * @param id      header {@code <prefix>-id} (nullable)
     * @param ts      header {@code <prefix>-timestamp} (nullable)
     * @param sig     header {@code <prefix>-signature} (nullable)
     * @return {@link Result#VALID} solo si la firma coincide; {@link Result#INVALID} en cualquier
     *         otro caso (fail-closed).
     */
    public Result verify(byte[] rawBody, String id, String ts, String sig) {
        String secret = props.resend() == null ? null : props.resend().inboundWebhookSecret();
        if (isBlank(secret)) {
            log.error("[INBOUND] inbound-webhook-secret no configurado; se rechaza todo (fail-closed).");
            return Result.INVALID;
        }
        if (isBlank(id) || isBlank(ts) || isBlank(sig)) {
            log.warn("[INBOUND] headers de firma ausentes; rechazado (fail-closed).");
            return Result.INVALID;
        }
        if (!timestampWithinWindow(ts)) {
            log.warn("[INBOUND] timestamp fuera de ventana anti-replay; rechazado.");
            return Result.INVALID;
        }

        String rawBodyUtf8 = new String(rawBody == null ? new byte[0] : rawBody, StandardCharsets.UTF_8);
        String signedContent = id + "." + ts + "." + rawBodyUtf8;

        String expected;
        try {
            byte[] key = Base64.getDecoder().decode(stripSecretPrefix(secret));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal(signedContent.getBytes(StandardCharsets.UTF_8));
            expected = Base64.getEncoder().encodeToString(digest);
        } catch (RuntimeException | java.security.GeneralSecurityException ex) {
            // Secret mal formado (Base64 inválido) o algoritmo ausente: fail-closed, sin loggear secret.
            log.error("[INBOUND] no se pudo calcular el HMAC (secret mal formado?); rechazado. causa: {}",
                    ex.getClass().getSimpleName());
            return Result.INVALID;
        }

        // El header puede traer varias firmas separadas por espacio, cada una 'v1,<base64>'.
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        for (String part : sig.trim().split("\\s+")) {
            int comma = part.indexOf(',');
            String candidate = comma >= 0 ? part.substring(comma + 1) : part;
            if (MessageDigest.isEqual(expectedBytes, candidate.getBytes(StandardCharsets.UTF_8))) {
                return Result.VALID;
            }
        }
        log.warn("[INBOUND] firma inbound inválida; rechazado.");
        return Result.INVALID;
    }

    private boolean timestampWithinWindow(String ts) {
        try {
            long eventSeconds = Long.parseLong(ts.trim());
            long nowSeconds = System.currentTimeMillis() / 1000L;
            return Math.abs(nowSeconds - eventSeconds) <= TOLERANCE.getSeconds();
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String stripSecretPrefix(String secret) {
        String s = secret.trim();
        return s.startsWith(SECRET_PREFIX) ? s.substring(SECRET_PREFIX.length()) : s;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
