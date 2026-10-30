package com.mybusinesssilva.support.adapters.out.notification;

import com.mybusinesssilva.support.config.NotificationsProperties;
import com.mybusinesssilva.support.domain.port.out.ResendInboundContentPort;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

/**
 * Adaptador de salida que trae el CUERPO de un correo inbound desde la <b>Receiving API</b> de Resend
 * ({@code GET https://api.resend.com/emails/receiving/{email_id}}) vía {@link RestClient}, sin SDK
 * (mismo patrón que {@link ResendNotificationAdapter}, reutilizando la MISMA {@code api-key}).
 *
 * <p><b>Causa raíz que resuelve:</b> el webhook {@code email.received} de Resend NO incluye el cuerpo
 * ({@code text}/{@code html}); solo metadatos + {@code email_id}. El cuerpo se obtiene aquí. La key
 * {@code puntonube-soporte} (Full access) sirve para este endpoint; no hace falta una key nueva.
 *
 * <p><b>Robustez (nunca rompe la ingesta):</b> todo fallo (config incompleta, HTTP de error, timeout,
 * respuesta ilegible) se captura y se traduce a {@link Optional#empty()}; el servicio de ingesta lo
 * mapea a {@code DISCARDED_CONTENT_FETCH_FAILED}, distinguible de un cuerpo realmente vacío. Timeouts
 * propios (no toca el {@code supportRestClient} compartido). La API key NUNCA se loggea.
 */
@Component
public class ResendInboundContentAdapter implements ResendInboundContentPort {

    private static final Logger log = LoggerFactory.getLogger(ResendInboundContentAdapter.class);

    private static final String RECEIVING_BASE_URL = "https://api.resend.com/emails/receiving/";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestClient http;
    private final NotificationsProperties props;

    public ResendInboundContentAdapter(NotificationsProperties props) {
        this.props = props;
        // RestClient dedicado con timeouts razonables: una Receiving API lenta/colgada no debe bloquear
        // el hilo del webhook indefinidamente. No se reutiliza el bean compartido para no alterar el
        // comportamiento (sin timeouts) de los otros adaptadores salientes.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public Optional<InboundContent> fetchContent(String emailId) {
        if (emailId == null || emailId.isBlank()) {
            return Optional.empty();
        }
        String apiKey = props.resend() == null ? null : props.resend().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            // Degradación elegante: sin RESEND_API_KEY no se puede pedir el cuerpo. El servicio lo
            // tratará como fallo de fetch (distinguible de 'cuerpo vacío'). No se loggea el secreto.
            log.warn("[INBOUND] sin api-key de Resend; no se puede traer el cuerpo del correo.");
            return Optional.empty();
        }
        String url = RECEIVING_BASE_URL + UriUtils.encodePathSegment(emailId, java.nio.charset.StandardCharsets.UTF_8);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = http.get()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                log.warn("[INBOUND] Receiving API devolvió cuerpo nulo para el correo solicitado.");
                return Optional.empty();
            }
            String text = asString(response.get("text"));
            String html = asString(response.get("html"));
            return Optional.of(new InboundContent(text, html));
        } catch (RuntimeException ex) {
            // HTTP de error, timeout o respuesta ilegible: no se propaga (no tumba la transacción de
            // ingesta). Solo se loggea la clase de excepción, nunca el secreto ni el cuerpo.
            log.warn("[INBOUND] fallo al traer el cuerpo de la Receiving API de Resend (se degrada a "
                    + "fetch fallido). causa: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
