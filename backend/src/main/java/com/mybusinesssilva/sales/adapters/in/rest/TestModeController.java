package com.mybusinesssilva.sales.adapters.in.rest;

import com.mybusinesssilva.platform.security.AuthenticatedUser;
import com.mybusinesssilva.sales.application.TestModeService;
import com.mybusinesssilva.sales.application.TestModeService.TestModeStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuración del MODO PRUEBA del negocio.
 *
 * <ul>
 * <li>{@code GET /} y {@code PUT /}: administración del dueño (estado + bitácora,
 * activar/desactivar). OWNER-only ({@code @moduleAccess.canManageTestMode()}).</li>
 * <li>{@code GET /active}: estado ligero (solo el flag) para que CUALQUIER
 * usuario del tenant —incluida la cajera— pueda pintar el banner de "modo
 * prueba" en el POS. Solo requiere poder usar el módulo {@code sales}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/settings/test-mode")
public class TestModeController {

    private final TestModeService testModeService;

    public TestModeController(TestModeService testModeService) {
        this.testModeService = testModeService;
    }

    /** Estado del modo prueba + bitácora reciente de cambios. Solo el dueño. */
    @GetMapping
    @PreAuthorize("@moduleAccess.canManageTestMode()")
    public TestModeStatus getStatus() {
        return testModeService.getStatus();
    }

    /**
     * Estado ligero (solo el flag), para el banner del POS. Lo puede leer cualquier
     * usuario del tenant que opere ventas (incluida la cajera), que DEBE ver el
     * banner aunque no administre el modo.
     */
    @GetMapping("/active")
    @PreAuthorize("@moduleAccess.canUse('sales')")
    public Map<String, Boolean> active() {
        return Map.of("active", testModeService.isActive());
    }

    /**
     * Activa/desactiva el modo prueba. Solo el dueño. Registra auditoría (quién,
     * cuándo, dispositivo derivado del User-Agent).
     */
    @PutMapping
    @PreAuthorize("@moduleAccess.canManageTestMode()")
    public TestModeStatus setActive(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @Valid @RequestBody UpdateTestModeRequest request) {
        return testModeService.setActive(
                request.active(),
                actor == null ? "unknown" : actor.subject(),
                userAgent);
    }

    /** Body para activar/desactivar el modo prueba. */
    public record UpdateTestModeRequest(@NotNull Boolean active) {
    }
}
