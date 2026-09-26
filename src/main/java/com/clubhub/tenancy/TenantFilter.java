package com.clubhub.tenancy;

import com.clubhub.tenant.Tenant;
import com.clubhub.tenant.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * For every /api/club/** request: resolve the club, reject unknown/suspended ones,
 * and run the rest of the request inside that club's TenantContext.
 * Platform endpoints (/api/platform/**) and actuator are left untouched.
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    public static final String CLUB_API_PREFIX = "/api/club/";

    private final TenantResolver tenantResolver;
    private final TenantRepository tenantRepository;

    public TenantFilter(TenantResolver tenantResolver, TenantRepository tenantRepository) {
        this.tenantResolver = tenantResolver;
        this.tenantRepository = tenantRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(CLUB_API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Optional<String> slug = tenantResolver.resolveSlug(request);
        if (slug.isEmpty()) {
            writeProblem(response, HttpStatus.BAD_REQUEST, "Missing " + HeaderTenantResolver.HEADER + " header");
            return;
        }

        // Validate format first: never query (or later, cache) arbitrary client input
        Optional<Tenant> tenant = Tenant.SLUG_PATTERN.matcher(slug.get()).matches()
                ? tenantRepository.findBySlug(slug.get())
                : Optional.empty();

        if (tenant.isEmpty()) {
            writeProblem(response, HttpStatus.NOT_FOUND, "Unknown club");
            return;
        }
        if (!tenant.get().isActive()) {
            writeProblem(response, HttpStatus.FORBIDDEN, "Club is suspended");
            return;
        }

        try {
            TenantContext.callAs(tenant.get().getSchemaName(), () -> {
                chain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private static void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        // detail is always a fixed server-side string, never echoed client input
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s"}"""
                .formatted(status.getReasonPhrase(), status.value(), detail));
    }
}
