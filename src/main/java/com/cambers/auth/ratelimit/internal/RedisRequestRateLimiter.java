package com.cambers.auth.ratelimit.internal;

import com.cambers.auth.ratelimit.RateLimitBackendUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "security.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
class RedisRequestRateLimiter implements RequestRateLimiter {

    // v2 intentionally separates sorted-set sliding-window keys from the previous string-counter format.
    private static final String KEY_PREFIX = "auth:rate-limit:v2:";

    /**
     * Sliding-window limiter executed atomically by Redis.
     *
     * A positive result means the request is allowed. A negative result encodes
     * the number of milliseconds until the oldest counted request leaves the window.
     */
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>("""
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local member = ARGV[3]

            local redis_time = redis.call('TIME')
            local now = (tonumber(redis_time[1]) * 1000) + math.floor(tonumber(redis_time[2]) / 1000)
            local cutoff = now - window

            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', cutoff)
            local current = redis.call('ZCARD', KEYS[1])

            if current >= limit then
                local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
                local retry_after = window
                if oldest[2] ~= nil then
                    retry_after = math.max(window - (now - tonumber(oldest[2])), 1)
                end
                redis.call('PEXPIRE', KEYS[1], window)
                return -retry_after
            end

            redis.call('ZADD', KEYS[1], now, member)
            redis.call('PEXPIRE', KEYS[1], window)
            return current + 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    RedisRequestRateLimiter(StringRedisTemplate redisTemplate) {
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
                    Long.toString(windowMillis),
                    UUID.randomUUID().toString()
            );
            if (result == null) {
                throw new IllegalStateException("Redis rate-limit script returned no result");
            }
            if (result > 0) {
                return RateLimitDecision.allow();
            }

            long retryAfterMillis = Math.max(-result, 1);
            return RateLimitDecision.reject((retryAfterMillis + 999) / 1000);
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
