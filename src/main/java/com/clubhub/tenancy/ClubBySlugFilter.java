package com.clubhub.tenancy;

import com.clubhub.ratelimit.RateLimitGuard;
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
 * For /api/clubs/{slug}/** : the student-facing side of a club, used by logged-in users who are
 * NOT (yet) members, e.g. to browse open recruitment drives and apply.
 *
 * Binds only the club's schema (TenantContext), never a CurrentMember, so none of the
 * member-only endpoints under /api/club/** become reachable this way. Which club is chosen
 * by the URL is safe here because everything on this path is meant for outsiders anyway;
 * each endpoint still scopes data to the caller (e.g. "my applications").
 * Unknown and suspended clubs get the same 404.
 */
@Component
public class ClubBySlugFilter extends OncePerRequestFilter {

    public static final String PUBLIC_CLUB_PREFIX = "/api/clubs/";

    private final TenantRepository tenantRepository;
    private final RateLimitGuard rateLimits;

    public ClubBySlugFilter(TenantRepository tenantRepository, RateLimitGuard rateLimits) {
        this.tenantRepository = tenantRepository;
        this.rateLimits = rateLimits;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PUBLIC_CLUB_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String rest = request.getRequestURI().substring(PUBLIC_CLUB_PREFIX.length());
        int slash = rest.indexOf('/');
        String slug = slash < 0 ? rest : rest.substring(0, slash);

        // validate the format first: garbage in the URL never reaches the database
        Optional<Tenant> tenant = Tenant.SLUG_PATTERN.matcher(slug).matches()
                ? tenantRepository.findBySlug(slug).filter(Tenant::isActive)
                : Optional.empty();
        if (tenant.isEmpty()) {
            TenantFilter.writeProblem(response, HttpStatus.NOT_FOUND, "Club not found");
            return;
        }
        if (!rateLimits.allowClubRequest(tenant.get(), response)) {
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
}
