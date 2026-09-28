package com.clubhub.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/** Per-IP limit on login/register/refresh: the endpoints anyone can call without a token. */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    // resend/forgot send real emails: without a limit they'd be a free spam cannon and burn the mail quota
    private static final Set<String> LIMITED = Set.of("/api/auth/login", "/api/auth/register", "/api/auth/refresh",
            "/api/auth/verify-email", "/api/auth/resend-verification", "/api/auth/forgot-password",
            "/api/auth/reset-password");

    private final RateLimitGuard guard;

    public AuthRateLimitFilter(RateLimitGuard guard) {
        this.guard = guard;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // behind a reverse proxy, enable server.forward-headers-strategy so this is the real client IP
        if (guard.allowAuthRequest(request.getRemoteAddr(), response)) {
            chain.doFilter(request, response);
        }
    }
}
