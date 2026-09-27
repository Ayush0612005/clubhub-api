package com.clubhub.tenancy;

import com.clubhub.membership.Membership;
import com.clubhub.membership.MembershipRepository;
import com.clubhub.ratelimit.RateLimitGuard;
import com.clubhub.security.JwtTokenService;
import com.clubhub.tenant.Tenant;
import com.clubhub.tenant.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * For every /api/club/** request: take the club from the VERIFIED access token (tid claim),
 * re-check that the caller is still a member of that (active) club, then run the rest of the
 * request inside that club's TenantContext.
 *
 * Runs after the Spring Security filter chain (security is registered at order -100, this bean
 * filter at the default lowest precedence), so authentication has already happened.
 *
 * The membership is re-checked on every request instead of trusting the token alone: removing a
 * member takes effect immediately rather than after their access token expires. It costs one
 * indexed lookup per request (primary-key and unique-index hits), which is cheap next to the
 * request itself. Each club request also counts against the club's rate limit (Redis).
 */
@Component
public class TenantFilter extends OncePerRequestFilter {

    public static final String CLUB_API_PREFIX = "/api/club/";

    private final TenantRepository tenantRepository;
    private final MembershipRepository membershipRepository;
    private final RateLimitGuard rateLimits;

    public TenantFilter(TenantRepository tenantRepository, MembershipRepository membershipRepository,
                        RateLimitGuard rateLimits) {
        this.tenantRepository = tenantRepository;
        this.membershipRepository = membershipRepository;
        this.rateLimits = rateLimits;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(CLUB_API_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            writeProblem(response, HttpStatus.UNAUTHORIZED, "Authentication required");
            return;
        }

        Optional<UUID> tenantId = parseUuid(jwtAuth.getToken().getClaimAsString(JwtTokenService.CLAIM_TENANT_ID));
        if (tenantId.isEmpty()) {
            writeProblem(response, HttpStatus.FORBIDDEN, "No active club: call POST /api/auth/switch-club first");
            return;
        }

        Optional<Tenant> tenant = tenantRepository.findById(tenantId.get());
        Optional<UUID> userId = parseUuid(jwtAuth.getToken().getSubject());
        Optional<Membership> membership = tenant.isPresent() && userId.isPresent()
                ? membershipRepository.findByUserIdAndTenantId(userId.get(), tenantId.get())
                : Optional.empty();

        if (membership.isEmpty()) {
            // same answer for unknown club and non-member: nothing to learn by probing ids
            writeProblem(response, HttpStatus.FORBIDDEN, "You are not a member of this club");
            return;
        }
        if (!tenant.get().isActive()) {
            writeProblem(response, HttpStatus.FORBIDDEN, "Club is suspended");
            return;
        }
        if (!rateLimits.allowClubRequest(tenant.get(), response)) {
            return;
        }

        CurrentMember.ClubMember member = new CurrentMember.ClubMember(
                userId.get(), tenantId.get(), membership.get().getRole());
        try {
            TenantContext.callAs(tenant.get().getSchemaName(), () ->
                    CurrentMember.callAs(member, () -> {
                        chain.doFilter(request, response);
                        return null;
                    }));
        } catch (IOException | ServletException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    private static Optional<UUID> parseUuid(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    static void writeProblem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        // detail is always a fixed server-side string, never echoed client input
        response.getWriter().write("""
                {"type":"about:blank","title":"%s","status":%d,"detail":"%s"}"""
                .formatted(status.getReasonPhrase(), status.value(), detail));
    }
}
