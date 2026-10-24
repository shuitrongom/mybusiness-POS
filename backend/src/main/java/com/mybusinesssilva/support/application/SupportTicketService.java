package com.mybusinesssilva.support.application;

import com.mybusinesssilva.platform.audit.AuditService;
import com.mybusinesssilva.platform.notifications.NotificationPort;
import com.mybusinesssilva.support.config.NotificationsProperties;
import com.mybusinesssilva.support.config.SupportImageProperties;
import com.mybusinesssilva.support.domain.exception.SupportValidationException;
import com.mybusinesssilva.support.domain.exception.TicketNotFoundException;
import com.mybusinesssilva.support.domain.model.SupportTicket;
import com.mybusinesssilva.support.domain.model.SupportTicketImage;
import com.mybusinesssilva.support.domain.model.TicketContext;
import com.mybusinesssilva.support.domain.model.TicketStatus;
import com.mybusinesssilva.support.domain.model.TicketType;
import com.mybusinesssilva.support.domain.port.in.Actor;
import com.mybusinesssilva.support.domain.port.in.ChangeTicketStatusUseCase;
import com.mybusinesssilva.support.domain.port.in.CreateSupportTicketUseCase;
import com.mybusinesssilva.support.domain.port.in.GetSupportTicketUseCase;
import com.mybusinesssilva.support.domain.port.in.ListSupportTicketsUseCase;
import com.mybusinesssilva.support.domain.port.in.RequesterScope;
import com.mybusinesssilva.support.domain.port.in.TicketQuery;
import com.mybusinesssilva.support.domain.port.out.ImageStoragePort;
import com.mybusinesssilva.support.domain.port.out.SupportTicketRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;

/**
 * Orquestador del buzón de soporte: valida, resuelve el negocio (barrera dura), valida imágenes por
 * magic bytes, sube, persiste de forma transaccional, audita y notifica (best-effort, tras el commit).
 *
 * <p>Implementa los cuatro casos de uso del §3 del diseño. No expone el {@code id} secuencial interno
 * (opera por {@code publicId} UUID). La notificación por correo nunca tumba la creación del ticket.
 */
@Service
public class SupportTicketService implements CreateSupportTicketUseCase, ListSupportTicketsUseCase,
        GetSupportTicketUseCase, ChangeTicketStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(SupportTicketService.class);

    // ----- Límites de validación de texto (§5.4) -----
    private static final int TITLE_MIN = 3;
    private static final int TITLE_MAX = 160;
    private static final int DESCRIPTION_MIN = 5;
    private static final int DESCRIPTION_MAX = 4000;
    private static final int SCREEN_PATH_MAX = 300;
    private static final int APP_VERSION_MAX = 60;
    private static final int USER_AGENT_MAX = 500;
    private static final java.util.regex.Pattern SCREEN_PATH_RE =
            java.util.regex.Pattern.compile("^/[\\w\\-/.%]{0,299}$");

    // =====================================================================================
    // POLÍTICA DE IMAGEN INVÁLIDA — §11.8 (DECISIÓN DE NEGOCIO BLOQUEANTE DEL DUEÑO)
    // -------------------------------------------------------------------------------------
    // Un ÚNICO punto de decisión con AMBAS ramas codificadas. NO está finalizada: el dueño debe
    // confirmar por el canal formal (§11.8) antes de fijar una como definitiva.
    //   - Rama A (REJECT_ALL): si alguna imagen es inválida (tipo/tamaño/cantidad), se rechaza TODO
    //     el ticket con 422 IMAGE_INVALID + invalidImages[]. No se crea nada.
    //   - Rama B (ACCEPT_VALID): se crea el ticket solo con las imágenes válidas y se informan las
    //     descartadas (rejectedImages[]). No se pierde el texto del usuario.
    //
    // TODO(dueño §11.8): confirmar la rama definitiva. Hasta la confirmación formal queda Rama B
    // (recomendación NO vinculante del diseño, alineada con el steering premium "no perder el
    // trabajo"; coincide con la pista del dueño "la b es la mejor"). Cambiar la política = cambiar
    // ESTA sola constante; ambas ramas ya están implementadas abajo.
    static final InvalidImagePolicy IMAGE_POLICY = InvalidImagePolicy.ACCEPT_VALID; // Rama B (provisional)

    /** Las dos ramas posibles ante imagen inválida (§11.8). */
    enum InvalidImagePolicy {
        /** Rama A: rechazar todo el ticket (422 IMAGE_INVALID). */
        REJECT_ALL,
        /** Rama B: crear con las válidas e informar las descartadas (201 + rejectedImages). */
        ACCEPT_VALID
    }

    private final SupportTicketRepository repo;
    private final ImageStoragePort imageStorage;
    private final NotificationPort notifications;
    private final AuditService auditService;
    private final NotificationsProperties notificationsProperties;
    private final SupportImageProperties imageProperties;
    private final TransactionTemplate transactionTemplate;

    public SupportTicketService(SupportTicketRepository repo,
                                ImageStoragePort imageStorage,
                                NotificationPort notifications,
                                AuditService auditService,
                                NotificationsProperties notificationsProperties,
                                SupportImageProperties imageProperties,
                                PlatformTransactionManager transactionManager) {
        this.repo = repo;
        this.imageStorage = imageStorage;
        this.notifications = notifications;
        this.auditService = auditService;
        this.notificationsProperties = notificationsProperties;
        this.imageProperties = imageProperties;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // =====================================================================================
    // CREATE
    // =====================================================================================

    @Override
    public SupportTicket create(NewTicketCommand command) {
        if (command == null) {
            throw new SupportValidationException("Comando de alta ausente.");
        }

        // (1) Validar/sanear campos de texto.
        if (command.type() == null) {
            throw new SupportValidationException("El tipo de ticket es obligatorio (ERROR | MEJORA).");
        }
        String title = requireTrimmed(command.title(), "título", TITLE_MIN, TITLE_MAX);
        String description = requireTrimmed(command.description(), "descripción",
                DESCRIPTION_MIN, DESCRIPTION_MAX);
        String screenPath = sanitizeScreenPath(command.screenPath());
        String appVersion = truncateNullable(command.appVersion(), APP_VERSION_MAX);

        TicketContext ctx = command.context();
        if (ctx == null) {
            throw new SupportValidationException("Falta el contexto del reporte.");
        }

        // (2) BARRERA DURA: exigir tenant presente y business_id resuelto (nunca ticket huérfano).
        long businessId = requireResolvedBusiness(ctx.tenantSchema());

        // (3) Validar imágenes por magic bytes, tamaño y cantidad.
        ImageValidationResult validation = validateImages(command.images());

        // --- PUNTO DE DECISIÓN ÚNICO de la política de imagen inválida (§11.8) ---
        List<UploadedImage> toUpload;
        if (validation.hasInvalid()) {
            switch (IMAGE_POLICY) {
                case REJECT_ALL -> {
                    // Rama A: nada a medias; se rechaza todo con 422 IMAGE_INVALID + invalidImages[].
                    throw new SupportImageRejectedException(
                            "Alguna imagen no es válida (tipo/tamaño/cantidad).", validation.rejected());
                }
                case ACCEPT_VALID -> {
                    // Rama B: se continúa con el subconjunto válido; se avisará de las descartadas.
                    toUpload = validation.valid();
                    if (!validation.rejected().isEmpty()) {
                        log.info("[SUPPORT] {} imagen(es) descartada(s) al crear el ticket (Rama B).",
                                validation.rejected().size());
                    }
                }
                default -> throw new IllegalStateException("Política de imagen no soportada.");
            }
        } else {
            toUpload = validation.valid();
        }

        // (4) Subir las imágenes válidas (no-op si Cloudinary no está; fallos de red se omiten).
        List<SupportTicketImage> uploaded = imageStorage.upload(toUpload);

        // (5) Persistir (ticket + imágenes) en una transacción.
        String userAgent = truncateNullable(ctx.userAgent(), USER_AGENT_MAX);
        String device = ctx.device() == null || ctx.device().isBlank() ? "unknown" : ctx.device();
        SupportTicket persisted = persist(new SupportTicket(
                null, null, businessId, ctx.tenantSchema(),
                ctx.reporterUserId(), ctx.reporterEmail(), ctx.reporterRole(),
                command.type(), title, description, screenPath, appVersion, userAgent, device,
                TicketStatus.NUEVO, null, null, null, null, null, uploaded), uploaded);

        // (6) Auditar el alta (dato de plataforma → recordGlobal) con el detailsMap del §3 paso 5.
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("tenant", ctx.tenantSchema());
        details.put("role", ctx.reporterRole());
        details.put("device", device);
        details.put("userAgent", userAgent);
        details.put("screenPath", screenPath);
        details.put("type", command.type().name());
        details.put("imageCount", uploaded.size());
        details.put("at", Instant.now().toString());
        auditService.recordGlobal(ctx.reporterUserId(), "SUPPORT_TICKET_CREATED",
                "support_ticket", persisted.publicId().toString(), details);

        // (7) Notificar DESPUÉS del commit, en try/catch aislado; omitir si el inbox está en blanco.
        notifyNewTicket(persisted);

        return persisted;
    }

    /**
     * Persiste ticket + imágenes en una sola transacción (vía {@link TransactionTemplate}, para que
     * el commit real ocurra ANTES de la notificación best-effort del §3 paso 7, sin depender del
     * proxy de {@code @Transactional} en una auto-invocación).
     */
    private SupportTicket persist(SupportTicket ticket, List<SupportTicketImage> images) {
        return transactionTemplate.execute(status -> {
            SupportTicket inserted = repo.insert(ticket);
            if (images != null && !images.isEmpty()) {
                repo.insertImages(inserted.id(), images);
            }
            // Devuelve el ticket con sus imágenes ya resueltas (el insert no las trae).
            return withImages(inserted, images);
        });
    }

    private static SupportTicket withImages(SupportTicket inserted, List<SupportTicketImage> images) {
        return new SupportTicket(
                inserted.id(), inserted.publicId(), inserted.businessId(), inserted.tenantSchema(),
                inserted.reporterUserId(), inserted.reporterEmail(), inserted.reporterRole(),
                inserted.type(), inserted.title(), inserted.description(), inserted.screenPath(),
                inserted.appVersion(), inserted.userAgent(), inserted.device(), inserted.status(),
                inserted.resolutionNote(), inserted.createdAt(), inserted.updatedAt(),
                inserted.resolvedAt(), inserted.resolvedBy(),
                images == null ? List.of() : images);
    }

    /** Envío de correo best-effort tras el commit: nunca tumba el ticket ya creado. */
    private void notifyNewTicket(SupportTicket ticket) {
        String inbox = notificationsProperties.support() == null
                ? null : notificationsProperties.support().inbox();
        if (inbox == null || inbox.isBlank()) {
            log.warn("[SUPPORT] SUPPORT_INBOX_EMAIL en blanco; correo de soporte NO enviado "
                    + "(ticket {} creado igual).", ticket.publicId());
            return;
        }
        try {
            String subject = "[Soporte PuntoNube] " + ticket.type() + ": " + ticket.title();
            String html = buildEmailHtml(ticket);
            notifications.sendEmail(inbox, subject, html, null, null);
        } catch (RuntimeException ex) {
            // Aislado: un fallo de correo no afecta el resultado del alta.
            log.warn("[SUPPORT] Fallo al notificar el ticket {} (se ignora). Causa: {}",
                    ticket.publicId(), ex.getClass().getSimpleName());
        }
    }

    /** Cuerpo HTML del correo, con los campos del ticket escapados (anti-XSS). */
    private static String buildEmailHtml(SupportTicket t) {
        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(esc(t.type().name())).append(": ").append(esc(t.title())).append("</h2>");
        sb.append("<ul>");
        sb.append("<li><b>Negocio:</b> ").append(t.businessId()).append("</li>");
        sb.append("<li><b>Reportó:</b> ").append(esc(t.reporterEmail() != null ? t.reporterEmail()
                : t.reporterUserId())).append(" (").append(esc(t.reporterRole())).append(")</li>");
        sb.append("<li><b>Pantalla:</b> ").append(esc(t.screenPath())).append("</li>");
        sb.append("<li><b>Dispositivo:</b> ").append(esc(t.device())).append("</li>");
        sb.append("<li><b>Versión:</b> ").append(esc(t.appVersion())).append("</li>");
        sb.append("<li><b>Ticket:</b> ").append(esc(String.valueOf(t.publicId()))).append("</li>");
        sb.append("</ul>");
        sb.append("<p><b>Descripción:</b></p>");
        sb.append("<p>").append(esc(t.description())).append("</p>");
        if (t.images() != null && !t.images().isEmpty()) {
            sb.append("<p><b>Imágenes:</b></p><ul>");
            for (SupportTicketImage img : t.images()) {
                String url = esc(img.url());
                sb.append("<li><a href=\"").append(url).append("\">").append(url).append("</a></li>");
            }
            sb.append("</ul>");
        }
        return sb.toString();
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value);
    }

    // =====================================================================================
    // LIST / GET
    // =====================================================================================

    @Override
    public List<SupportTicket> list(TicketQuery query, RequesterScope scope) {
        return repo.find(query, scope);
    }

    @Override
    public SupportTicket get(UUID publicId, RequesterScope scope) {
        SupportTicket ticket = repo.findByPublicId(publicId)
                .orElseThrow(() -> new TicketNotFoundException("Ticket de soporte no encontrado."));
        if (!isVisible(ticket, scope)) {
            // No se revela la existencia de tickets fuera de alcance: se trata como no encontrado.
            throw new TicketNotFoundException("Ticket de soporte no encontrado.");
        }
        return ticket;
    }

    // =====================================================================================
    // CHANGE STATUS
    // =====================================================================================

    @Override
    public SupportTicket changeStatus(UUID publicId, TicketStatus newStatus, String note,
                                      Actor actor, RequesterScope scope) {
        if (newStatus == null) {
            throw new SupportValidationException("El estado destino es obligatorio.");
        }
        SupportTicket current = repo.findByPublicId(publicId)
                .orElseThrow(() -> new TicketNotFoundException("Ticket de soporte no encontrado."));
        if (!isVisible(current, scope)) {
            throw new TicketNotFoundException("Ticket de soporte no encontrado.");
        }
        TicketStatus from = current.status();
        if (from == null || !from.canTransitionTo(newStatus)) {
            throw new SupportValidationException(
                    "Transición de estado inválida: " + from + " → " + newStatus + ".");
        }

        Instant now = Instant.now();
        boolean resolving = newStatus == TicketStatus.RESUELTO
                || newStatus == TicketStatus.DESCARTADO
                || newStatus == TicketStatus.CERRADO;
        String resolvedBy = actor == null ? null : actor.subject();
        SupportTicket updated = repo.updateStatusByPublicId(
                publicId, newStatus, note,
                resolving ? resolvedBy : current.resolvedBy(),
                resolving ? now : current.resolvedAt());

        // Auditar con las claves EXACTAS del §7.
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("from", from.name());
        details.put("to", newStatus.name());
        details.put("note", note);
        details.put("device", actor == null ? null : actor.device());
        details.put("userAgent", actor == null ? null : truncateNullable(actor.userAgent(), USER_AGENT_MAX));
        details.put("at", now.toString());
        auditService.recordGlobal(resolvedBy, "SUPPORT_TICKET_STATUS_CHANGED",
                "support_ticket", publicId.toString(), details);

        return updated;
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private long requireResolvedBusiness(String tenantSchema) {
        if (tenantSchema == null || tenantSchema.isBlank()) {
            throw new SupportValidationException("No hay negocio en contexto para la operación de soporte.");
        }
        return repo.resolveBusinessIdBySchema(tenantSchema)
                .orElseThrow(() -> new SupportValidationException("Negocio no resuelto para el schema."));
    }

    private static boolean isVisible(SupportTicket ticket, RequesterScope scope) {
        return switch (scope) {
            case RequesterScope.SuperAdmin ignored -> true;
            case RequesterScope.BusinessOwner owner ->
                    ticket.businessId() != null && ticket.businessId() == owner.businessId();
            case RequesterScope.Reporter reporter ->
                    ticket.businessId() != null && ticket.businessId() == reporter.businessId()
                            && reporter.reporterUserId() != null
                            && reporter.reporterUserId().equals(ticket.reporterUserId());
        };
    }

    private static String requireTrimmed(String value, String field, int min, int max) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() < min || trimmed.length() > max) {
            throw new SupportValidationException(
                    "El " + field + " debe tener entre " + min + " y " + max + " caracteres.");
        }
        return trimmed;
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
        // Si no cumple el patrón (§5.4) no se pierde el dato: se guarda truncado tal cual (es texto,
        // nunca entra a SQL por concatenación y se renderiza escapado en UI/correo). El regex es la
        // referencia de forma válida documentada en el diseño.
        if (!SCREEN_PATH_RE.matcher(truncated).matches()) {
            log.debug("[SUPPORT] screenPath fuera del patrón esperado; se guarda truncado tal cual.");
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

    /** Valida cantidad, tamaño y tipo real (magic bytes) de las imágenes. */
    private ImageValidationResult validateImages(List<UploadedImage> images) {
        List<UploadedImage> valid = new ArrayList<>();
        List<RejectedImage> rejected = new ArrayList<>();
        if (images == null || images.isEmpty()) {
            return new ImageValidationResult(valid, rejected);
        }
        int acceptedCount = 0;
        for (int i = 0; i < images.size(); i++) {
            UploadedImage img = images.get(i);
            byte[] bytes = img == null ? null : img.bytes();
            if (bytes == null || bytes.length == 0) {
                rejected.add(new RejectedImage(i, "TYPE"));
                continue;
            }
            if (bytes.length > imageProperties.maxBytes()) {
                rejected.add(new RejectedImage(i, "SIZE"));
                continue;
            }
            if (!isSupportedImage(bytes)) {
                rejected.add(new RejectedImage(i, "TYPE"));
                continue;
            }
            if (acceptedCount >= imageProperties.maxCount()) {
                // Excede la cantidad permitida: se marca como COUNT (en Rama A rechaza todo; en Rama B
                // las sobrantes van a rejectedImages).
                rejected.add(new RejectedImage(i, "COUNT"));
                continue;
            }
            valid.add(img);
            acceptedCount++;
        }
        return new ImageValidationResult(valid, rejected);
    }

    /** Tipo real por magic bytes: solo JPEG, PNG o WEBP (no se confía en el content-type del cliente). */
    private static boolean isSupportedImage(byte[] b) {
        return isJpeg(b) || isPng(b) || isWebp(b);
    }

    private static boolean isJpeg(byte[] b) {
        return b.length >= 3
                && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        return b.length >= 8
                && (b[0] & 0xFF) == 0x89 && (b[1] & 0xFF) == 0x50 && (b[2] & 0xFF) == 0x4E
                && (b[3] & 0xFF) == 0x47 && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A
                && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A;
    }

    private static boolean isWebp(byte[] b) {
        // RIFF....WEBP: bytes 0-3 = "RIFF", bytes 8-11 = "WEBP".
        return b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    /** Resultado de la validación de imágenes: válidas a subir + descartadas con su razón. */
    record ImageValidationResult(List<UploadedImage> valid, List<RejectedImage> rejected) {
        boolean hasInvalid() {
            return !rejected.isEmpty();
        }
    }

    /** Imagen descartada: índice original y razón (SIZE | TYPE | COUNT). */
    public record RejectedImage(int index, String reason) {
    }

    /**
     * Rechazo de alta por imagen inválida (Rama A del §11.8): mapea a 422 con {@code title =
     * IMAGE_INVALID} y lleva el detalle de las imágenes inválidas. Extiende
     * {@link SupportValidationException} para reusar el 422 del handler global, y el controller añade
     * la propiedad {@code invalidImages} del cuerpo.
     */
    public static final class SupportImageRejectedException extends SupportValidationException {
        private final transient List<RejectedImage> invalidImages;

        public SupportImageRejectedException(String message, List<RejectedImage> invalidImages) {
            super(message);
            this.invalidImages = invalidImages == null ? List.of() : List.copyOf(invalidImages);
        }

        public List<RejectedImage> invalidImages() {
            return invalidImages;
        }
    }
}
