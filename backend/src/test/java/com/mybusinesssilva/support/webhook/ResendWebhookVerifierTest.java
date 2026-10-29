package com.mybusinesssilva.support.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.support.adapters.in.webhook.ResendWebhookVerifier;
import com.mybusinesssilva.support.config.NotificationsProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias (sin BD, sin red) del verificador de firma Svix del webhook inbound (Fase 2 §3.2):
 * firma válida OK; no coincide / header ausente / secret vacío / timestamp fuera de ventana ⇒
 * fail-closed; múltiples firmas {@code v1,…}; prefijo de header configurable.
 */
class ResendWebhookVerifierTest {

    // Secret de prueba en formato whsec_<base64>. "clave-de-prueba-de-32-bytes-min!" = 32 bytes.
    private static final String RAW_KEY = "clave-de-prueba-de-32-bytes-min!";
    private static final String SECRET =
            "whsec_" + Base64.getEncoder().encodeToString(RAW_KEY.getBytes(StandardCharsets.UTF_8));

    private static final String ID = "msg_2abc123";
    private static final byte[] BODY = "{\"from\":\"a@b.com\"}".getBytes(StandardCharsets.UTF_8);

    private ResendWebhookVerifier verifier(String secret, String prefix) {
        NotificationsProperties props = new NotificationsProperties(
                new NotificationsProperties.Resend("", "", secret, "", "", prefix, 1_048_576, 65_536),
                new NotificationsProperties.Support(""));
        return new ResendWebhookVerifier(props);
    }

    /** Firma Svix de referencia: base64(hmacSha256(base64decode(key), id + "." + ts + "." + body)). */
    private static String signature(String id, String ts, byte[] body) {
        try {
            byte[] key = Base64.getDecoder().decode(SECRET.substring("whsec_".length()));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            String signed = id + "." + ts + "." + new String(body, StandardCharsets.UTF_8);
            return Base64.getEncoder().encodeToString(mac.doFinal(signed.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String nowTs() {
        return Long.toString(System.currentTimeMillis() / 1000L);
    }

    @Test
    void validSignatureIsAccepted() {
        String ts = nowTs();
        String sig = "v1," + signature(ID, ts, BODY);
        assertThat(verifier(SECRET, "svix").verify(BODY, ID, ts, sig))
                .isEqualTo(ResendWebhookVerifier.Result.VALID);
    }

    @Test
    void wrongSignatureIsRejected() {
        String ts = nowTs();
        assertThat(verifier(SECRET, "svix").verify(BODY, ID, ts, "v1,bm90LWEtdmFsaWQtc2ln"))
                .isEqualTo(ResendWebhookVerifier.Result.INVALID);
    }

    @Test
    void missingHeadersAreRejectedFailClosed() {
        String ts = nowTs();
        String sig = "v1," + signature(ID, ts, BODY);
        ResendWebhookVerifier v = verifier(SECRET, "svix");
        assertThat(v.verify(BODY, null, ts, sig)).isEqualTo(ResendWebhookVerifier.Result.INVALID);
        assertThat(v.verify(BODY, ID, null, sig)).isEqualTo(ResendWebhookVerifier.Result.INVALID);
        assertThat(v.verify(BODY, ID, ts, null)).isEqualTo(ResendWebhookVerifier.Result.INVALID);
    }

    @Test
    void emptySecretRejectsEverythingFailClosed() {
        String ts = nowTs();
        String sig = "v1," + signature(ID, ts, BODY);
        assertThat(verifier("", "svix").verify(BODY, ID, ts, sig))
                .isEqualTo(ResendWebhookVerifier.Result.INVALID);
        assertThat(verifier(null, "svix").verify(BODY, ID, ts, sig))
                .isEqualTo(ResendWebhookVerifier.Result.INVALID);
    }

    @Test
    void timestampOutsideWindowIsRejected() {
        String oldTs = Long.toString(System.currentTimeMillis() / 1000L - 3600); // 1h atrás
        String sig = "v1," + signature(ID, oldTs, BODY);
        assertThat(verifier(SECRET, "svix").verify(BODY, ID, oldTs, sig))
                .isEqualTo(ResendWebhookVerifier.Result.INVALID);
    }

    @Test
    void acceptsWhenOneOfSeveralSignaturesMatches() {
        String ts = nowTs();
        String good = signature(ID, ts, BODY);
        String header = "v1,bm90LWEtdmFsaWQtc2ln v2," + good;  // una inválida + la buena
        assertThat(verifier(SECRET, "svix").verify(BODY, ID, ts, header))
                .isEqualTo(ResendWebhookVerifier.Result.VALID);
    }

    @Test
    void worksRegardlessOfConfiguredPrefix() {
        // El verificador no mira el nombre del header (eso lo hace el controller); con prefijo
        // 'webhook' el HMAC es idéntico, así que la firma válida sigue aceptándose.
        String ts = nowTs();
        String sig = "v1," + signature(ID, ts, BODY);
        assertThat(verifier(SECRET, "webhook").verify(BODY, ID, ts, sig))
                .isEqualTo(ResendWebhookVerifier.Result.VALID);
    }
}
