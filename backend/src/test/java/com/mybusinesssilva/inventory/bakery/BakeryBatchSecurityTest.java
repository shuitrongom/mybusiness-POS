package com.mybusinesssilva.inventory.bakery;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.platform.security.AuthenticatedUser;
import com.mybusinesssilva.platform.security.ModuleAccessEvaluator;
import com.mybusinesssilva.platform.security.Roles;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Barreras de acceso del PANADERO, que respaldan los {@code @PreAuthorize} de los
 * controladores. Verifican el acceso por los DOS caminos de {@code canUse}:
 * (a) con permisos granulares y (b) el fallback {@code PANADERO_MODULES} sin ellos.
 * En ambos, el PANADERO alcanza SOLO {@code bakery_prod} y recibe negativa en
 * {@code sales}/{@code cash}/{@code bakery} (panel admin).
 */
class BakeryBatchSecurityTest {

    private final ModuleAccessEvaluator evaluator = new ModuleAccessEvaluator();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(AuthenticatedUser user) {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                user, null, AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void panaderoWithGranularPermissionsReachesOnlyBakeryProd() {
        // Token real del panadero: modules incluye 'bakery' (giro panadería) y permisos
        // granulares VIEW/CREATE solo sobre 'bakery_prod' (lo que siembra V47).
        AuthenticatedUser baker = new AuthenticatedUser(
                "panadero@panaderia.mx", "tenant_9",
                Set.of(Roles.PANADERO),
                Set.of("bakery", "sales", "cash"),
                Set.of("bakery_prod:VIEW", "bakery_prod:CREATE"));
        authenticate(baker);

        // Captura de la tanda: SÍ.
        assertThat(evaluator.canUse("bakery_prod")).isTrue();
        assertThat(evaluator.can("bakery_prod", "CREATE")).isTrue();
        assertThat(evaluator.can("bakery_prod", "VIEW")).isTrue();
        // Panel admin (bakery) y ventas/caja: NO.
        assertThat(evaluator.can("bakery", "VIEW")).isFalse();
        assertThat(evaluator.canUse("sales")).isFalse();
        assertThat(evaluator.canUse("cash")).isFalse();
    }

    @Test
    void panaderoWithoutGranularPermissionsStillBlockedByFallback() {
        // Token SIN permisos granulares (rol mal creado / token viejo): la rama
        // PANADERO_MODULES del fallback debe acotar igual a 'bakery_prod'.
        AuthenticatedUser baker = new AuthenticatedUser(
                "panadero@panaderia.mx", "tenant_9",
                List.of(Roles.PANADERO),
                List.of("bakery", "sales", "cash"));
        authenticate(baker);

        assertThat(evaluator.canUse("bakery_prod")).isTrue();
        // Sin esta rama, el return permisivo del fallback abriría sales/cash/bakery.
        assertThat(evaluator.canUse("sales")).isFalse();
        assertThat(evaluator.canUse("cash")).isFalse();
        assertThat(evaluator.canUse("bakery")).isFalse();
    }

    @Test
    void adminReachesPanelAndCapture() {
        AuthenticatedUser admin = new AuthenticatedUser(
                "admin@panaderia.mx", "tenant_9",
                List.of(Roles.ADMIN), List.of("bakery", "sales", "cash", "inventory"));
        authenticate(admin);

        // OWNER/ADMIN atajan a true en canUse/can cuando el módulo comercial está habilitado.
        assertThat(evaluator.can("bakery", "VIEW")).isTrue();
        assertThat(evaluator.can("bakery_prod", "CREATE")).isTrue();
        assertThat(evaluator.canUse("inventory")).isTrue();
    }

    @Test
    void panaderoCannotAdministerRawMaterials() {
        AuthenticatedUser baker = new AuthenticatedUser(
                "panadero@panaderia.mx", "tenant_9",
                List.of(Roles.PANADERO), List.of("bakery", "inventory"));
        authenticate(baker);
        // La MP se administra por 'inventory', que el panadero no alcanza.
        assertThat(evaluator.canUse("inventory")).isFalse();
    }
}
