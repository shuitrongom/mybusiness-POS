package com.mybusinesssilva.platform.security;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.stereotype.Service;

/**
 * Servicio de autenticación en dos pasos (MFA) basada en TOTP (códigos temporales de 6 dígitos,
 * compatibles con Google Authenticator, Authy, etc.).
 *
 * <p>Genera el secreto por usuario, permite construir la URI para el código QR de alta, y
 * verifica los códigos que introduce el usuario al iniciar sesión.
 */
@Service
public class MfaService {

    private static final String ISSUER = "MyBusiness Silva";

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(HashingAlgorithm.SHA1);
    private final CodeVerifier codeVerifier =
            new DefaultCodeVerifier(codeGenerator, timeProvider);

    /**
     * @return un nuevo secreto MFA para asociar a un usuario.
     */
    public String generateSecret() {
        return secretGenerator.generate();
    }

    /**
     * Construye la URI {@code otpauth://} que las apps de autenticación leen desde un QR.
     *
     * @param account correo o nombre del usuario
     * @param secret  secreto MFA del usuario
     */
    public String buildOtpAuthUri(String account, String secret) {
        return "otpauth://totp/"
                + urlEncode(ISSUER) + ":" + urlEncode(account)
                + "?secret=" + secret
                + "&issuer=" + urlEncode(ISSUER)
                + "&algorithm=SHA1&digits=6&period=30";
    }

    /**
     * Verifica un código TOTP contra el secreto del usuario.
     *
     * @param secret secreto MFA del usuario
     * @param code   código de 6 dígitos introducido
     * @return true si el código es válido en la ventana de tiempo actual
     */
    public boolean verifyCode(String secret, String code) {
        return codeVerifier.isValidCode(secret, code);
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
