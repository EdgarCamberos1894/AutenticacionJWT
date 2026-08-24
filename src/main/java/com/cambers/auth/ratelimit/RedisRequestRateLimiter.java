package com.cambers.auth.ratelimit;

import com.cambers.auth.config.properties.RateLimitProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "security.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisRequestRateLimiter implements RequestRateLimiter {

    private static final String KEY_PREFIX = "auth:rate-limit:v1:";

    /**
     * Fixed-window limiter executed atomically by Redis.
     *
     * A positive result means the request is allowed. A negative result encodes
     * the remaining window TTL in milliseconds for a rejected request.
     */
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local current = tonumber(redis.call('GET', KEYS[1]) or '0')

            if current >= limit then
                local ttl = redis.call('PTTL', KEYS[1])
                if ttl <= 0 then
                    ttl = window
                    redis.call('PEXPIRE', KEYS[1], window)
                end
                return -math.max(ttl, 1)
            end

            local next = redis.call('INCR', KEYS[1])
            if next == 1 then
                redis.call('PEXPIRE', KEYS[1], window)
            end
            return next
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRequestRateLimiter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitDecision consume(
            String policyName,
            String clientIdentifier,
            RateLimitProperties.Policy policy) {

        String key = KEY_PREFIX + policyName + ":" + sha256(clientIdentifier);
        long windowMillis = policy.window().toMillis();

        try {
            Long result = redisTemplate.execute(
                    CONSUME_SCRIPT,
                    List.of(key),
                    Integer.toString(policy.limit()),
                    Long.toString(windowMillis)
            );
            if (result == null) {
                throw new IllegalStateException("Redis rate-limit script returned no result");
            }
            if (result > 0) {
                return RateLimitDecision.allow();
            }

            long ttlMillis = Math.max(-result, 1);
            return RateLimitDecision.reject((ttlMillis + 999) / 1000);
        } catch (RuntimeException exception) {
            throw new RateLimitBackendUnavailableException(exception);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
