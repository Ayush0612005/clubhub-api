package com.clubhub.support;

import com.clubhub.security.JwtTokenService;
import com.clubhub.user.User;
import com.clubhub.user.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Test helpers that authenticate MockMvc requests as a real (persisted) user with a JWT principal,
 * without going through /login. The full login/refresh/switch-club flow is tested separately
 * with real signed tokens (SecurityRulesTest, RefreshFlowTest, SwitchClubTest).
 */
public final class TestAuth {

    private TestAuth() {
    }

    public static UUID newUserId(UserRepository users) {
        return users.save(new User("test." + UUID.randomUUID() + "@srmist.edu.in", "{noop}unused", "Test User")).getId();
    }

    /** Authenticated user with no active club. */
    public static RequestPostProcessor asUser(UUID userId) {
        return jwt().jwt(j -> j.subject(userId.toString())
                        .claim(JwtTokenService.CLAIM_ROLES, List.of("USER")))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /** Authenticated PLATFORM_ADMIN with no active club. */
    public static RequestPostProcessor asPlatformAdmin(UUID userId) {
        return jwt().jwt(j -> j.subject(userId.toString())
                        .claim(JwtTokenService.CLAIM_ROLES, List.of("PLATFORM_ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
    }

    /** Authenticated user whose token is scoped to one club. Membership is still checked server-side. */
    public static RequestPostProcessor inClub(UUID userId, UUID tenantId, String clubSlug) {
        return jwt().jwt(j -> j.subject(userId.toString())
                        .claim(JwtTokenService.CLAIM_ROLES, List.of("USER"))
                        .claim(JwtTokenService.CLAIM_TENANT_ID, tenantId.toString())
                        .claim(JwtTokenService.CLAIM_CLUB, clubSlug))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
