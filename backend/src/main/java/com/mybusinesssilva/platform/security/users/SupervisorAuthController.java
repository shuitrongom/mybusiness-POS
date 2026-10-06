package com.mybusinesssilva.platform.security.users;

import com.mybusinesssilva.platform.security.AuthenticatedUser;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autorización de acciones sensibles del POS mediante PIN de supervisor, y gestión del PIN propio.
 * Accesible por cualquier usuario operativo (incluido el cajero) que use el punto de venta.
 */
@RestController
@RequestMapping("/api/v1/authorize")
@PreAuthorize("@moduleAccess.canUse('sales')")
public class SupervisorAuthController {

    private final SupervisorAuthService supervisorAuthService;

    public SupervisorAuthController(SupervisorAuthService supervisorAuthService) {
        this.supervisorAuthService = supervisorAuthService;
    }

    /** Verifica el PIN de un supervisor para autorizar una acción. */
    @PostMapping("/supervisor")
    public ResponseEntity<Map<String, Object>> authorize(@RequestBody AuthorizeRequest request) {
        return supervisorAuthService.authorize(request.pin())
                .map(name -> ResponseEntity.ok(Map.<String, Object>of("authorized", true, "by", name)))
                .orElseGet(() -> ResponseEntity.status(403).body(Map.of("authorized", false)));
    }

    /** Establece el PIN de supervisor del usuario actual. */
    @PostMapping("/set-pin")
    public Map<String, String> setPin(@AuthenticationPrincipal AuthenticatedUser user,
                                      @RequestBody SetPinRequest request) {
        supervisorAuthService.setPin(user.subject(), request.pin());
        return Map.of("status", "ok");
    }

    public record AuthorizeRequest(@NotBlank String pin, String action) {
    }

    public record SetPinRequest(@NotBlank String pin) {
    }
}
