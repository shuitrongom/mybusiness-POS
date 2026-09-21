package com.mybusinesssilva.payments.adapters.out.provider;

import com.mybusinesssilva.payments.domain.port.out.RechargeProviderPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador SIMULADO (sandbox) del agregador de recargas/servicios. Permite probar el flujo
 * completo sin un contrato real. Calcula una comisión de ejemplo (porcentaje del monto).
 *
 * <p>Al contratar un agregador real (TAECEL, Multiprepago, etc.) se implementa otro adaptador
 * de {@link RechargeProviderPort} apuntando a su API y se activa por configuración
 * ({@code app.payments.provider=real}). El dominio y los casos de uso NO cambian.
 *
 * <p>Activo por defecto o cuando {@code app.payments.provider=simulated}.
 */
@Component
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedRechargeProvider implements RechargeProviderPort {

    // Comisión de ejemplo: 3% para recargas, 2% para servicios.
    private static final BigDecimal RECHARGE_RATE = new BigDecimal("0.03");
    private static final BigDecimal SERVICE_RATE = new BigDecimal("0.02");

    @Override
    public ProviderResult recharge(String carrier, String phone, BigDecimal amount) {
        if (phone == null || phone.isBlank() || amount == null || amount.signum() <= 0) {
            return ProviderResult.failure("Datos de recarga inválidos");
        }
        BigDecimal commission = amount.multiply(RECHARGE_RATE).setScale(2, RoundingMode.HALF_UP);
        return ProviderResult.ok(folio(), commission);
    }

    @Override
    public ProviderResult payService(String biller, String reference, BigDecimal amount) {
        if (reference == null || reference.isBlank() || amount == null || amount.signum() <= 0) {
            return ProviderResult.failure("Datos de pago de servicio inválidos");
        }
        BigDecimal commission = amount.multiply(SERVICE_RATE).setScale(2, RoundingMode.HALF_UP);
        return ProviderResult.ok(folio(), commission);
    }

    private String folio() {
        return "SIM-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
