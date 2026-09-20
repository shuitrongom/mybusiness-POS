package com.mybusinesssilva.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias del servicio de JWT: emisión y validación de tokens de acceso y refresh,
 * y rechazo de tokens manipulados.
 */
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        var jwt = new SecurityProperties.Jwt(
                "unit-test-secret-key-with-at-least-32-bytes-123456",
                15, 7, "mybusiness-silva-test");
        jwtService = new JwtService(new SecurityProperties(jwt));
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
