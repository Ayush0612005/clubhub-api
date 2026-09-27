package com.clubhub.ratelimit;

import com.clubhub.ratelimit.RateLimiter.Decision;
import com.clubhub.tenant.Tenant;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * The two limits the API enforces:
 * - per club: every request to /api/club/** and /api/clubs/{slug}/** counts against that club's
 *   bucket, sized by its plan (one busy club can't starve the others: "noisy neighbour" protection)
 * - per client IP on the unauthenticated auth endpoints: slows down password guessing
 */
@Component
public class RateLimitGuard {

    private final RateLimiter limiter;
    private final int authPerMinute;
    private final int clubPerMinuteOverride;

    public RateLimitGuard(RateLimiter limiter,
                          @Value("${clubhub.rate-limits.auth-per-minute:20}") int authPerMinute,
                          @Value("${clubhub.rate-limits.club-per-minute-override:0}") int clubPerMinuteOverride) {
        this.limiter = limiter;
        this.authPerMinute = authPerMinute;
        this.clubPerMinuteOverride = clubPerMinuteOverride;
    }

    /** @return false if the request was rejected (a 429 has been written). */
    public boolean allowClubRequest(Tenant club, HttpServletResponse response) throws IOException {
        int perMinute = clubPerMinuteOverride > 0 ? clubPerMinuteOverride : club.getPlan().requestsPerMinute();
        return check(limiter.tryConsume("rl:club:" + club.getId(), perMinute), response,
                "This club has reached its request limit, try again shortly");
    }

    public boolean allowAuthRequest(String clientIp, HttpServletResponse response) throws IOException {
        return check(limiter.tryConsume("rl:auth:" + clientIp, authPerMinute), response,
                "Too many attempts, try again shortly");
    }

    private static boolean check(Decision decision, HttpServletResponse response, String detail) throws IOException {
        if (decision.remaining() >= 0) {
            response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
        }
        if (decision.allowed()) {
            return true;
        }
        response.setStatus(429);
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        response.setContentType("application/problem+json");
        response.getWriter().write("""
                {"type":"about:blank","title":"Too Many Requests","status":429,"detail":"%s"}""".formatted(detail));
        return false;
    }
}
