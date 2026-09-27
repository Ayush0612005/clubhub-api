package com.clubhub.ratelimit;

import com.clubhub.TestcontainersConfiguration;
import com.clubhub.tenant.TenantProvisioningService;
import com.clubhub.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static com.clubhub.support.TestAuth.inClub;
import static com.clubhub.support.TestAuth.newUserId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Small limits for this context only, against a real Redis container. */
@SpringBootTest(properties = {
        "clubhub.rate-limits.auth-per-minute=3",
        "clubhub.rate-limits.club-per-minute-override=5"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RateLimitTest {

    @Autowired MockMvc mvc;
    @Autowired RateLimiter limiter;
    @Autowired TenantProvisioningService provisioningService;
    @Autowired UserRepository users;

    @Test
    void bucketRefusesOnceEmpty() {
        String key = "test:" + UUID.randomUUID();
        assertThat(limiter.tryConsume(key, 2).allowed()).isTrue();
        assertThat(limiter.tryConsume(key, 2).allowed()).isTrue();
        RateLimiter.Decision third = limiter.tryConsume(key, 2);
        assertThat(third.allowed()).isFalse();
        assertThat(third.retryAfterSeconds()).isPositive();
    }

    @Test
    void loginAttemptsAreLimitedPerIp() throws Exception {
        String body = """
                {"email":"nobody@srmist.edu.in","password":"wrong-password-123"}""";
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("10.9.8.7"); return r; })
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("10.9.8.7"); return r; })
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
        // another client is unaffected
        mvc.perform(post("/api/auth/login").with(r -> { r.setRemoteAddr("10.9.8.8"); return r; })
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void eachClubHasItsOwnRequestBudget() throws Exception {
        UUID adminId = newUserId(users);
        UUID busy = provisioningService.provision("busy_club", "Busy Club", adminId).getId();
        UUID quiet = provisioningService.provision("quiet_club", "Quiet Club", adminId).getId();
        RequestPostProcessor inBusy = inClub(adminId, busy, "busy_club");

        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/club/profile").with(inBusy)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/club/profile").with(inBusy))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Remaining", "0"));
        // a noisy club doesn't use up another club's budget
        mvc.perform(get("/api/club/profile").with(inClub(adminId, quiet, "quiet_club"))).andExpect(status().isOk());
    }
}
