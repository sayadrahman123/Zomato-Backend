package com.zomato.backend.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.UUID;

/**
 * Redis-backed Sliding Window Rate Limiter.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * Algorithm: Sliding Window with Redis Sorted Set (ZSET)
 *
 * Key:   ratelimit:{group}:{identifier}
 *          group      = AUTH | PUBLIC | USER | ADMIN
 *          identifier = client IP (unauthenticated) or userId (authenticated)
 *
 * Structure (per key):
 *   ZSET where each member = unique UUID, score = timestamp (epoch ms)
 *
 * Per request:
 *   1. ZREMRANGEBYSCORE  → remove entries older than (now - windowMs)
 *   2. ZCARD             → count remaining (= requests in current window)
 *   3. If count < limit  → ZADD current request, PEXPIRE key, return (1, remaining)
 *   4. If count >= limit → return (0, 0) — rejected
 *
 * Why ZSET instead of a simple counter (INCR)?
 *   A simple counter (fixed window) allows 2× burst at window boundaries:
 *     Window 1: 0:59 sends 100 requests → window resets at 1:00
 *     Window 2: 1:01 sends 100 requests → 200 requests in 2 seconds ❌
 *   The sliding window recounts only requests in the last N ms, so
 *   the burst is impossible regardless of when the window boundary falls.
 *
 * Why Lua?
 *   ZREMRANGEBYSCORE + ZCARD + ZADD must be atomic. Without Lua:
 *   - Thread A reads count=99 (under limit)
 *   - Thread B reads count=99 (under limit)
 *   - Both add → count=101 (limit exceeded) ❌
 *   The Lua script runs as a single atomic operation inside Redis.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private static final String KEY_PREFIX = "ratelimit:";

    /**
     * Lua script: atomic sliding window check-and-increment.
     *
     * KEYS[1] = the rate limit key
     * ARGV[1] = now (epoch ms, as string)
     * ARGV[2] = windowMs (sliding window size in ms)
     * ARGV[3] = limit (max requests per window)
     * ARGV[4] = requestId (unique member for the ZSET)
     *
     * Returns: {allowed (0|1), remaining}
     *   [1, remaining] → request allowed
     *   [0, 0]         → request rejected (rate limit exceeded)
     */
    private static final DefaultRedisScript<java.util.List> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText(
            "local key = KEYS[1] " +
            "local now = tonumber(ARGV[1]) " +
            "local window = tonumber(ARGV[2]) " +
            "local limit = tonumber(ARGV[3]) " +
            "local reqId = ARGV[4] " +

            // Remove entries outside the sliding window
            "redis.call('ZREMRANGEBYSCORE', key, '-inf', now - window) " +

            // Count requests in the current window
            "local count = redis.call('ZCARD', key) " +

            "if count < limit then " +
            // Allowed: record this request
            "    redis.call('ZADD', key, now, reqId) " +
            // Expire the key after one window (auto-cleanup)
            "    redis.call('PEXPIRE', key, window) " +
            "    return {1, limit - count - 1} " +   // {allowed=1, remaining}
            "else " +
            "    return {0, 0} " +                    // {allowed=0, remaining=0}
            "end"
        );
        RATE_LIMIT_SCRIPT.setResultType(java.util.List.class);
    }

    private final RedisTemplate<String, Object> redisTemplate;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Checks whether the given identifier is within the rate limit.
     *
     * @param group      endpoint group (e.g. "AUTH", "PUBLIC", "USER")
     * @param identifier client IP or userId string
     * @param limit      maximum requests allowed per window
     * @param windowMs   sliding window size in milliseconds
     * @return RateLimitResult containing allowed flag and remaining count
     */
    public RateLimitResult check(String group, String identifier, int limit, long windowMs) {
        String key = KEY_PREFIX + group + ":" + identifier;
        long now = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();

        try {
            @SuppressWarnings("unchecked")
            java.util.List<?> result = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    Collections.singletonList(key),
                    String.valueOf(now),
                    String.valueOf(windowMs),
                    String.valueOf(limit),
                    requestId
            );

            if (result == null || result.size() < 2) {
                // Redis error — fail open (allow request) to avoid blocking all traffic
                log.error("Rate limiter Lua script returned null for key={}", key);
                return RateLimitResult.allowed(limit - 1);
            }

            boolean allowed   = ((Number) result.get(0)).longValue() == 1L;
            long    remaining = ((Number) result.get(1)).longValue();

            if (!allowed) {
                log.warn("Rate limit EXCEEDED: group={}, identifier={}, limit={}/{}ms",
                        group, identifier, limit, windowMs);
            }

            return allowed
                    ? RateLimitResult.allowed(remaining)
                    : RateLimitResult.rejected();

        } catch (Exception e) {
            // Redis down — fail open: don't block legitimate traffic
            log.error("Rate limiter Redis error: key={}, error={}", key, e.getMessage());
            return RateLimitResult.allowed(limit - 1);
        }
    }

    // ── Value Object ──────────────────────────────────────────────────────────

    /**
     * Result of a rate limit check.
     *
     * @param allowed   true if the request should proceed
     * @param remaining requests remaining in the current window (0 if rejected)
     */
    public record RateLimitResult(boolean allowed, long remaining) {
        static RateLimitResult allowed(long remaining) { return new RateLimitResult(true, Math.max(0, remaining)); }
        static RateLimitResult rejected()              { return new RateLimitResult(false, 0); }
    }
}
