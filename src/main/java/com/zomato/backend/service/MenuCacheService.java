package com.zomato.backend.service;

import com.zomato.backend.dto.response.MenuCategoryWithItemsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Industry-grade Redis caching layer for the Menu module.
 *
 * Solves three problems with @Cacheable / @CacheEvict:
 *
 * 1. Cache Stampede (Thundering Herd):
 *    When a popular restaurant's cache key expires, N concurrent requests
 *    all get a cache miss simultaneously and all hammer the DB.
 *    Fix: Redis distributed lock (SET NX EX).
 *      - Only the lock-holder queries the DB and populates the cache.
 *      - Other threads wait briefly, then re-check the cache.
 *      - If lock is never acquired (after MAX_RETRIES), fall back to a
 *        direct DB query so the user still gets a response.
 *
 * 2. Double-Checked Locking (prevents redundant DB queries):
 *    After acquiring the lock, we check the cache again — another thread
 *    may have already populated it between our miss and our lock acquisition.
 *
 * 3. Lock Leases (prevents deadlock):
 *    The Redis lock has a TTL of LOCK_TTL_SECONDS. Even if the process
 *    crashes while holding the lock, it auto-expires so other threads
 *    can eventually proceed.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * Key naming scheme:
 *
 *   menu:restaurant:{restaurantId}   → cached menu (List<MenuCategoryWithItemsResponse>)
 *   menu:lock:{restaurantId}         → distributed lock token (value: "LOCKED")
 *
 * TTLs:
 *   Cache: CACHE_TTL_MINUTES  = 15 minutes
 *   Lock:  LOCK_TTL_SECONDS   = 30 seconds (max lock hold time)
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuCacheService {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String CACHE_KEY_PREFIX = "menu:restaurant:";
    private static final String LOCK_KEY_PREFIX  = "menu:lock:";

    /** How long the cached menu stays valid. */
    private static final long CACHE_TTL_MINUTES  = 15L;

    /**
     * Max time a lock can be held. If the lock holder crashes, Redis
     * auto-expires the lock after this many seconds — preventing deadlock.
     */
    private static final long LOCK_TTL_SECONDS   = 30L;

    /** How many times to retry before falling back to a direct DB query. */
    private static final int  MAX_RETRY_ATTEMPTS  = 3;

    /** How long (ms) to wait between retry attempts. */
    private static final long RETRY_WAIT_MS       = 100L;

    private final RedisTemplate<String, Object> redisTemplate;

    // ── Core: Stampede-Protected Get-or-Load ─────────────────────────────────

    /**
     * The primary entry point for MenuService.
     *
     * Algorithm:
     *   1. Try cache → hit → return immediately (hot path, no lock needed)
     *   2. Cache miss → try to acquire Redis lock
     *      a. Lock acquired:
     *         i.  Double-check cache (another thread may have just populated it)
     *         ii. Call loader (DB query) → write to cache → release lock → return
     *      b. Lock not acquired → wait RETRY_WAIT_MS → re-check cache → retry
     *   3. After MAX_RETRY_ATTEMPTS with no lock and no cache hit:
     *      → DB fallback (user still gets data, just uncached)
     *      → Log warning so this can be monitored / tuned
     *
     * @param restaurantId the restaurant whose menu we're loading
     * @param loader       a Supplier that queries the DB — called at most once
     * @return the menu, from cache or DB
     */
    @SuppressWarnings("unchecked")
    public List<MenuCategoryWithItemsResponse> getOrLoad(
            Long restaurantId,
            Supplier<List<MenuCategoryWithItemsResponse>> loader
    ) {
        // ── Step 1: Fast path — cache hit ─────────────────────────────────────
        Optional<List<MenuCategoryWithItemsResponse>> cached = getFromCache(restaurantId);
        if (cached.isPresent()) {
            log.debug("Menu cache HIT: restaurantId={}", restaurantId);
            return cached.get();
        }

        // ── Step 2: Cache miss — try distributed lock ─────────────────────────
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {

            if (acquireLock(restaurantId)) {
                try {
                    // Double-check: another thread may have populated cache while we waited for the lock
                    Optional<List<MenuCategoryWithItemsResponse>> doubleChecked = getFromCache(restaurantId);
                    if (doubleChecked.isPresent()) {
                        log.debug("Menu cache HIT (double-check after lock): restaurantId={}", restaurantId);
                        return doubleChecked.get();
                    }

                    // We're the chosen thread — load from DB and populate cache
                    log.debug("Menu cache MISS (lock acquired): loading from DB, restaurantId={}", restaurantId);
                    List<MenuCategoryWithItemsResponse> menu = loader.get();
                    putInCache(restaurantId, menu);
                    return menu;

                } finally {
                    releaseLock(restaurantId);
                }
            }

            // Lock held by another thread — wait, then re-check cache
            log.debug("Menu lock contention (attempt {}/{}): restaurantId={}", attempt, MAX_RETRY_ATTEMPTS, restaurantId);
            sleepQuietly(RETRY_WAIT_MS);

            Optional<List<MenuCategoryWithItemsResponse>> retryCheck = getFromCache(restaurantId);
            if (retryCheck.isPresent()) {
                log.debug("Menu cache HIT (after lock wait): restaurantId={}", restaurantId);
                return retryCheck.get();
            }
        }

        // ── Step 3: Fallback — all retries exhausted ──────────────────────────
        // This is rare (lock held for >300ms and cache still empty).
        // Return DB data without caching to avoid a thundering write storm.
        log.warn("Menu stampede protection exhausted (restaurantId={}). Falling back to direct DB query.", restaurantId);
        return loader.get();
    }

    // ── Invalidation ──────────────────────────────────────────────────────────

    /**
     * Evicts the cached menu for a restaurant.
     * Called after any menu mutation (add/update/delete category or item).
     *
     * The next call to getOrLoad() will rebuild the cache from the DB.
     */
    public void evict(Long restaurantId) {
        Boolean deleted = redisTemplate.delete(cacheKey(restaurantId));
        if (Boolean.TRUE.equals(deleted)) {
            log.info("Menu cache evicted: restaurantId={}", restaurantId);
        } else {
            log.debug("Menu cache evict (key not found, already evicted): restaurantId={}", restaurantId);
        }
    }

    // ── Internal: Cache Read/Write ────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Optional<List<MenuCategoryWithItemsResponse>> getFromCache(Long restaurantId) {
        try {
            Object raw = redisTemplate.opsForValue().get(cacheKey(restaurantId));
            if (raw instanceof List<?> list) {
                return Optional.of((List<MenuCategoryWithItemsResponse>) list);
            }
        } catch (Exception e) {
            // Redis down / deserialization error — treat as cache miss, do not crash
            log.error("Menu cache GET failed (Redis error): restaurantId={}, error={}", restaurantId, e.getMessage());
        }
        return Optional.empty();
    }

    private void putInCache(Long restaurantId, List<MenuCategoryWithItemsResponse> menu) {
        try {
            redisTemplate.opsForValue().set(
                    cacheKey(restaurantId),
                    menu,
                    CACHE_TTL_MINUTES,
                    TimeUnit.MINUTES
            );
            log.debug("Menu cached: restaurantId={}, TTL={}min", restaurantId, CACHE_TTL_MINUTES);
        } catch (Exception e) {
            // Redis down — log and continue. The result is still returned from DB.
            log.error("Menu cache PUT failed (Redis error): restaurantId={}, error={}", restaurantId, e.getMessage());
        }
    }

    // ── Internal: Distributed Lock ────────────────────────────────────────────

    /**
     * Tries to acquire the distributed lock using Redis SET NX EX.
     * Returns true only if this thread is now the lock holder.
     *
     * SET key "LOCKED" NX EX 30
     *   NX → only set if key does NOT already exist
     *   EX → expire after LOCK_TTL_SECONDS seconds
     */
    private boolean acquireLock(Long restaurantId) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                    lockKey(restaurantId),
                    "LOCKED",
                    LOCK_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.error("Menu lock acquire failed (Redis error): restaurantId={}, error={}", restaurantId, e.getMessage());
            return false; // Treat as "lock not acquired" → fallback path
        }
    }

    /**
     * Releases the distributed lock by deleting the lock key.
     * Safe to call even if the lock has already expired.
     */
    private void releaseLock(Long restaurantId) {
        try {
            redisTemplate.delete(lockKey(restaurantId));
        } catch (Exception e) {
            // Lock will auto-expire via TTL — not fatal
            log.error("Menu lock release failed (Redis error): restaurantId={}", restaurantId);
        }
    }

    // ── Key builders ──────────────────────────────────────────────────────────

    private String cacheKey(Long restaurantId) {
        return CACHE_KEY_PREFIX + restaurantId;
    }

    private String lockKey(Long restaurantId) {
        return LOCK_KEY_PREFIX + restaurantId;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
