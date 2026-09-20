package com.mybusinesssilva.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Pruebas unitarias de componentes de seguridad que no requieren base de datos:
 * codificación de contraseñas (Argon2id), MFA (TOTP), limitador de intentos y
 * evaluación de acceso por módulo.
 */
class SecurityUnitTest {

    @Test
    void argon2EncodesAndMatchesPassword() {
        PasswordEncoder encoder = new PasswordConfig().passwordEncoder();
        String hash = encoder.encode("SuperSecreta123!");

        assertThat(hash).startsWith("$argon2");
        assertThat(encoder.matches("SuperSecreta123!", hash)).isTrue();
        assertThat(encoder.matches("incorrecta", hash)).isFalse();
    }

    @Test
    void mfaGeneratesSecretAndValidatesGeneratedCode() throws Exception {
        MfaService mfa = new MfaService();
        String secret = mfa.generateSecret();
        assertThat(secret).isNotBlank();

        // Genera un código válido para "ahora" con el mismo algoritmo y verifica que se acepte.
        var timeProvider = new dev.samstevens.totp.time.SystemTimeProvider();
        var codeGenerator = new dev.samstevens.totp.code.DefaultCodeGenerator();
        long bucket = Math.floorDiv(timeProvider.getTime(), 30);
        String code = codeGenerator.generate(secret, bucket);

        assertThat(mfa.verifyCode(secret, code)).isTrue();
        assertThat(mfa.verifyCode(secret, "000000")).isFalse();

        assertThat(mfa.buildOtpAuthUri("owner@negocio.mx", secret))
                .startsWith("otpauth://totp/");
    }

    @Test
    void loginRateLimiterBlocksAfterMaxFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String key = "attacker@evil.mx";

        assertThat(limiter.isBlocked(key)).isFalse();
        for (int i = 0; i < 5; i++) {
            limiter.recordFailure(key);
        }
        assertThat(limiter.isBlocked(key)).isTrue();

        limiter.reset(key);
        assertThat(limiter.isBlocked(key)).isFalse();
    }

    @Test
    void moduleAccessRequiresEnabledModuleForNonSuperAdmin() {
        ModuleAccessEvaluator evaluator = new ModuleAccessEvaluator();

        // Cajero de un negocio con solo el módulo de ventas habilitado.
        AuthenticatedUser cashier = new AuthenticatedUser(
                "cajero@negocio.mx", "tenant_5",
                List.of(Roles.CASHIER), List.of("sales"));
        setAuthenticated(cashier);

        assertThat(evaluator.canUse("sales")).isTrue();
        assertThat(evaluator.canUse("invoicing")).isFalse();

        SecurityContextHolder.clearContext();
    }

    @Test
    void superAdminBypassesModuleGating() {
        ModuleAccessEvaluator evaluator = new ModuleAccessEvaluator();

        AuthenticatedUser superAdmin = new AuthenticatedUser(
                "admin@mybusinesssilva.com", "",
                List.of(Roles.SUPER_ADMIN), List.of());
        setAuthenticated(superAdmin);

        assertThat(evaluator.canUse("bi")).isTrue();
        assertThat(evaluator.canUse("anything")).isTrue();

        SecurityContextHolder.clearContext();
    }

    private void setAuthenticated(AuthenticatedUser user) {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                user, null, AuthorityUtils.NO_AUTHORITIES);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
