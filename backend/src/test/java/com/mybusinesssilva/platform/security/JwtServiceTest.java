package com.mybusinesssilva.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * Pruebas unitarias del servicio de JWT: emisión y validación de tokens de
 * acceso y refresh,
 * y rechazo de tokens manipulados.
 */
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        var jwt = new SecurityProperties.Jwt(
                "unit-test-secret-key-with-at-least-32-bytes-123456",
                15, 7, "mybusiness-silva-test");
        // Sin perfil 'prod' activo: la validación de secreto no aplica en dev/test.
        jwtService = new JwtService(new SecurityProperties(jwt), new MockEnvironment());
    }

    @Test
    void failsToStartInProdWhenSecretIsDevDefault() {
        var jwt = new SecurityProperties.Jwt(
                "dev-secret-change-me-please-32bytes-minimum-0123456789",
                15, 7, "mybusiness-silva");
        var prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtService(new SecurityProperties(jwt), prodEnv))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void failsToStartInProdWhenSecretTooShort() {
        var jwt = new SecurityProperties.Jwt("too-short", 15, 7, "mybusiness-silva");
        var prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtService(new SecurityProperties(jwt), prodEnv))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void startsInProdWhenSecretIsStrong() {
        var jwt = new SecurityProperties.Jwt(
                "prod-secret-super-fuerte-con-mas-de-32-bytes-aleatorios-xyz",
                15, 7, "mybusiness-silva");
        var prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");

        var prodJwtService = new JwtService(new SecurityProperties(jwt), prodEnv);

        String token = prodJwtService.issueAccessToken(
                "owner@negocio.mx", "tenant_10", List.of(Roles.OWNER), List.of("sales"));
        assertThat(prodJwtService.isAccessToken(prodJwtService.parse(token))).isTrue();
    }

    @Test
    void issuesAndParsesAccessTokenWithClaims() {
        String token = jwtService.issueAccessToken(
                "cajero@negocio.mx", "tenant_10",
                List.of(Roles.CASHIER), List.of("sales", "inventory"));

        Claims claims = jwtService.parse(token);

        assertThat(jwtService.isAccessToken(claims)).isTrue();
        assertThat(claims.getSubject()).isEqualTo("cajero@negocio.mx");
        assertThat(jwtService.tenantOf(claims)).isEqualTo("tenant_10");
        assertThat(jwtService.rolesOf(claims)).containsExactly(Roles.CASHIER);
        assertThat(jwtService.modulesOf(claims)).containsExactlyInAnyOrder("sales", "inventory");
    }

    @Test
    void refreshTokenIsDistinguishedFromAccessToken() {
        String refresh = jwtService.issueRefreshToken("owner@negocio.mx", "tenant_10");
        Claims claims = jwtService.parse(refresh);

        assertThat(jwtService.isRefreshToken(claims)).isTrue();
        assertThat(jwtService.isAccessToken(claims)).isFalse();
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.issueAccessToken(
                "x@y.mx", "tenant_1", List.of(Roles.OWNER), List.of());
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtService.parse(tampered))
                .isInstanceOf(Exception.class);
    }
}
