package com.mybusinesssilva.support.application;

import com.mybusinesssilva.notifications.application.NotificationService;
import com.mybusinesssilva.notifications.domain.model.Notification;
import com.mybusinesssilva.notifications.domain.model.NotificationType;
import com.mybusinesssilva.notifications.domain.model.RecipientScope;
import com.mybusinesssilva.platform.audit.AuditService;
import com.mybusinesssilva.platform.notifications.NotificationPort;
import com.mybusinesssilva.support.config.NotificationsProperties;
import com.mybusinesssilva.support.domain.exception.SupportValidationException;
import com.mybusinesssilva.support.domain.exception.TicketNotFoundException;
import com.mybusinesssilva.support.domain.model.AuthorType;
import com.mybusinesssilva.support.domain.model.SupportTicket;
import com.mybusinesssilva.support.domain.model.SupportTicketMessage;
import com.mybusinesssilva.support.domain.model.TicketVisibility;
import com.mybusinesssilva.support.domain.port.in.ListThreadUseCase;
import com.mybusinesssilva.support.domain.port.in.PostMessageUseCase;
import com.mybusinesssilva.support.domain.port.in.RequesterScope;
import com.mybusinesssilva.support.domain.port.out.SupportMessageRepository;
import com.mybusinesssilva.support.domain.port.out.SupportTicketRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

/**
 * Orquesta el hilo bidireccional de mensajes de soporte (Decisión D6: no engorda
 * {@code SupportTicketService}). Implementa listar y publicar mensajes, con sanitización canónica
 * del cuerpo, auditoría, y notificaciones in-app + correo al reportante best-effort tras el commit.
 */
@Service
public class SupportThreadService implements ListThreadUseCase, PostMessageUseCase {

    private static final Logger log = LoggerFactory.getLogger(SupportThreadService.class);

    private static final int BODY_MAX_CP = 4000;
    private static final int APP_VERSION_MAX = 60;
    private static final int SCREEN_PATH_MAX = 300;
    private static final int EMAIL_BODY_PREVIEW = 140;
    private static final Duration EMAIL_COOLDOWN = Duration.ofMinutes(30);
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cc}&&[^\n\t]]");
    private static final Pattern SCREEN_PATH_RE = Pattern.compile("^/[\\w\\-/.%]{0,299}$");

    private final SupportTicketRepository ticketRepo;
    private final SupportMessageRepository messageRepo;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final NotificationsProperties notificationsProperties;
    private final NotificationPort notificationPort;
    private final TransactionTemplate transactionTemplate;

    public SupportThreadService(SupportTicketRepository ticketRepo,
                                SupportMessageRepository messageRepo,
                                NotificationService notificationService,
                                AuditService auditService,
                                NotificationsProperties notificationsProperties,
                                NotificationPort notificationPort,
                                PlatformTransactionManager transactionManager) {
        this.ticketRepo = ticketRepo;
        this.messageRepo = messageRepo;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.notificationsProperties = notificationsProperties;
        this.notificationPort = notificationPort;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<SupportTicketMessage> listThread(UUID ticketPublicId, RequesterScope scope) {
        SupportTicket ticket = resolveVisible(ticketPublicId, scope);
        return messageRepo.findByTicketId(ticket.id());
    }

    @Override
    public SupportTicketMessage postMessage(UUID ticketPublicId, String body,
                                            Author author, RequesterScope scope) {
        SupportTicket ticket = resolveVisible(ticketPublicId, scope);

        // (2) Sanear body en ORDEN CANÓNICO FIJO (§5.1 / M2).
        String sanitized = sanitizeBody(body);

        // (3) Sanear screenPath y truncar appVersion del Author.
        String screenPath = sanitizeScreenPath(author.screenPath());
        String appVersion = truncateNullable(author.appVersion(), APP_VERSION_MAX);

        // (4) Construir el mensaje con los campos ya resueltos por el controller (H-A3).
        SupportTicketMessage draft = new SupportTicketMessage(
                null, null, ticket.id(),
                author.type(), author.role(), author.userId(), author.email(),
                sanitized, appVersion, screenPath, null);

        // (5) Persistir dentro de la transacción (commit antes de notificar).
        SupportTicketMessage saved = transactionTemplate.execute(status -> messageRepo.insert(draft));

        // (6) Auditar el evento (dato de plataforma).
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("authorType", author.type().name());
        details.put("messagePublicId", saved.publicId() == null ? null : saved.publicId().toString());
        details.put("at", Instant.now().toString());
        auditService.recordGlobal(author.userId(), "SUPPORT_MESSAGE_POSTED",
                "support_ticket", ticket.publicId().toString(), details);

        // (7a) Notificación in-app best-effort tras el commit (try/catch aislado).
        try {
            notifyInApp(ticket, author.type(), sanitized);
        } catch (RuntimeException ex) {
            log.warn("[SUPPORT] Notif in-app del mensaje de {} falló (se ignora). Causa: {}",
                    ticket.publicId(), ex.getClass().getSimpleName());
        }

        // (7b) Correo al reportante best-effort (solo si responde el super-admin, con cooldown).
        if (author.type() == AuthorType.SUPER_ADMIN) {
            try {
                notifyReporterByEmail(ticket, saved);
            } catch (RuntimeException ex) {
                log.warn("[SUPPORT] Correo de respuesta al reportante de {} falló (se ignora). Causa: {}",
                        ticket.publicId(), ex.getClass().getSimpleName());
            }
        }

        return saved;
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private SupportTicket resolveVisible(UUID ticketPublicId, RequesterScope scope) {
        SupportTicket ticket = ticketRepo.findByPublicId(ticketPublicId)
                .orElseThrow(() -> new TicketNotFoundException("Ticket de soporte no encontrado."));
        if (!TicketVisibility.isVisible(ticket, scope)) {
            throw new TicketNotFoundException("Ticket de soporte no encontrado.");
        }
        return ticket;
    }

    /** Sanea el cuerpo en el orden canónico FIJO (§5.1 / M2). */
    private static String sanitizeBody(String raw) {
        String s = raw == null ? "" : raw.trim();
        // b. Normalizar CRLF/CR sueltos a LF.
        s = s.replace("\r\n", "\n").replace("\r", "\n");
        // c. Strip de caracteres de control salvo \n y \t.
        s = CONTROL_CHARS.matcher(s).replaceAll("");
        // d. Vacío → 422.
        if (s.trim().isEmpty()) {
            throw new SupportValidationException("El mensaje no puede estar vacío.");
        }
        // e. Truncado de seguridad por code points.
        return limitCodePoints(s, BODY_MAX_CP);
    }

    private void notifyInApp(SupportTicket ticket, AuthorType authorType, String sanitizedBody) {
        if (authorType == AuthorType.SUPER_ADMIN) {
            Notification draft = new Notification(
                    null, null, ticket.businessId(), ticket.tenantSchema(),
                    ticket.reporterUserId(), RecipientScope.USER, NotificationType.SUPPORT_REPLY,
                    "Nueva respuesta a tu reporte: " + ticket.title(),
                    preview(sanitizedBody),
                    "support_ticket", ticket.publicId().toString(),
                    Map.of("ticketType", ticket.type() == null ? "" : ticket.type().name()),
                    null, null);
            notificationService.create(draft);
        } else {
            Notification draft = new Notification(
                    null, null, null, null,
                    null, RecipientScope.SUPER_ADMIN, NotificationType.SUPPORT_REPLY_FROM_REPORTER,
                    "Nueva respuesta del negocio: " + ticket.title(),
                    preview(sanitizedBody),
                    "support_ticket", ticket.publicId().toString(),
                    Map.of("ticketType", ticket.type() == null ? "" : ticket.type().name()),
                    null, null);
            notificationService.create(draft);
        }
    }

    private void notifyReporterByEmail(SupportTicket ticket, SupportTicketMessage saved) {
        String to = ticket.reporterEmail();
        if (to == null || to.isBlank()) {
            log.warn("[SUPPORT] Reportante sin correo en ticket {}; correo de respuesta omitido.",
                    ticket.publicId());
            return;
        }
        // Anti-spam (H-A7): si ya hubo respuesta del super-admin en los últimos 30 min, omitir.
        var previous = messageRepo.lastSuperAdminMessageAt(ticket.id(), saved.createdAt());
        if (previous.isPresent()) {
            Duration gap = Duration.between(previous.get(), saved.createdAt());
            if (gap.compareTo(EMAIL_COOLDOWN) < 0) {
                log.debug("[SUPPORT] Cooldown de correo activo para ticket {}; correo omitido.",
                        ticket.publicId());
                return;
            }
        }
        String subject = "[PuntoNube] Respuesta a tu reporte: " + esc(ticket.title());
        notificationPort.sendEmail(to, subject, buildReplyEmailHtml(ticket), null, null);
    }

    /** Cuerpo HTML mínimo escapado con CTA (no vuelca el hilo). */
    private static String buildReplyEmailHtml(SupportTicket t) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>Tienes una nueva respuesta del equipo PuntoNube</h2>");
        sb.append("<p>Reporte: <b>").append(esc(t.title())).append("</b></p>");
        sb.append("<p>Entra a PuntoNube y abre la sección de Soporte para leer la respuesta "
                + "y continuar la conversación.</p>");
        return sb.toString();
    }

    private static String preview(String body) {
        if (body == null) {
            return null;
        }
        return limitCodePoints(body, EMAIL_BODY_PREVIEW);
    }

    private static String sanitizeScreenPath(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String truncated = trimmed.length() <= SCREEN_PATH_MAX
                ? trimmed : trimmed.substring(0, SCREEN_PATH_MAX);
        if (!SCREEN_PATH_RE.matcher(truncated).matches()) {
            log.debug("[SUPPORT] screenPath del mensaje fuera del patrón; se guarda truncado tal cual.");
        }
        return truncated;
    }

    private static String truncateNullable(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    /** Trunca {@code s} a {@code maxCp} code points (NUNCA String.length()/substring crudo). */
    private static String limitCodePoints(String s, int maxCp) {
        int count = s.codePointCount(0, s.length());
        if (count <= maxCp) {
            return s;
        }
        int end = s.offsetByCodePoints(0, maxCp);
        log.debug("[SUPPORT] body/texto truncado a {} code points.", maxCp);
        return s.substring(0, end);
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }
}
