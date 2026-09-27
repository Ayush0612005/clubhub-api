package com.clubhub.ratelimit;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

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

    /** After a failed connect, don't retry (and delay requests) more often than this. */
    private static final Duration RECONNECT_BACKOFF = Duration.ofSeconds(30);

    private final RedisClient client;
    private volatile ProxyManager<byte[]> buckets; // null until Redis has been reached once
    private volatile Instant nextConnectAttempt = Instant.EPOCH;

    public RateLimiter(DataRedisConnectionDetails redis,
                       @Value("${spring.data.redis.ssl.enabled:false}") boolean sslEnabled,
                       @Value("${spring.data.redis.url:}") String url) {
        DataRedisConnectionDetails.Standalone standalone = redis.getStandalone();
        RedisURI.Builder uri = RedisURI.builder()
                .withHost(standalone.getHost())
                .withPort(standalone.getPort())
                .withDatabase(standalone.getDatabase())
                // hosted Redis (e.g. Upstash) is TLS-only: ssl.enabled=true or a rediss:// URL
                .withSsl(sslEnabled || url.startsWith("rediss://"))
                // also bounds connection initialization (TLS + AUTH), which is slow on a cold, small JVM
                .withTimeout(Duration.ofSeconds(5));
        if (StringUtils.hasText(redis.getPassword())) {
            if (StringUtils.hasText(redis.getUsername())) {
                uri.withAuthentication(redis.getUsername(), redis.getPassword()); // Redis 6+ ACL user
            } else {
                uri.withPassword(redis.getPassword().toCharArray());
            }
        }
        this.client = RedisClient.create(uri.build());
        // TCP connect to a hosted Redis over the internet: allow more than the default
        client.setOptions(ClientOptions.builder()
                .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofSeconds(10)).build())
                .build());
        // No connection here: Redis being slow or down at startup must not stop the API from booting.
    }

    /**
     * Connects on first use. Bucket4j opens the Redis connection when the proxy manager is built, so a
     * failure here means "Redis unreachable": return null (caller fails open) and retry after a backoff.
     */
    private ProxyManager<byte[]> buckets() {
        ProxyManager<byte[]> current = buckets;
        if (current != null || Instant.now().isBefore(nextConnectAttempt)) {
            return current;
        }
        synchronized (this) {
            if (buckets == null && !Instant.now().isBefore(nextConnectAttempt)) {
                try {
                    buckets = Bucket4jLettuce.casBasedBuilder(client)
                            // a full bucket is the same as no bucket: let Redis drop idle keys after one refill period
                            .expirationAfterWrite(ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
                            .build();
                } catch (RuntimeException e) {
                    nextConnectAttempt = Instant.now().plus(RECONNECT_BACKOFF);
                    log.warn("Rate limiter cannot reach Redis, allowing requests (retry in {}s): {}",
                            RECONNECT_BACKOFF.toSeconds(), e.getMessage());
                }
            }
            return buckets;
        }
    }

    /** Takes one token from the bucket {@code key}, which holds {@code perMinute} tokens refilled per minute. */
    public Decision tryConsume(String key, int perMinute) {
        try {
            BucketConfiguration config = BucketConfiguration.builder()
                    .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                    .build();
            ProxyManager<byte[]> manager = buckets();
            if (manager == null) {
                return Decision.allowAll();
            }
            ConsumptionProbe probe = manager.builder()
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
