package com.zomato.backend.service;

import com.zomato.backend.dto.response.RestaurantResponse;
import com.zomato.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Industry-grade Redis caching for individual restaurant lookups.
 *
 * Solves all four classic cache problems:
 *
 * ┌─────────────────────────────────────────────────────────────────────────┐
 * │ 1. CACHE STAMPEDE (Thundering Herd)                                     │
 * │    Problem:  popular restaurant's key expires → 1,000 concurrent misses │
 * │             all query the DB simultaneously.                            │
 * │    Fix:     Redis distributed lock (SET NX EX) with UUID token.         │
 * │             Only the lock-holder queries DB. Others wait + re-check.    │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ 2. CACHE PENETRATION                                                    │
 * │    Problem:  requests for non-existent IDs (invalid / malicious)        │
 * │             bypass cache forever — every request hits the DB.           │
 * │    Fix:     cache a NULL_SENTINEL string for missing restaurants with   │
 * │             a short TTL (PENETRATION_TTL_SECONDS = 120s).               │
 * │             On read: sentinel → throw ResourceNotFoundException (no DB).│
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ 3. CACHE AVALANCHE                                                      │
 * │    Problem:  many keys share the same TTL → all expire at once          │
 * │             (e.g. after Redis restart or mass population) →             │
 * │             sudden DB flood at a single moment.                         │
 * │    Fix:     add random jitter to each key's TTL so expirations are      │
 * │             spread across a JITTER_MAX_SECONDS window.                  │
 * │             Effective TTL = CACHE_TTL_MINUTES ± 0..JITTER_MAX_SECONDS. │
 * ├─────────────────────────────────────────────────────────────────────────┤
 * │ 4. SAFE LOCK RELEASE (Lock Theft Race)                                  │
 * │    Problem:  Thread A's lock TTL expires while it holds the lock.       │
 * │             Thread B acquires the same lock key.                        │
 * │             Thread A finishes and calls DEL → deletes Thread B's lock.  │
 * │    Fix:     Each acquisition stores a unique UUID as the lock value.    │
 * │             Release uses a Lua script that atomically checks            │
 * │             "is this my token?" before deleting.                        │
 * └─────────────────────────────────────────────────────────────────────────┘
 *
 * Key naming:
 *   restaurant:{id}        → RestaurantResponse | NULL_SENTINEL (TTL: ~10min)
 *   restaurant:lock:{id}   → UUID token (TTL: 30s)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantCacheService {

    // ── Cache key prefixes ────────────────────────────────────────────────────

    private static final String CACHE_PREFIX = "restaurant:";
    private static final String LOCK_PREFIX  = "restaurant:lock:";

    // ── TTL configuration ─────────────────────────────────────────────────────

    /**
     * Base TTL for a cached restaurant.
     * With jitter applied: effective range is 10:00 – 12:00 minutes.
     */
    private static final long CACHE_TTL_MINUTES   = 10L;

    /**
     * Random jitter ceiling (in seconds) added to each key.
     * Spreads key expirations across a 2-minute window → prevents avalanche.
     */
    private static final int  JITTER_MAX_SECONDS  = 120;

    /**
     * TTL for NULL_SENTINEL entries (non-existent restaurant IDs).
     * Short so that a legitimately created restaurant appears within 2 minutes.
     * Long enough to absorb a penetration attack burst.
     */
    private static final long PENETRATION_TTL_SECONDS = 120L;

    /**
     * Sentinel value stored when a restaurant ID is not found in the DB.
     * Must not collide with valid JSON (prefixed with __ for safety).
     */
    private static final String NULL_SENTINEL       = "__RESTAURANT_NOT_FOUND__";

    // ── Lock configuration ────────────────────────────────────────────────────

    /** Max time a lock lives before Redis auto-expires it (deadlock prevention). */
    private static final long LOCK_TTL_SECONDS    = 30L;

    /** How many times to compete for the lock before falling back to DB. */
    private static final int  MAX_RETRY_ATTEMPTS   = 3;

    /** Wait time (ms) between failed lock-acquire attempts. */
    private static final long RETRY_WAIT_MS        = 100L;

    // ── Lua script: atomic compare-and-delete ─────────────────────────────────

    /**
     * Atomically releases the lock only if the stored token matches ours.
     *
     * if GET(KEYS[1]) == ARGV[1] then DEL(KEYS[1]); return 1
     * else return 0   -- lock expired or was taken by another thread
     */
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT;

    static {
        RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>();
        RELEASE_LOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "    return redis.call('del', KEYS[1]) " +
                "else " +
                "    return 0 " +
                "end"
        );
        RELEASE_LOCK_SCRIPT.setResultType(Long.class);
    }

    private static final Random JITTER_RANDOM = new Random();

    private final RedisTemplate<String, Object> redisTemplate;

    // ══════════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Cache-aside read with all four protections active.
     *
     * Flow:
     *   1. Cache hit (real data)      → return immediately.
     *   2. Cache hit (NULL_SENTINEL)  → throw ResourceNotFoundException (no DB).
     *   3. Cache miss                 → acquire lock → double-check → load DB.
     *      If loader returns empty    → write NULL_SENTINEL, throw 404.
     *      If loader returns data     → write to cache with jittered TTL, return.
     *   4. Lock contention            → wait + retry.
     *   5. Retries exhausted          → direct DB fallback (degraded mode).
     *
     * @param restaurantId   the ID to look up
     * @param loader         DB query — called at most once under normal conditions
     * @return the cached or freshly loaded RestaurantResponse
     * @throws ResourceNotFoundException if the restaurant does not exist
     */
    public RestaurantResponse getOrLoad(Long restaurantId, Supplier<Optional<RestaurantResponse>> loader) {

        // ── 1. Fast path: cache hit ───────────────────────────────────────────
        CacheResult hit = readFromCache(restaurantId);
        if (hit.isSentinel()) {
            log.debug("Cache PENETRATION guard hit: restaurantId={}", restaurantId);
            throwNotFound(restaurantId);
        }
        if (hit.hasValue()) {
            log.debug("Cache HIT: restaurantId={}", restaurantId);
            return hit.value();
        }

        // ── 2. Cache miss: compete for distributed lock ───────────────────────
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            Optional<String> tokenOpt = tryAcquireLock(restaurantId);

            if (tokenOpt.isPresent()) {
                String token = tokenOpt.get();
                try {
                    // Double-check: another thread may have populated cache
                    // between our miss and our lock acquisition.
                    CacheResult dcCheck = readFromCache(restaurantId);
                    if (dcCheck.isSentinel()) {
                        throwNotFound(restaurantId);
                    }
                    if (dcCheck.hasValue()) {
                        log.debug("Cache HIT (double-check): restaurantId={}", restaurantId);
                        return dcCheck.value();
                    }

                    // Lock holder: query the DB.
                    log.debug("Cache MISS — loading from DB (lock holder): restaurantId={}", restaurantId);
                    Optional<RestaurantResponse> result = loader.get();

                    if (result.isEmpty()) {
                        // Restaurant does not exist — cache the sentinel to block future penetrations.
                        writeSentinel(restaurantId);
                        throwNotFound(restaurantId);
                    }

                    // Write real data with jittered TTL (avalanche prevention).
                    writeToCache(restaurantId, result.get());
                    return result.get();

                } finally {
                    releaseLock(restaurantId, token);
                }
            }

            // Lock held by another thread — wait and re-check.
            log.debug("Lock contention (attempt {}/{}): restaurantId={}", attempt, MAX_RETRY_ATTEMPTS, restaurantId);
            sleepQuietly(RETRY_WAIT_MS);

            CacheResult retryHit = readFromCache(restaurantId);
            if (retryHit.isSentinel()) {
                throwNotFound(restaurantId);
            }
            if (retryHit.hasValue()) {
                log.debug("Cache HIT (after lock wait): restaurantId={}", restaurantId);
                return retryHit.value();
            }
        }

        // ── 3. Fallback: all retries exhausted ───────────────────────────────
        // Rare scenario. Serve from DB without caching.
        log.warn("Stampede protection exhausted (restaurantId={}). Direct DB fallback.", restaurantId);
        return loader.get()
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));
    }

    /**
     * Evicts the cache entry for a restaurant.
     * Called after any write mutation (update, toggle, soft-delete, admin approve/reject).
     */
    public void evict(Long restaurantId) {
        try {
            Boolean deleted = redisTemplate.delete(cacheKey(restaurantId));
            if (Boolean.TRUE.equals(deleted)) {
                log.info("Restaurant cache evicted: restaurantId={}", restaurantId);
            } else {
                log.debug("Restaurant cache evict (key not present): restaurantId={}", restaurantId);
            }
        } catch (Exception e) {
            log.error("Restaurant cache evict failed: restaurantId={}, error={}", restaurantId, e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INTERNAL: CACHE READ / WRITE
    // ══════════════════════════════════════════════════════════════════════════

    private CacheResult readFromCache(Long restaurantId) {
        try {
            Object raw = redisTemplate.opsForValue().get(cacheKey(restaurantId));
            if (raw == null) {
                return CacheResult.miss();
            }
            if (NULL_SENTINEL.equals(raw)) {
                return CacheResult.sentinel();
            }
            if (raw instanceof RestaurantResponse response) {
                return CacheResult.hit(response);
            }
        } catch (Exception e) {
            // Redis down or deserialization error — treat as miss, never crash.
            log.error("Restaurant cache GET failed: restaurantId={}, error={}", restaurantId, e.getMessage());
        }
        return CacheResult.miss();
    }

    private void writeToCache(Long restaurantId, RestaurantResponse response) {
        try {
            long ttlSeconds = (CACHE_TTL_MINUTES * 60) + JITTER_RANDOM.nextInt(JITTER_MAX_SECONDS);
            redisTemplate.opsForValue().set(cacheKey(restaurantId), response, ttlSeconds, TimeUnit.SECONDS);
            log.debug("Restaurant cached: restaurantId={}, TTL={}s", restaurantId, ttlSeconds);
        } catch (Exception e) {
            log.error("Restaurant cache PUT failed: restaurantId={}, error={}", restaurantId, e.getMessage());
        }
    }

    /**
     * Writes the NULL_SENTINEL for a non-existent restaurant.
     * Uses a short fixed TTL (PENETRATION_TTL_SECONDS) so a newly created
     * restaurant becomes visible within 2 minutes.
     * No jitter needed — sentinel TTL is intentionally short.
     */
    private void writeSentinel(Long restaurantId) {
        try {
            redisTemplate.opsForValue().set(
                    cacheKey(restaurantId),
                    NULL_SENTINEL,
                    PENETRATION_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            log.debug("Penetration sentinel cached: restaurantId={}, TTL={}s",
                    restaurantId, PENETRATION_TTL_SECONDS);
        } catch (Exception e) {
            log.error("Sentinel PUT failed: restaurantId={}, error={}", restaurantId, e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INTERNAL: DISTRIBUTED LOCK
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Tries to acquire the distributed lock using SET NX EX.
     * Returns the unique UUID token if acquired, empty otherwise.
     */
    private Optional<String> tryAcquireLock(Long restaurantId) {
        try {
            String token = UUID.randomUUID().toString();
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    lockKey(restaurantId),
                    token,
                    LOCK_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
        } catch (Exception e) {
            log.error("Lock acquire failed: restaurantId={}, error={}", restaurantId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Releases the lock atomically using a Lua script.
     * Only deletes the key if the stored value matches our token.
     * If the lock has expired, this is a safe no-op.
     */
    private void releaseLock(Long restaurantId, String token) {
        try {
            Long result = redisTemplate.execute(
                    RELEASE_LOCK_SCRIPT,
                    Collections.singletonList(lockKey(restaurantId)),
                    token
            );
            if (result == null || result != 1L) {
                log.warn("Lock NOT released (expired or stolen): restaurantId={}, token={}", restaurantId, token);
            }
        } catch (Exception e) {
            // TTL auto-expires the lock — not fatal.
            log.error("Lock release failed: restaurantId={}", restaurantId);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private String cacheKey(Long restaurantId) {
        return CACHE_PREFIX + restaurantId;
    }

    private String lockKey(Long restaurantId) {
        return LOCK_PREFIX + restaurantId;
    }

    private void throwNotFound(Long restaurantId) {
        throw new ResourceNotFoundException("Restaurant", "id", restaurantId);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VALUE OBJECT: CacheResult (discriminated union)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Discriminated union representing three possible cache outcomes:
     *   MISS     → key not present in Redis
     *   SENTINEL → NULL_SENTINEL stored (restaurant doesn't exist)
     *   HIT      → real RestaurantResponse found
     *
     * Avoids null-ambiguity between "not in cache" and "cached as null".
     */
    private sealed interface CacheResult
            permits CacheResult.Miss, CacheResult.Sentinel, CacheResult.Hit {

        static CacheResult miss()               { return new Miss();        }
        static CacheResult sentinel()           { return new Sentinel();    }
        static CacheResult hit(RestaurantResponse v) { return new Hit(v);  }

        default boolean isSentinel() { return this instanceof Sentinel; }
        default boolean hasValue()   { return this instanceof Hit;      }
        default RestaurantResponse value() {
            return ((Hit) this).response();
        }

        record Miss()     implements CacheResult {}
        record Sentinel() implements CacheResult {}
        record Hit(RestaurantResponse response) implements CacheResult {}
    }
}
