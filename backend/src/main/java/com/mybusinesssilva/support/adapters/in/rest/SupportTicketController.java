package com.mybusinesssilva.support.adapters.in.rest;

import com.mybusinesssilva.platform.security.AuthenticatedUser;
import com.mybusinesssilva.platform.security.Roles;
import com.mybusinesssilva.platform.util.DeviceDetector;
import com.mybusinesssilva.support.adapters.in.rest.dto.ChangeStatusRequest;
import com.mybusinesssilva.support.adapters.in.rest.dto.CreateTicketRequest;
import com.mybusinesssilva.support.adapters.in.rest.dto.TicketListItem;
import com.mybusinesssilva.support.adapters.in.rest.dto.TicketView;
import com.mybusinesssilva.support.adapters.in.rest.dto.MessageView;
import com.mybusinesssilva.support.adapters.in.rest.dto.PostMessageRequest;
import com.mybusinesssilva.support.domain.model.AuthorType;
import com.mybusinesssilva.support.domain.model.TicketContext;
import com.mybusinesssilva.support.domain.model.TicketStatus;
import com.mybusinesssilva.support.domain.model.TicketType;
import com.mybusinesssilva.support.domain.port.in.Actor;
import com.mybusinesssilva.support.domain.port.in.ChangeTicketStatusUseCase;
import com.mybusinesssilva.support.domain.port.in.CreateSupportTicketUseCase;
import com.mybusinesssilva.support.domain.port.in.CreateSupportTicketUseCase.NewTicketCommand;
import com.mybusinesssilva.support.domain.port.in.CreateSupportTicketUseCase.UploadedImage;
import com.mybusinesssilva.support.domain.port.in.GetSupportTicketUseCase;
import com.mybusinesssilva.support.domain.port.in.ListSupportTicketsUseCase;
import com.mybusinesssilva.support.domain.port.in.ListThreadUseCase;
import com.mybusinesssilva.support.domain.port.in.PostMessageUseCase;
import com.mybusinesssilva.support.domain.exception.SupportValidationException;
import com.mybusinesssilva.support.domain.port.in.RequesterScope;
import com.mybusinesssilva.support.domain.port.in.TicketQuery;
import com.mybusinesssilva.support.domain.port.out.SupportTicketRepository;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * API del buzón de soporte para usuarios del negocio (OWNER y CASHIER del
 * tenant).
 *
 * <ul>
 * <li>{@code POST /} — crear ticket (multipart: parte {@code data} JSON +
 * partes {@code images}).</li>
 * <li>{@code GET /} — listar los tickets visibles según el alcance del
 * solicitante.</li>
 * <li>{@code GET /{publicId}} — detalle (404 si no es visible).</li>
 * <li>{@code PATCH /{publicId}/status} — cambiar estado (solo OWNER).</li>
 * </ul>
 *
 * <p>
 * El {@code TicketContext} y el {@code RequesterScope} se derivan en el
 * controller a partir del
 * {@code AuthenticatedUser} y el request (nunca del cliente).
 */
@RestController
@RequestMapping("/api/v1/support/tickets")
public class SupportTicketController {

    private final CreateSupportTicketUseCase createUseCase;
    private final ListSupportTicketsUseCase listUseCase;
    private final GetSupportTicketUseCase getUseCase;
    private final ChangeTicketStatusUseCase changeStatusUseCase;
    private final SupportTicketRepository repository;
    private final ListThreadUseCase listThreadUseCase;
    private final PostMessageUseCase postMessageUseCase;

    public SupportTicketController(CreateSupportTicketUseCase createUseCase,
            ListSupportTicketsUseCase listUseCase,
            GetSupportTicketUseCase getUseCase,
            ChangeTicketStatusUseCase changeStatusUseCase,
            SupportTicketRepository repository,
            ListThreadUseCase listThreadUseCase,
            PostMessageUseCase postMessageUseCase) {
        this.createUseCase = createUseCase;
        this.listUseCase = listUseCase;
        this.getUseCase = getUseCase;
        this.changeStatusUseCase = changeStatusUseCase;
        this.listThreadUseCase = listThreadUseCase;
        this.postMessageUseCase = postMessageUseCase;
        this.repository = repository;
    }

    /**
     * Crea un ticket. OWNER o CASHIER autenticados del tenant (nunca el
     * super-admin).
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated() and !hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketView create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @Valid @RequestPart("data") CreateTicketRequest data,
            @RequestPart(value = "images", required = false) MultipartFile[] images) {

        String device = DeviceDetector.classify(userAgent);
        TicketContext context = new TicketContext(
                user == null ? null : user.tenant(),
                null, // businessId lo resuelve el servicio (barrera dura) desde el tenant
                user == null ? null : user.subject(),
                user == null ? null : user.subject(), // reporterEmail = subject (es el correo del token)
                primaryRole(user),
                data.screenPath(),
                data.appVersion(),
                userAgent,
                device);

        NewTicketCommand command = new NewTicketCommand(
                data.type(), data.title(), data.description(),
                data.screenPath(), data.appVersion(), context, toUploadedImages(images));

        return TicketView.from(createUseCase.create(command));
    }

    /**
     * Lista los tickets visibles para el solicitante según su alcance y filtros.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated() and !hasRole('SUPER_ADMIN')")
    public List<TicketListItem> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) TicketType type,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String q) {

        RequesterScope scope = tenantScope(user);
        // businessId no se expone al cliente de negocio: el scope ya impone su negocio.
        TicketQuery query = new TicketQuery(null, type, status, from, to, q);
        return listUseCase.list(query, scope).stream().map(TicketListItem::from).toList();
    }

    /**
     * Detalle de un ticket por {@code publicId} (404 si no es visible para el
     * solicitante).
     */
    @GetMapping("/{publicId}")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPER_ADMIN')")
    public TicketView detail(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID publicId) {
        return TicketView.from(getUseCase.get(publicId, tenantScope(user)));
    }

    /** Cambia el estado de un ticket. Solo OWNER (la cajera no cambia estado). */
    @PatchMapping("/{publicId}/status")
    @PreAuthorize("isAuthenticated() and hasRole('OWNER')")
    public TicketView changeStatus(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @PathVariable UUID publicId,
            @Valid @RequestBody ChangeStatusRequest request) {

        Actor actor = new Actor(
                user == null ? null : user.subject(),
                DeviceDetector.classify(userAgent),
                userAgent);
        return TicketView.from(changeStatusUseCase.changeStatus(
                publicId, request.status(), request.note(), actor, tenantScope(user)));
    }

    /**
     * Lista el hilo de mensajes del ticket visible para el solicitante de tenant.
     */
    @GetMapping("/{publicId}/messages")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPER_ADMIN')")
    public List<MessageView> listThread(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID publicId) {
        return listThreadUseCase.listThread(publicId, tenantScope(user))
                .stream().map(MessageView::from).toList();
    }

    /**
     * Publica un mensaje del reportante en el hilo. {@code author_type=REPORTER}
     * fijo (nunca del cliente).
     */
    @PostMapping("/{publicId}/messages")
    @PreAuthorize("isAuthenticated() and !hasRole('SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageView postMessage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID publicId,
            @Valid @RequestBody PostMessageRequest request) {
        PostMessageUseCase.Author author = new PostMessageUseCase.Author(
                AuthorType.REPORTER,
                primaryRole(user),
                user == null ? null : user.subject(),
                user == null ? null : user.subject(),
                sanitizeAppVersion(request.appVersion()),
                sanitizeScreenPath(request.screenPath()));
        return MessageView.from(postMessageUseCase.postMessage(
                publicId, request.body(), author, tenantScope(user)));
    }

    // ----- Derivación del alcance y utilidades (nunca del cliente) -----

    /**
     * Construye el alcance del solicitante de tenant con precedencia determinista
     * (§3): OWNER o ADMIN
     * ven todo su negocio; el resto de roles (CASHIER, etc.) solo lo que crearon.
     * Exige tenant
     * presente y {@code business_id} resuelto (barrera dura): si falta, 422 — nunca
     * se consulta sin
     * negocio. El {@code businessId}/{@code reporterUserId} salen del token, nunca
     * del cliente.
     */
    private RequesterScope tenantScope(AuthenticatedUser user) {
        if (user == null) {
            throw new SupportValidationException("No hay negocio en contexto para la operación de soporte.");
        }
        String tenant = user.tenant();
        if (tenant == null || tenant.isBlank()) {
            throw new SupportValidationException("No hay negocio en contexto para la operación de soporte.");
        }
        long businessId = repository.resolveBusinessIdBySchema(tenant)
                .orElseThrow(() -> new SupportValidationException("Negocio no resuelto para el schema."));
        if (user.hasRole(Roles.OWNER) || user.hasRole(Roles.ADMIN)) {
            return new RequesterScope.BusinessOwner(businessId);
        }
        return new RequesterScope.Reporter(businessId, user.subject());
    }

    private static String primaryRole(AuthenticatedUser user) {
        if (user == null) {
            return null;
        }
        if (user.hasRole(Roles.OWNER)) {
            return Roles.OWNER;
        }
        if (user.hasRole(Roles.ADMIN)) {
            return Roles.ADMIN;
        }
        if (user.hasRole(Roles.SUPERVISOR)) {
            return Roles.SUPERVISOR;
        }
        if (user.hasRole(Roles.CASHIER)) {
            return Roles.CASHIER;
        }
        return user.roles().stream().findFirst().orElse(null);
    }

    /**
     * Trunca la versión del front a 60 (misma lógica del alta). El servicio la
     * vuelve a aplicar.
     */
    private static String sanitizeAppVersion(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= 60 ? trimmed : trimmed.substring(0, 60);
    }

    /**
     * Sanea la ruta del front (misma lógica del alta). El servicio la vuelve a
     * aplicar.
     */
    private static String sanitizeScreenPath(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300);
    }

    private static List<UploadedImage> toUploadedImages(MultipartFile[] images) {
        if (images == null || images.length == 0) {
            return List.of();
        }
        List<UploadedImage> result = new ArrayList<>(images.length);
        for (MultipartFile file : images) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            try {
                result.add(new UploadedImage(
                        file.getOriginalFilename(), file.getContentType(), file.getBytes()));
            } catch (IOException ex) {
                throw new IllegalStateException("No se pudo leer una imagen adjunta.", ex);
            }
        }
        return result;
    }
}
