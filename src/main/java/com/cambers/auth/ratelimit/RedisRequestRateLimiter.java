package com.cambers.auth.ratelimit;

import com.cambers.auth.config.properties.RateLimitProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(prefix = "security.rate-limit", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisRequestRateLimiter implements RequestRateLimiter {

    private static final String KEY_PREFIX = "auth:rate-limit:v1:";

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
            Object rawResult = redisTemplate.execute((RedisCallback<Object>) connection -> connection.execute(
                    "INCREX",
                    bytes(key),
                    bytes("BYINT"),
                    bytes("1"),
                    bytes("UBOUND"),
                    bytes(Integer.toString(policy.limit())),
                    bytes("PX"),
                    bytes(Long.toString(windowMillis)),
                    bytes("ENX")
            ));

            List<?> result = requireTwoElementResult(rawResult);
            long actualIncrement = asLong(result.get(1));
            if (actualIncrement > 0) {
                return RateLimitDecision.allow();
            }

            Long ttlMillis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
            long effectiveTtl = ttlMillis == null || ttlMillis <= 0 ? windowMillis : ttlMillis;
            return RateLimitDecision.reject((effectiveTtl + 999) / 1000);
        } catch (RuntimeException exception) {
            throw new RateLimitBackendUnavailableException(exception);
        }
    }

    private List<?> requireTwoElementResult(Object rawResult) {
        if (rawResult instanceof List<?> result && result.size() == 2) {
            return result;
        }
        throw new IllegalStateException("INCREX returned an unexpected response");
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof byte[] bytes) {
            return Long.parseLong(new String(bytes, StandardCharsets.UTF_8));
        }
        if (value instanceof String string) {
            return Long.parseLong(string);
        }
        throw new IllegalStateException("INCREX returned a non-numeric value");
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
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
