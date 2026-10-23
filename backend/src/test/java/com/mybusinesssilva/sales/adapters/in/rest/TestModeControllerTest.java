package com.mybusinesssilva.sales.adapters.in.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.platform.security.AuthenticatedUser;
import com.mybusinesssilva.platform.security.ModuleAccessEvaluator;
import com.mybusinesssilva.platform.security.Roles;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Gate OWNER-only del corte PRUEBA→REAL (CA-4).
 *
 * <p>
 * El endpoint {@code POST /api/v1/settings/test-mode/cutover} está anotado con
 * {@code @PreAuthorize("@moduleAccess.canManageTestMode()")} — el MISMO gate que
 * el toggle {@code PUT}. Esta prueba ejercita directamente la expresión de ese
 * gate ({@link ModuleAccessEvaluator#canManageTestMode()}) para confirmar, sin
 * levantar el contexto web, que solo el DUEÑO (o el Super Admin) pasa y que ADMIN
 * y CAJERO quedan fuera (serían 403 antes de entrar al servicio). Sigue el mismo
 * patrón de {@code SecurityUnitTest}.
 */
class TestModeControllerTest {

    private final ModuleAccessEvaluator moduleAccess = new ModuleAccessEvaluator();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerCanRunCutover() {
        authenticate(new AuthenticatedUser("owner@panaderia.mx", "tenant_9",
                List.of(Roles.OWNER), List.of("sales", "bakery")));
        assertThat(moduleAccess.canManageTestMode()).isTrue();
    }

    @Test
    void superAdminCanRunCutover() {
        authenticate(new AuthenticatedUser("admin@mybusinesssilva.com", "",
                List.of(Roles.SUPER_ADMIN), List.of()));
        assertThat(moduleAccess.canManageTestMode()).isTrue();
    }

    @Test
    void adminCannotRunCutover() {
        authenticate(new AuthenticatedUser("admin@panaderia.mx", "tenant_9",
                List.of(Roles.ADMIN), List.of("sales", "bakery")));
        assertThat(moduleAccess.canManageTestMode()).isFalse();
    }

    @Test
    void cashierCannotRunCutover() {
        authenticate(new AuthenticatedUser("cajera@panaderia.mx", "tenant_9",
                List.of(Roles.CASHIER), List.of("sales", "bakery")));
        assertThat(moduleAccess.canManageTestMode()).isFalse();
    }

    @Test
    void anonymousCannotRunCutover() {
        SecurityContextHolder.clearContext();
        assertThat(moduleAccess.canManageTestMode()).isFalse();
    }

    private void authenticate(AuthenticatedUser user) {
        var auth = new UsernamePasswordAuthenticationToken(
                user, null, AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
