package com.zomato.backend.service;

import com.zomato.backend.dto.response.UserResponse;
import com.zomato.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redis caching for user profile lookups.
 *
 * Patterns applied (deliberately lighter than RestaurantCacheService):
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ STAMPEDE — intentionally NOT implemented                                │
 * │   User profile keys are per-user (user:profile:{userId}). Even if a    │
 * │   key expires, only requests from that one user will miss concurrently. │
 * │   A single user cannot produce a thundering herd — the load is bounded │
 * │   by their request rate, not shared across thousands of callers.        │
 * │   Adding a distributed lock here would be over-engineering.             │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ PENETRATION ✅                                                           │
 * │   An attacker can enumerate user IDs (GET /profile via admin endpoint)  │
 * │   or a bug can repeatedly query deleted user IDs. Without protection,   │
 * │   every miss falls through to the DB indefinitely.                      │
 * │   Fix: cache NULL_SENTINEL for missing users (TTL: 60s).               │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ AVALANCHE ✅                                                             │
 * │   When the app restarts and re-warms profile caches during peak login   │
 * │   time, all profiles expire at the same moment → DB spike.             │
 * │   Fix: jitter per key (base 5min ± 0–60s random offset).               │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ SAFE INVALIDATION ✅                                                     │
 * │   On updateProfile() or changePassword() evict the key explicitly.     │
 * │   No Lua script needed here — plain DEL is safe for user profiles       │
 * │   because only the profile owner mutates their own key (no lock-theft  │
 * │   race — there's no lock to steal).                                     │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * Key naming:
 *   user:profile:{userId}   → UserResponse | NULL_SENTINEL (TTL: ~5min)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileCacheService {

    // ── Key prefixes ──────────────────────────────────────────────────────────

    private static final String CACHE_PREFIX = "user:profile:";

    // ── TTL configuration ─────────────────────────────────────────────────────

    /**
     * Base TTL for a cached profile.
     * 5 minutes balances freshness vs. DB load.
     * Profile data is rarely stale for more than a few seconds after an update
     * (we evict immediately on writes), so a longer TTL here is safe.
     */
    private static final long CACHE_TTL_SECONDS      = 300L;   // 5 minutes

    /**
     * Jitter ceiling: 0 to 60 extra seconds per key.
     * Effective TTL range: 5:00 – 6:00 per user profile.
     */
    private static final int  JITTER_MAX_SECONDS     = 60;

    /**
     * TTL for NULL_SENTINEL entries.
     * Short (60s) so that a legitimately created user account starts
     * appearing within 1 minute if a sentinel was cached for that ID.
     */
    private static final long PENETRATION_TTL_SECONDS = 60L;

    /**
     * Stored when a user ID is not found in the DB.
     * Prevents repeated DB hits for invalid / deleted user IDs.
     */
    private static final String NULL_SENTINEL = "__USER_NOT_FOUND__";

    private static final Random JITTER_RANDOM = new Random();

    private final RedisTemplate<String, Object> redisTemplate;

    // ══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the cached profile, or loads it from the DB via the supplier.
     *
     * Flow:
     *   1. Cache hit (UserResponse)   → return immediately.
     *   2. Cache hit (NULL_SENTINEL)  → throw ResourceNotFoundException (no DB query).
     *   3. Cache miss                 → call loader.
     *      Loader returns empty       → cache NULL_SENTINEL (60s) → throw 404.
     *      Loader returns data        → cache with jittered TTL → return.
     *
     * @param userId the user to load
     * @param loader DB query returning Optional<UserResponse>
     * @return the UserResponse, from cache or DB
     * @throws ResourceNotFoundException if the user does not exist
     */
    public UserResponse getOrLoad(Long userId, Supplier<Optional<UserResponse>> loader) {

        // ── Step 1: Cache read ────────────────────────────────────────────────
        CacheResult result = readFromCache(userId);

        if (result.isSentinel()) {
            log.debug("Penetration guard hit: userId={}", userId);
            throwNotFound(userId);
        }
        if (result.hasValue()) {
            log.debug("Profile cache HIT: userId={}", userId);
            return result.value();
        }

        // ── Step 2: Cache miss — load from DB ────────────────────────────────
        log.debug("Profile cache MISS — loading from DB: userId={}", userId);
        Optional<UserResponse> loaded = loader.get();

        if (loaded.isEmpty()) {
            // User not found — cache sentinel to stop penetration.
            writeSentinel(userId);
            throwNotFound(userId);
        }

        UserResponse response = loaded.get();
        writeToCache(userId, response);
        return response;
    }

    /**
     * Evicts the user's profile cache entry.
     * <p>
     * Call this after any write that changes profile data:
     *   - updateProfile()
     *   - changePassword()
     *   - admin ban/unban/role-change
     * <p>
     * Plain DEL is safe here — no lock to steal because user profile keys
     * are not involved in any distributed lock protocol.
     *
     * @param userId the user whose profile should be invalidated
     */
    public void evict(Long userId) {
        try {
            Boolean deleted = redisTemplate.delete(cacheKey(userId));
            if (Boolean.TRUE.equals(deleted)) {
                log.info("Profile cache evicted: userId={}", userId);
            } else {
                log.debug("Profile cache evict (key not present): userId={}", userId);
            }
        } catch (Exception e) {
            log.error("Profile cache evict failed: userId={}, error={}", userId, e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INTERNAL: CACHE READ / WRITE
    // ══════════════════════════════════════════════════════════════════════════

    private CacheResult readFromCache(Long userId) {
        try {
            Object raw = redisTemplate.opsForValue().get(cacheKey(userId));
            if (raw == null)                      return CacheResult.miss();
            if (NULL_SENTINEL.equals(raw))        return CacheResult.sentinel();
            if (raw instanceof UserResponse resp) return CacheResult.hit(resp);
        } catch (Exception e) {
            // Redis down or deserialization error — treat as miss, never crash.
            log.error("Profile cache GET failed: userId={}, error={}", userId, e.getMessage());
        }
        return CacheResult.miss();
    }

    private void writeToCache(Long userId, UserResponse response) {
        try {
            long ttl = CACHE_TTL_SECONDS + JITTER_RANDOM.nextInt(JITTER_MAX_SECONDS);
            redisTemplate.opsForValue().set(cacheKey(userId), response, ttl, TimeUnit.SECONDS);
            log.debug("Profile cached: userId={}, TTL={}s", userId, ttl);
        } catch (Exception e) {
            // Redis down — log and continue. Result still returned from DB.
            log.error("Profile cache PUT failed: userId={}, error={}", userId, e.getMessage());
        }
    }

    private void writeSentinel(Long userId) {
        try {
            redisTemplate.opsForValue().set(
                    cacheKey(userId),
                    NULL_SENTINEL,
                    PENETRATION_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            log.debug("Penetration sentinel cached: userId={}, TTL={}s", userId, PENETRATION_TTL_SECONDS);
        } catch (Exception e) {
            log.error("Sentinel PUT failed: userId={}, error={}", userId, e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private String cacheKey(Long userId) {
        return CACHE_PREFIX + userId;
    }

    private void throwNotFound(Long userId) {
        throw new ResourceNotFoundException("User", "id", userId);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VALUE OBJECT: CacheResult (discriminated union)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Discriminated union: MISS | SENTINEL | HIT.
     * Eliminates null ambiguity between "not cached" and "cached as not found".
     */
    private sealed interface CacheResult
            permits CacheResult.Miss, CacheResult.Sentinel, CacheResult.Hit {

        static CacheResult miss()                    { return new Miss();     }
        static CacheResult sentinel()                { return new Sentinel(); }
        static CacheResult hit(UserResponse response){ return new Hit(response); }

        default boolean isSentinel() { return this instanceof Sentinel; }
        default boolean hasValue()   { return this instanceof Hit;      }
        default UserResponse value() { return ((Hit) this).response();  }

        record Miss()     implements CacheResult {}
        record Sentinel() implements CacheResult {}
        record Hit(UserResponse response) implements CacheResult {}
    }
}
