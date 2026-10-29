package com.mybusinesssilva.support.adapters.in.rest;

import com.mybusinesssilva.support.adapters.in.rest.dto.ResendInboundEvent;
import com.mybusinesssilva.support.adapters.in.webhook.ResendWebhookVerifier;
import com.mybusinesssilva.support.application.IngestInboundMessageUseCase;
import com.mybusinesssilva.support.application.InboundResult;
import com.mybusinesssilva.support.config.NotificationsProperties;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * Webhook INBOUND de Resend (Fase 2). Ruta PÚBLICA a nivel de Spring Security
 * ({@code /api/v1/webhooks/resend/**} en {@code SecurityConfig}) pero AUTENTICADA por la firma Svix:
 * sin {@code @PreAuthorize}; la identidad real la impone la firma + la allowlist, nunca el JWT ni el
 * payload.
 *
 * <p>Secuencia (barreras del §3.2/§9): (0) tope de tamaño del cuerpo → {@code 413}; (1) firma Svix →
 * {@code 401} si falla, {@code 400} solo si el body es ilegible TRAS firma válida; luego
 * {@code ingest} (barreras 2–4 + dedup); mapea el desenlace a {@code 2xx}; un fallo transitorio se
 * propaga como {@code 5xx} (Resend reintenta). No se loggea el secret ni el cuerpo completo.
 */
@RestController
public class ResendInboundWebhookController {

    private static final Logger log = LoggerFactory.getLogger(ResendInboundWebhookController.class);

    private final ResendWebhookVerifier verifier;
    private final IngestInboundMessageUseCase ingestUseCase;
    private final NotificationsProperties props;
    private final ObjectMapper objectMapper;

    public ResendInboundWebhookController(ResendWebhookVerifier verifier,
                                          IngestInboundMessageUseCase ingestUseCase,
                                          NotificationsProperties props,
                                          ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.ingestUseCase = ingestUseCase;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/v1/webhooks/resend/inbound")
    public ResponseEntity<Void> inbound(
            @RequestBody(required = false) byte[] rawBody,
            @RequestHeader(value = "svix-id", required = false) String svixId,
            @RequestHeader(value = "svix-timestamp", required = false) String svixTs,
            @RequestHeader(value = "svix-signature", required = false) String svixSig,
            @RequestHeader(value = "webhook-id", required = false) String webhookId,
            @RequestHeader(value = "webhook-timestamp", required = false) String webhookTs,
            @RequestHeader(value = "webhook-signature", required = false) String webhookSig) {

        byte[] body = rawBody == null ? new byte[0] : rawBody;

        // (0) Barrera de tamaño ANTES de HMAC/parseo/BD.
        int maxBytes = props.resend() == null ? 1_048_576 : props.resend().inboundMaxBytes();
        if (body.length > maxBytes) {
            log.warn("[INBOUND] cuerpo excede el tamaño máximo ({} bytes); rechazado.", body.length);
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        }

        // (1) Firma Svix. Se eligen los headers según el prefijo configurado (svix | webhook).
        boolean useWebhookPrefix = usesWebhookPrefix();
        String id = useWebhookPrefix ? webhookId : svixId;
        String ts = useWebhookPrefix ? webhookTs : svixTs;
        String sig = useWebhookPrefix ? webhookSig : svixSig;
        if (verifier.verify(body, id, ts, sig) != ResendWebhookVerifier.Result.VALID) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Body ilegible TRAS firma válida ⇒ 400 (no 401).
        ResendInboundEvent event;
        try {
            event = objectMapper.readValue(body, ResendInboundEvent.class);
        } catch (RuntimeException ex) {
            log.warn("[INBOUND] payload inbound ilegible tras firma válida; 400.");
            return ResponseEntity.badRequest().build();
        }

        // eventId de dedup: svix-id si está; fallback sha256:<hex> del cuerpo crudo.
        String eventId = (id == null || id.isBlank()) ? "sha256:" + sha256Hex(body) : id;

        // (2–4 + dedup) Ingesta. Un fallo transitorio propaga la excepción ⇒ 5xx (Resend reintenta).
        InboundResult result = ingestUseCase.ingest(eventId, event);
        log.info("[INBOUND] svix-id={} outcome={}", safe(id), result.outcome());
        // Todos los desenlaces de negocio (INSERTED/DISCARDED_*/DUPLICATE) ⇒ 2xx: Resend no reintenta.
        return ResponseEntity.ok().build();
    }

    private boolean usesWebhookPrefix() {
        String prefix = props.resend() == null ? null : props.resend().inboundSignatureHeaderPrefix();
        return prefix != null && prefix.trim().equalsIgnoreCase("webhook");
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 siempre está disponible en la JVM; si no, usamos un fallback estable.
            return Integer.toHexString(java.util.Arrays.hashCode(data));
        }
    }

    private static String safe(String svixId) {
        return svixId == null ? "(ausente)" : svixId;
    }
}
