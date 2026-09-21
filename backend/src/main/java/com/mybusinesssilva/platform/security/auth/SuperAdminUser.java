package com.mybusinesssilva.platform.security.auth;

/**
 * Usuario del Super Admin (proveedor del SaaS), almacenado en el schema global {@code admin}.
 *
 * @param id           identificador
 * @param email        correo (identificador de acceso)
 * @param passwordHash hash Argon2id de la contraseña
 * @param fullName     nombre completo
 * @param mfaEnabled   si tiene doble factor activo
 * @param mfaSecret    secreto TOTP (nulo si MFA no está activo)
 * @param active       si la cuenta está activa
 */
public record SuperAdminUser(
        Long id,
        String email,
        String passwordHash,
        String fullName,
        boolean mfaEnabled,
        String mfaSecret,
        boolean active) {
}
