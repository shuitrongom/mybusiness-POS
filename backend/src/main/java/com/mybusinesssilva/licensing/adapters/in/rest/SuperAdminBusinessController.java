package com.mybusinesssilva.licensing.adapters.in.rest;

import com.mybusinesssilva.licensing.application.BusinessBackupService;
import com.mybusinesssilva.licensing.application.BusinessBackupService.BusinessBackup;
import com.mybusinesssilva.licensing.application.LicensingService;
import com.mybusinesssilva.licensing.application.LicensingService.BusinessDetail;
import com.mybusinesssilva.licensing.application.LicensingService.CreateBusinessResult;
import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.model.BusinessModule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.mybusinesssilva.platform.security.AuthenticatedUser;

/**
 * Panel del Super Admin: gestión de negocios (tenants), licencias y módulos.
 * Todos los endpoints requieren el rol SUPER_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin/businesses")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminBusinessController {

    private final LicensingService licensingService;
    private final BusinessBackupService backupService;

    public SuperAdminBusinessController(LicensingService licensingService,
                                        BusinessBackupService backupService) {
        this.licensingService = licensingService;
        this.backupService = backupService;
    }

    @GetMapping
    public List<BusinessView> list() {
        return licensingService.listBusinesses().stream().map(BusinessView::from).toList();
    }

    @PostMapping
    public ResponseEntity<CreatedBusinessView> create(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @Valid @RequestBody CreateBusinessRequest request) {
        CreateBusinessResult result = licensingService.createBusiness(
                actorEmail(actor), request.name(), request.rfc(),
                request.businessLine(), request.planId(), request.trialMonths(),
                request.ownerEmail(), request.ownerName(), request.ownerWhatsapp());
        return ResponseEntity.status(HttpStatus.CREATED).body(CreatedBusinessView.from(result));
    }

    @GetMapping("/{id}")
    public BusinessDetailView detail(@PathVariable long id) {
        return BusinessDetailView.from(licensingService.businessDetail(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        licensingService.deleteBusiness(actorEmail(actor), id);
        return ResponseEntity.noContent().build();
    }

    /** Genera y descarga un respaldo JSON con todos los datos del negocio. */
    @GetMapping("/{id}/backup")
    public ResponseEntity<BusinessBackup> backup(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        BusinessBackup backup = backupService.export(actorEmail(actor), id);
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + BusinessBackupService.suggestedFilename(backup) + "\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(backup);
    }

    @PostMapping("/{id}/purchase")
    public ResponseEntity<Void> purchase(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        licensingService.purchaseLicense(actorEmail(actor), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/modules")
    public ResponseEntity<Void> sellModule(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable long id,
            @Valid @RequestBody SellModuleRequest request) {
        licensingService.sellSurchargeModule(actorEmail(actor), id, request.moduleKey(), request.price());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<Void> suspend(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        licensingService.suspend(actorEmail(actor), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reactivate")
    public ResponseEntity<Void> reactivate(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable long id) {
        licensingService.reactivate(actorEmail(actor), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/modules")
    public List<String> enabledModules(@PathVariable long id) {
        return licensingService.enabledModules(id);
    }

    private String actorEmail(AuthenticatedUser actor) {
        return actor == null ? "unknown" : actor.subject();
    }

    // --- DTOs ---

    /**
     * Alta de negocio, incluyendo los datos del usuario Dueño inicial.
     *
     * <p>Validaciones:
     * <ul>
     *   <li>RFC (opcional): formato SAT de persona moral (12) o física (13). Se acepta vacío.</li>
     *   <li>Correo del dueño: formato de correo válido.</li>
     *   <li>WhatsApp del dueño: 10 dígitos (número nacional de México), opcionalmente con +52.
     *       El frontend lo normaliza; aquí se admite con o sin lada.</li>
     * </ul>
     */
    public record CreateBusinessRequest(
            @NotBlank String name,
            @Pattern(regexp = "^$|^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$",
                    message = "El RFC no tiene un formato válido")
            String rfc,
            @NotBlank String businessLine,
            @NotNull Long planId,
            @Min(0) int trialMonths,
            @NotBlank @Email(message = "El correo del dueño no es válido") String ownerEmail,
            @NotBlank String ownerName,
            @Pattern(regexp = "^$|^(\\+?52)?\\s*(\\d\\s*){10}$",
                    message = "El WhatsApp debe tener 10 dígitos (lada de México)")
            String ownerWhatsapp) {
    }

    /** Venta de módulo adicional (excedente). */
    public record SellModuleRequest(
            @NotBlank String moduleKey,
            @NotNull BigDecimal price) {
    }

    /** Vista de negocio para la API. */
    public record BusinessView(
            Long id,
            String name,
            String rfc,
            String businessLine,
            String schemaName,
            String status,
            int trialMonths,
            String trialEndsAt,
            Long planId) {

        static BusinessView from(Business b) {
            return new BusinessView(
                    b.getId(), b.getName(), b.getRfc(), b.getBusinessLine(),
                    b.getSchemaName(), b.getStatus().name(), b.getTrialMonths(),
                    b.getTrialEndsAt() == null ? null : b.getTrialEndsAt().toString(),
                    b.getPlanId());
        }
    }

    /**
     * Respuesta del alta de negocio: incluye los datos del negocio y las credenciales del dueño,
     * que se muestran una sola vez para entregarlas al cliente.
     */
    public record CreatedBusinessView(
            BusinessView business,
            OwnerCredentialsView owner,
            boolean emailSent,
            boolean whatsappSent) {

        static CreatedBusinessView from(CreateBusinessResult r) {
            return new CreatedBusinessView(
                    BusinessView.from(r.business()),
                    new OwnerCredentialsView(
                            r.ownerCredentials().email(),
                            r.ownerCredentials().password(),
                            r.ownerWhatsapp()),
                    r.emailSent(),
                    r.whatsappSent());
        }
    }

    /** Credenciales del dueño (contraseña visible solo al crear el negocio). */
    public record OwnerCredentialsView(String email, String password, String whatsapp) {
    }

    /** Detalle de un negocio: datos, módulos habilitados y fechas del ciclo de licencia. */
    public record BusinessDetailView(
            Long id,
            String name,
            String rfc,
            String businessLine,
            String schemaName,
            String status,
            int trialMonths,
            String trialStartsAt,
            String trialEndsAt,
            String purchasedAt,
            Long planId,
            List<ModuleView> modules) {

        static BusinessDetailView from(BusinessDetail d) {
            Business b = d.business();
            List<ModuleView> mods = d.modules().stream().map(ModuleView::from).toList();
            return new BusinessDetailView(
                    b.getId(), b.getName(), b.getRfc(), b.getBusinessLine(),
                    b.getSchemaName(), b.getStatus().name(), b.getTrialMonths(),
                    b.getTrialStartsAt() == null ? null : b.getTrialStartsAt().toString(),
                    b.getTrialEndsAt() == null ? null : b.getTrialEndsAt().toString(),
                    b.getPurchasedAt() == null ? null : b.getPurchasedAt().toString(),
                    b.getPlanId(), mods);
        }
    }

    /** Vista de un módulo habilitado para un negocio. */
    public record ModuleView(
            String moduleKey,
            boolean enabled,
            String origin,
            BigDecimal soldPrice,
            String soldAt) {

        static ModuleView from(BusinessModule m) {
            return new ModuleView(
                    m.moduleKey(), m.enabled(), m.origin().name(),
                    m.soldPrice(),
                    m.soldAt() == null ? null : m.soldAt().toString());
        }
    }
}
