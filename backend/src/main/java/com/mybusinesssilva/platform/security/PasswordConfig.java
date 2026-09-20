package com.mybusinesssilva.platform.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuración del codificador de contraseñas.
 *
 * <p>Usa Argon2id, el algoritmo recomendado actualmente para almacenamiento de contraseñas
 * por su resistencia a ataques con hardware especializado (GPU/ASIC). Los parámetros siguen
 * las recomendaciones de referencia (memoria, iteraciones y paralelismo).
 */
@Configuration
public class PasswordConfig {

    // Parámetros de Argon2id (referencia OWASP): saltLen=16, hashLen=32,
    // parallelism=1, memory=19456 KiB (~19 MB), iterations=2.
    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KB = 19456;
    private static final int ITERATIONS = 2;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(
                SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KB, ITERATIONS);
    }
}
