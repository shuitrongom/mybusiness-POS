package com.mybusinesssilva.licensing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mybusinesssilva.licensing.domain.model.Business;
import com.mybusinesssilva.licensing.domain.model.BusinessStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de las reglas del ciclo de licencia en la entidad de dominio {@link Business}.
 * Usan un reloj fijo para hacer deterministas las fechas de prueba y vencimiento.
 */
class BusinessTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void createsInTrialWithFutureExpiration() {
        Business b = Business.createInTrial("Negocio", null, "abarrotes", 1L, 1, fixedClock);

        assertThat(b.getStatus()).isEqualTo(BusinessStatus.TRIAL);
        assertThat(b.getTrialEndsAt()).isAfter(NOW);
        assertThat(b.allowsAccess()).isTrue();
    }

    @Test
    void zeroTrialMonthsStartsExpired() {
        Business b = Business.createInTrial("Negocio", null, "abarrotes", 1L, 0, fixedClock);

        assertThat(b.getStatus()).isEqualTo(BusinessStatus.EXPIRED);
        assertThat(b.allowsAccess()).isFalse();
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> Business.createInTrial("  ", null, "abarrotes", 1L, 1, fixedClock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeTrialMonths() {
        assertThatThrownBy(() -> Business.createInTrial("N", null, "abarrotes", 1L, -1, fixedClock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void trialExpiresAfterDueDate() {
        Business b = Business.createInTrial("Negocio", null, "abarrotes", 1L, 1, fixedClock);

        // Un reloj 40 días después: la prueba de 1 mes (~30 días) ya venció.
        Clock later = Clock.fixed(NOW.plus(Duration.ofDays(40)), ZoneOffset.UTC);
        boolean changed = b.expireTrialIfDue(later);

        assertThat(changed).isTrue();
        assertThat(b.getStatus()).isEqualTo(BusinessStatus.EXPIRED);
        assertThat(b.allowsAccess()).isFalse();
    }

    @Test
    void trialDoesNotExpireBeforeDueDate() {
        Business b = Business.createInTrial("Negocio", null, "abarrotes", 1L, 1, fixedClock);

        Clock soon = Clock.fixed(NOW.plus(Duration.ofDays(10)), ZoneOffset.UTC);
        boolean changed = b.expireTrialIfDue(soon);

        assertThat(changed).isFalse();
        assertThat(b.getStatus()).isEqualTo(BusinessStatus.TRIAL);
    }

    @Test
    void purchaseLicenseActivatesAndIsIdempotent() {
        Business b = Business.createInTrial("Negocio", null, "abarrotes", 1L, 1, fixedClock);

        b.purchaseLicense(fixedClock);
        assertThat(b.getStatus()).isEqualTo(BusinessStatus.ACTIVE);
        assertThat(b.getPurchasedAt()).isEqualTo(NOW);

        Instant firstPurchase = b.getPurchasedAt();
        b.purchaseLicense(Clock.fixed(NOW.plus(Duration.ofDays(5)), ZoneOffset.UTC));
        assertThat(b.getPurchasedAt()).isEqualTo(firstPurchase); // no cambia si ya está activo
    }

    @Test
    void suspendAndReactivate() {
        Business b = Business.createInTrial("Negocio", null, "abarrotes", 1L, 1, fixedClock);
        b.purchaseLicense(fixedClock);

        b.suspend();
        assertThat(b.getStatus()).isEqualTo(BusinessStatus.SUSPENDED);
        assertThat(b.allowsAccess()).isFalse();

        b.reactivate();
        assertThat(b.getStatus()).isEqualTo(BusinessStatus.ACTIVE);
        assertThat(b.allowsAccess()).isTrue();
    }
}
