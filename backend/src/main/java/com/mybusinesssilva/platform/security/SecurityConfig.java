package com.mybusinesssilva.platform.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración central de seguridad HTTP.
 *
 * <p>Características:
 * <ul>
 *   <li>Sin estado de sesión (stateless): la identidad viaja en el JWT.</li>
 *   <li>CSRF deshabilitado por ser una API stateless con tokens Bearer.</li>
 *   <li>Endpoints públicos: autenticación y health checks. El resto requiere autenticación.</li>
 *   <li>Seguridad a nivel de método habilitada para usar {@code @PreAuthorize}.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Health/info del actuator abiertos: Railway/plataforma los consultan sin
                        // token para el health check. Se cubre el endpoint y sus sub-rutas
                        // (ej. /actuator/health/liveness) para no depender de la forma exacta.
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/api/v1/auth/**",
                                "/api/v1/self-invoice/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        // 401 cuando NO hay autenticación válida (token ausente/expirado/ inválido).
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        // 403 cuando el usuario está autenticado pero NO tiene permiso para el recurso.
                        // (No debe expulsar al usuario; solo denegar ese recurso.)
                        .accessDeniedHandler((request, response, ex2) ->
                                response.sendError(HttpStatus.FORBIDDEN.value(), "Acceso denegado")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Evita que el contenedor de servlets registre por su cuenta el filtro JWT (por ser un
     * {@code @Component}). El filtro debe ejecutarse ÚNICAMENTE dentro de la cadena de Spring
     * Security (añadido con {@code addFilterBefore}); un doble registro haría que la autenticación
     * establecida se pierda en la cadena general.
     */
    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<JwtAuthenticationFilter>
            disableAutoRegistration(JwtAuthenticationFilter filter) {
        var registration = new org.springframework.boot.web.servlet.FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Orígenes permitidos para CORS. En desarrollo, "*" (cualquiera). En producción se definen los
     * dominios del front/portal con la variable de entorno CORS_ALLOWED_ORIGINS (lista separada por
     * comas, ej. "https://app.tudominio.com,https://*.tudominio.com").
     */
    @org.springframework.beans.factory.annotation.Value("${CORS_ALLOWED_ORIGINS:*}")
    private String corsAllowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = java.util.Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList();
        configuration.setAllowedOriginPatterns(origins.isEmpty() ? List.of("*") : origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
