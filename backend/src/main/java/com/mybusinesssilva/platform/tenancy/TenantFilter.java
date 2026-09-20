package com.mybusinesssilva.platform.tenancy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filtro que fija el tenant de la petición en el {@link TenantContext} al inicio y lo limpia
 * al final, garantizando que ninguna petición herede el contexto de otra.
 *
 * <p>Se ejecuta muy temprano en la cadena de filtros para que toda la lógica posterior
 * (incluida la persistencia) opere ya con el tenant correcto.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantFilter extends OncePerRequestFilter {

    private final TenantResolver tenantResolver;

    public TenantFilter(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            tenantResolver.resolve(request).ifPresent(TenantContext::setTenantId);
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
