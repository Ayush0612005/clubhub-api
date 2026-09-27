package com.clubhub.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Regression: on Render the first TLS connect to Upstash timed out and the constructor killed startup. */
class RateLimiterUnavailableTest {

    /** Nothing listens on port 1: every connection attempt is refused. */
    static DataRedisConnectionDetails unreachableRedis() {
        return new DataRedisConnectionDetails() {
            @Override
            public Standalone getStandalone() {
                return Standalone.of("127.0.0.1", 1);
            }
        };
    }

    @Test
    void constructionNeverTouchesRedisAndRequestsFailOpen() {
        RateLimiter limiter = new RateLimiter(unreachableRedis(), false, "");
        try {
            RateLimiter.Decision first = limiter.tryConsume("ip:1.2.3.4", 5);
            assertThat(first.allowed()).isTrue();

            // within the backoff window no reconnect is attempted, so requests aren't slowed down
            Instant start = Instant.now();
            for (int i = 0; i < 20; i++) {
                assertThat(limiter.tryConsume("ip:1.2.3.4", 5).allowed()).isTrue();
            }
            assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(1));
        } finally {
            limiter.destroy();
        }
    }
}
