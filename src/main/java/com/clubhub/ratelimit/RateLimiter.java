package com.clubhub.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Token buckets stored in Redis (Bucket4j, compare-and-swap), so every API instance shares the same
 * counters: a client can't get N times the limit by hitting N servers.
 *
 * Fails OPEN: if Redis is unreachable the request is allowed and a warning is logged. Rate limiting
 * protects the system; an outage of the limiter must not become an outage of the whole API.
 */
@Component
public class RateLimiter implements DisposableBean {

    public record Decision(boolean allowed, long remaining, long retryAfterSeconds) {

        static Decision allowAll() {
            return new Decision(true, -1, 0);
        }
    }

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);

    private final RedisClient client;
    private final ProxyManager<byte[]> buckets;

    public RateLimiter(DataRedisConnectionDetails redis) {
        DataRedisConnectionDetails.Standalone standalone = redis.getStandalone();
        RedisURI.Builder uri = RedisURI.builder()
                .withHost(standalone.getHost())
                .withPort(standalone.getPort())
                .withDatabase(standalone.getDatabase())
                .withTimeout(Duration.ofSeconds(2));
        if (redis.getPassword() != null) {
            uri.withPassword(redis.getPassword().toCharArray());
        }
        this.client = RedisClient.create(uri.build());
        this.buckets = Bucket4jLettuce.casBasedBuilder(client)
                // a full bucket is the same as no bucket: let Redis drop idle keys after one refill period
                .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
                .build();
    }

    /** Takes one token from the bucket {@code key}, which holds {@code perMinute} tokens refilled per minute. */
    public Decision tryConsume(String key, int perMinute) {
        try {
            BucketConfiguration config = BucketConfiguration.builder()
                    .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                    .build();
            ConsumptionProbe probe = buckets.builder()
                    .build(key.getBytes(StandardCharsets.UTF_8), () -> config)
                    .tryConsumeAndReturnRemaining(1);
            return new Decision(probe.isConsumed(), probe.getRemainingTokens(),
                    Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds()));
        } catch (RuntimeException e) {
            log.warn("Rate limiter unavailable, allowing request: {}", e.getMessage());
            return Decision.allowAll();
        }
    }

    @Override
    public void destroy() {
        client.shutdown();
    }
}
