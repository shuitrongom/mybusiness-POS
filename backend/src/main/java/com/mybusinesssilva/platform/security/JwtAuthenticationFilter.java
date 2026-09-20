package com.mybusinesssilva.platform.security;

import com.mybusinesssilva.platform.tenancy.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro que autentica cada petición a partir del token JWT de acceso.
 *
 * <p>Lee el token del encabezado {@code Authorization: Bearer <token>}. Si es válido:
 * <ul>
 *   <li>Construye el {@link AuthenticatedUser} con subject, tenant, roles y módulos.</li>
 *   <li>Establece la autenticación en el contexto de Spring Security.</li>
 *   <li>Fija el tenant en {@link TenantContext} (fuente de verdad del tenant es el JWT).</li>
 * </ul>
 *
 * <p>Si no hay token o es inválido, la petición continúa sin autenticación; las reglas de
 * autorización decidirán si el endpoint requiere o no estar autenticado.
 *
 * <p>Se ejecuta antes que el filtro de resolución de tenant por subdominio, de modo que el
 * tenant del JWT tenga prioridad.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null) {
            authenticate(token);
        }
        // La limpieza del contexto de seguridad y del TenantContext se realiza en filtros
        // dedicados al terminar la petición, evitando fugas entre peticiones del mismo hilo.
        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private void authenticate(String token) {
        try {
            Claims claims = jwtService.parse(token);
            if (!jwtService.isAccessToken(claims)) {
                return;
            }

            String subject = claims.getSubject();
            String tenant = jwtService.tenantOf(claims);
            List<String> roles = jwtService.rolesOf(claims);
            List<String> modules = jwtService.modulesOf(claims);

            AuthenticatedUser principal = new AuthenticatedUser(subject, tenant, roles, modules);

            var authorities = roles.stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .toList();

            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // El tenant del token es la fuente de verdad para la persistencia.
            if (StringUtils.hasText(tenant)) {
                TenantContext.setTenantId(tenant);
            }
        } catch (Exception ex) {
            // Token inválido o expirado: se ignora y la petición sigue sin autenticación.
            SecurityContextHolder.clearContext();
        }
    }
}
