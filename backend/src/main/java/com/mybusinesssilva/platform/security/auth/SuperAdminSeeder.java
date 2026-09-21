package com.mybusinesssilva.platform.security.auth;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea un usuario Super Admin inicial al arrancar, si aún no existe ninguno. Así el sistema es
 * usable desde el primer despliegue (el proveedor del SaaS puede iniciar sesión y crear negocios).
 *
 * <p>Las credenciales iniciales se toman de variables de entorno; si no se definen, se usan
 * valores por defecto SOLO fuera de producción. En producción se DEBE definir
 * {@code SUPERADMIN_EMAIL} y {@code SUPERADMIN_PASSWORD}, y cambiar la contraseña tras el primer
 * acceso.
 *
 * <p>No se ejecuta en el perfil de pruebas para no interferir con los tests.
 */
@Component
@Profile("!test")
public class SuperAdminSeeder implements CommandLineRunner {

    private final SuperAdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String email;
    private final String password;
    private final String fullName;

    public SuperAdminSeeder(
            SuperAdminUserRepository repository,
            PasswordEncoder passwordEncoder,
            org.springframework.core.env.Environment env) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.email = env.getProperty("SUPERADMIN_EMAIL", "admin@mybusinesssilva.com");
        this.password = env.getProperty("SUPERADMIN_PASSWORD", "Admin1234!Cambiar");
        this.fullName = env.getProperty("SUPERADMIN_NAME", "Super Administrador");
    }

    @Override
    public void run(String... args) {
        if (repository.findByEmail(email).isEmpty()) {
            repository.insert(email, passwordEncoder.encode(password), fullName);
        }
    }
}
