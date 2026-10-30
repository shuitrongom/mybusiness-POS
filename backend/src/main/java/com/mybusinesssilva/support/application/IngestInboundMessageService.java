package com.mybusinesssilva.support.application;

import com.mybusinesssilva.support.adapters.in.rest.dto.ResendInboundEvent;
import com.mybusinesssilva.support.config.NotificationsProperties;
import com.mybusinesssilva.support.domain.model.AuthorType;
import com.mybusinesssilva.support.domain.model.SupportTicket;
import com.mybusinesssilva.support.domain.model.SupportTicketMessage;
import com.mybusinesssilva.support.domain.port.in.PostMessageUseCase;
import com.mybusinesssilva.support.domain.port.in.RequesterScope;
import com.mybusinesssilva.support.domain.port.out.ResendInboundContentPort;
import com.mybusinesssilva.support.domain.port.out.SupportInboundEventRepository;
import com.mybusinesssilva.support.domain.port.out.SupportMessageRepository;
import com.mybusinesssilva.support.domain.port.out.SupportTicketRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Ingesta inbound (Fase 2 §6.1/§7.3): orquesta dedup + barreras 2–4 y reutiliza {@code postMessage}
 * para insertar la respuesta del equipo como {@code SUPER_ADMIN}, disparando la MISMA notificación
 * in-app + correo al reportante que el panel. La barrera 1 (firma) ya se resolvió en el controller.
 *
 * <p>El claim y el procesamiento ocurren en UNA transacción: un fallo transitorio hace rollback (la
 * fila {@code PROCESSING} desaparece) y deja que Resend reintente limpiamente con {@code 5xx}. La
 * identidad {@code SUPER_ADMIN} se fija en el servidor, NUNCA del payload.
 */
@Service
public class IngestInboundMessageService implements IngestInboundMessageUseCase {

    private static final Logger log = LoggerFactory.getLogger(IngestInboundMessageService.class);

    private static final String SOURCE_INBOUND_EMAIL = "INBOUND_EMAIL";

    private final SupportInboundEventRepository inboundEventRepo;
    private final SupportTicketRepository ticketRepo;
    private final SupportMessageRepository messageRepo;
    private final PostMessageUseCase postMessageUseCase;
    private final ResendInboundContentPort inboundContentPort;
    private final NotificationsProperties props;
    private final TransactionTemplate transactionTemplate;

    public IngestInboundMessageService(SupportInboundEventRepository inboundEventRepo,
                                       SupportTicketRepository ticketRepo,
                                       SupportMessageRepository messageRepo,
                                       PostMessageUseCase postMessageUseCase,
                                       ResendInboundContentPort inboundContentPort,
                                       NotificationsProperties props,
                                       PlatformTransactionManager transactionManager) {
        this.inboundEventRepo = inboundEventRepo;
        this.ticketRepo = ticketRepo;
        this.messageRepo = messageRepo;
        this.postMessageUseCase = postMessageUseCase;
        this.inboundContentPort = inboundContentPort;
        this.props = props;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public InboundResult ingest(String eventId, ResendInboundEvent event) {
        return transactionTemplate.execute(status -> process(eventId, event));
    }

    private InboundResult process(String eventId, ResendInboundEvent event) {
        // (1) Dedup: reclamar el evento. Si no gana el claim, ya fue/está siendo procesado.
        if (!inboundEventRepo.tryClaim(eventId)) {
            Optional<String> current = inboundEventRepo.outcomeOf(eventId);
            log.info("[INBOUND] evento ya reclamado ({}); no se reprocesa.",
                    current.orElse("desconocido"));
            return InboundResult.discarded(InboundResult.DUPLICATE);
        }

        String senderEmail = InboundEmailParser.normalizeAddress(event == null ? null : event.from());

        // (2) Barrera 2 — allowlist del remitente (vacía ⇒ fail-closed).
        String allowlist = props.resend() == null ? null : props.resend().inboundAllowlist();
        if (!InboundEmailParser.isSenderAllowed(event == null ? null : event.from(), allowlist)) {
            log.warn("[INBOUND] remitente no autorizado; descartado.");
            inboundEventRepo.finalizeEvent(eventId, InboundResult.DISCARDED_ALLOWLIST,
                    "remitente no autorizado", null, null);
            return InboundResult.discarded(InboundResult.DISCARDED_ALLOWLIST);
        }

        // (3) Barrera 3 — correlación a un ticket existente.
        Optional<UUID> publicId = InboundEmailParser.resolvePublicId(event);
        if (publicId.isEmpty()) {
            log.warn("[INBOUND] inbound sin token de correlación; descartado.");
            inboundEventRepo.finalizeEvent(eventId, InboundResult.DISCARDED_NO_TOKEN,
                    "sin token de correlación", null, null);
            return InboundResult.discarded(InboundResult.DISCARDED_NO_TOKEN);
        }
        Optional<SupportTicket> ticket = ticketRepo.findByPublicId(publicId.get());
        if (ticket.isEmpty()) {
            log.warn("[INBOUND] ticket inexistente para publicId {}; descartado.", publicId.get());
            inboundEventRepo.finalizeEvent(eventId, InboundResult.DISCARDED_NO_TICKET,
                    "ticket inexistente", null, null);
            return InboundResult.discarded(InboundResult.DISCARDED_NO_TICKET);
        }

        // (3.5) Resolución del CUERPO. El webhook email.received de Resend NO trae text/html (solo
        // metadatos + email_id): hay que pedirlo a la Receiving API. ORDEN DE SEGURIDAD: este fetch
        // ocurre SOLO tras pasar firma (controller), allowlist y correlación a un ticket existente —
        // NUNCA antes. Un webhook no verificado, un remitente no autorizado o un inbound no
        // correlacionado se descartan arriba SIN gastar una llamada a la API.
        Optional<EventWithBody> withBody = resolveBody(event);
        if (withBody.isEmpty()) {
            // Había un email_id pero la API no entregó el cuerpo (fallo HTTP/timeout/respuesta vacía).
            // Se distingue de "cuerpo realmente vacío" para que en logs quede claro el origen.
            log.warn("[INBOUND] no se pudo traer el cuerpo del correo (fetch a Resend falló); "
                    + "descartado.");
            inboundEventRepo.finalizeEvent(eventId, InboundResult.DISCARDED_CONTENT_FETCH_FAILED,
                    "fallo al traer el cuerpo de la Receiving API", ticket.get().id(), null);
            return InboundResult.discarded(InboundResult.DISCARDED_CONTENT_FETCH_FAILED);
        }

        // (4) Barrera 4 — parseo/limpieza. Original vacío ⇒ DISCARDED_EMPTY.
        int maxTextChars = props.resend() == null ? 65_536 : props.resend().inboundMaxTextChars();
        String cleaned = InboundEmailParser.extractNewText(withBody.get().event(), maxTextChars);
        if (cleaned == null || cleaned.isBlank()) {
            log.warn("[INBOUND] cuerpo inbound vacío; descartado.");
            inboundEventRepo.finalizeEvent(eventId, InboundResult.DISCARDED_EMPTY,
                    "cuerpo vacío", ticket.get().id(), null);
            return InboundResult.discarded(InboundResult.DISCARDED_EMPTY);
        }

        // (5) Reutilizar postMessage: saneo/truncado 4000 CP, auditoría y notificación al reportante.
        // La identidad SUPER_ADMIN se fija aquí (servidor), nunca del payload. author_user_id (NOT
        // NULL) = email verificado del remitente de la allowlist (trazabilidad, D-INS-1).
        PostMessageUseCase.Author author = new PostMessageUseCase.Author(
                AuthorType.SUPER_ADMIN, "SUPER_ADMIN", senderEmail, senderEmail, null, null);
        SupportTicketMessage saved = postMessageUseCase.postMessage(
                ticket.get().publicId(), cleaned, author, new RequesterScope.SuperAdmin());

        // Marca de origen (D-UI-2): el mensaje llegó por correo.
        messageRepo.markSource(saved.id(), SOURCE_INBOUND_EMAIL);

        // (6) Desenlace final.
        inboundEventRepo.finalizeEvent(eventId, InboundResult.INSERTED, "ok",
                ticket.get().id(), saved.id());
        log.info("[INBOUND] mensaje insertado en ticket {} (origen correo).", ticket.get().publicId());
        return new InboundResult(InboundResult.INSERTED, ticket.get().id(), saved.id());
    }

    /**
     * Resuelve el cuerpo del correo y devuelve el evento listo para parsear.
     *
     * <ul>
     *   <li>Si el payload ya trae {@code text}/{@code html} (payloads de prueba, planos o formatos
     *       futuros): se usa tal cual, SIN llamar a la API (optimización + fallback).</li>
     *   <li>Si NO trae cuerpo pero sí {@code email_id} (caso real de Resend): se pide a la Receiving
     *       API. Si la API entrega contenido, se reconstruye el evento con ese {@code text}/{@code
     *       html}. Si la API falla/timeout/responde vacío: {@link Optional#empty()} ⇒ el servicio
     *       descarta con {@code DISCARDED_CONTENT_FETCH_FAILED} (distinguible de cuerpo vacío).</li>
     *   <li>Si no hay cuerpo ni {@code email_id}: se deja el evento tal cual (sin cuerpo) ⇒ el parser
     *       lo verá vacío ⇒ {@code DISCARDED_EMPTY}.</li>
     * </ul>
     */
    private Optional<EventWithBody> resolveBody(ResendInboundEvent event) {
        if (event == null) {
            return Optional.of(new EventWithBody(null));
        }
        boolean hasInlineBody = notBlank(event.text()) || notBlank(event.html());
        if (hasInlineBody) {
            return Optional.of(new EventWithBody(event));
        }
        String emailId = event.emailId();
        if (emailId == null || emailId.isBlank()) {
            // No hay cuerpo ni handle para pedirlo: déjalo seguir y que el parser lo marque vacío.
            return Optional.of(new EventWithBody(event));
        }
        Optional<ResendInboundContentPort.InboundContent> content =
                inboundContentPort.fetchContent(emailId);
        if (content.isEmpty()) {
            // Había email_id pero la API no entregó el cuerpo: fallo de fetch, distinguible de vacío.
            return Optional.empty();
        }
        ResendInboundContentPort.InboundContent c = content.get();
        ResendInboundEvent enriched = new ResendInboundEvent(
                event.from(), event.to(), event.receivedFor(), event.recipient(), event.subject(),
                event.emailId(), c.text(), c.html());
        return Optional.of(new EventWithBody(enriched));
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Evento inbound ya con el cuerpo resuelto (del payload o traído de la Receiving API). */
    private record EventWithBody(ResendInboundEvent event) {
    }
}
