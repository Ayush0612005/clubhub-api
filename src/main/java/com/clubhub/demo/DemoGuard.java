package com.clubhub.demo;

import com.clubhub.security.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Set;

/**
 * Keeps the shared demo account inside its sandbox. Anyone on the internet can be the demo visitor,
 * so it may change anything in the demo club (which resets) but nothing that real students see:
 * no applying to or registering with real clubs, no campus suggestions.
 *
 * Reads are never blocked. Club-scoped writes ({@code /api/club/**}) are allowed because the
 * visitor's only membership is the demo club, so its club token can only point there.
 */
@Configuration(proxyBeanMethods = false)
public class DemoGuard implements HandlerInterceptor, WebMvcConfigurer {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    /** Thrown when the demo visitor tries to write outside the sandbox; maps to 403. */
    public static class OutsideSandboxException extends RuntimeException {
        public OutsideSandboxException() {
            super("The demo account can only change things inside the demo club. Create a free account to do this for real.");
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (READ_METHODS.contains(request.getMethod()) || !isDemoVisitor()) {
            return true;
        }
        if (!isInsideSandbox(request.getRequestURI())) {
            throw new OutsideSandboxException();
        }
        return true;
    }

    static boolean isInsideSandbox(String path) {
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/notifications")
                || path.startsWith("/api/club/")
                || path.startsWith("/api/clubs/" + DemoAccounts.CLUB_SLUG + "/");
    }

    private static boolean isDemoVisitor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken jwt
                && DemoAccounts.VISITOR_EMAIL.equals(jwt.getToken().getClaimAsString(JwtTokenService.CLAIM_EMAIL));
    }
}
