package com.mybusinesssilva.platform.security;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Limitador de intentos de inicio de sesión para mitigar ataques de fuerza bruta y
 * credential stuffing.
 *
 * <p>Cuenta los intentos fallidos por clave (por ejemplo, correo o IP). Al superar el máximo
 * permitido, bloquea temporalmente nuevos intentos durante una ventana configurable.
 *
 * <p>Implementación en memoria, adecuada para una sola instancia. Al escalar a varias
 * instancias se sustituirá por un almacén distribuido (por ejemplo Redis) manteniendo esta
 * misma interfaz, sin afectar al resto del sistema.
 */
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private record Attempts(int count, Instant lockedUntil) {
    }

    private final ConcurrentHashMap<String, Attempts> attemptsByKey = new ConcurrentHashMap<>();

    /**
     * @param key identificador del origen del intento (correo o IP)
     * @return true si la clave está actualmente bloqueada por exceso de intentos
     */
    public boolean isBlocked(String key) {
        Attempts attempts = attemptsByKey.get(key);
        if (attempts == null || attempts.lockedUntil() == null) {
            return false;
        }
        if (Instant.now().isAfter(attempts.lockedUntil())) {
            attemptsByKey.remove(key);
            return false;
        }
        return true;
    }

    /**
     * Registra un intento fallido. Al alcanzar el máximo, activa el bloqueo temporal.
     */
    public void recordFailure(String key) {
        attemptsByKey.compute(key, (k, current) -> {
            int newCount = (current == null ? 0 : current.count()) + 1;
            Instant lockedUntil = newCount >= MAX_ATTEMPTS
                    ? Instant.now().plus(LOCK_DURATION)
                    : null;
            return new Attempts(newCount, lockedUntil);
        });
    }

    /**
     * Limpia los intentos de una clave tras un inicio de sesión exitoso.
     */
    public void reset(String key) {
        attemptsByKey.remove(key);
    }
}
