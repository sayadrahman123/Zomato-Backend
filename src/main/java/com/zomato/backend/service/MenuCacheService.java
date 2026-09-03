package com.zomato.backend.service;

import com.zomato.backend.dto.response.MenuCategoryWithItemsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Industry-grade Redis caching layer for the Menu module.
 * <p>
 * Solves four problems with naive @Cacheable / @CacheEvict:
 * <p>
 * 1. Cache Stampede (Thundering Herd):
 *    When a popular restaurant's cache key expires, N concurrent requests
 *    all get a cache miss simultaneously, and all hammer the DB.
 *    Fix: Redis distributed lock (SET NX EX with a unique token).
 *      - Only the lock-holder queries the DB and populates the cache.
 *      - Other threads wait briefly, then re-check the cache.
 *      - If lock is never acquired (after MAX_RETRIES), fall back to a
 *        direct DB query so the user still gets a response.
 * <p>
 * 2. Double-Checked Locking (prevents redundant DB queries):
 *    After acquiring the lock, we check the cache again — another thread
 *    may have already populated it between our miss and our lock acquisition.
 * <p>
 * 3. Lock Leases (prevents deadlock):
 *    The Redis lock has a TTL of LOCK_TTL_SECONDS. Even if the process
 *    crashes while holding the lock, it auto-expires so other threads
 *    can eventually proceed.
 * <p>
 * 4. Safe Lock Release via Lua Script (prevents lock theft):
 *    A naive DEL on the lock key is unsafe:
 *      - Thread A's lock TTL expires (e.g. DB took > LOCK_TTL_SECONDS)
 *      - Thread B acquires the same lock key
 *      - Thread A finishes and calls DEL → accidentally deletes Thread B's lock
 *    Fix: Each acquisition stores a UUID token as the lock value.
 *    Release is done via a Lua script that atomically checks
 *    "is this my token?" before deleting:
 *      if GET(key) == token then DEL(key) end
 *    Because Lua scripts are executed atomically by Redis, there is no
 *    TOCTOU race between the check and the delete.
 * <p>
 * ─────────────────────────────────────────────────────────────────────────────
 * Key naming scheme:
 * <p>
 *   menu:restaurant:{restaurantId}   → cached menu (List of MenuCategoryWithItemsResponse)
 *   menu:lock:{restaurantId}         → distributed lock (value: UUID token)
 * <p>
 * TTLs:
 *   Cache: CACHE_TTL_MINUTES = 15 minutes
 *   Lock: LOCK_TTL_SECONDS = 30 seconds (max lock hold time before auto-expiry)
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuCacheService {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String CACHE_KEY_PREFIX  = "menu:restaurant:";
    private static final String LOCK_KEY_PREFIX   = "menu:lock:";

    /** How long the cached menu stays valid. */
    private static final long   CACHE_TTL_MINUTES = 15L;

    /**
     * Max time a lock can be held before Redis auto-expires it.
     * Prevents deadlock if the lock holder crashes or is killed.
     * Must be longer than the slowest expected DB query.
     */
    private static final long   LOCK_TTL_SECONDS  = 30L;

    /** How many times to retry before falling back to a direct DB query. */
    private static final int    MAX_RETRY_ATTEMPTS = 3;

    /** How long (ms) to wait between retry attempts. */
    private static final long   RETRY_WAIT_MS      = 100L;

    /**
     * Lua script for safe, atomic lock release.
     * <p>
     * Logic (runs atomically inside Redis, no TOCTOU race):
     *   if GET(KEYS[1]) == ARGV[1] then ← is this our token?
     *       DEL(KEYS[1]) ← yes → release the lock
     *       return 1 ← released
     *   else
     *       return 0 ← not ours (expired or stolen) → skip
     *   end
     * <p>
     * KEYS[1] = lock key (e.g. "menu:lock:42")
     * ARGV[1] = the UUID token we stored when we acquired the lock
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

    private final RedisTemplate<String, Object> redisTemplate;

    // ── Core: Stampede-Protected Get-or-Load ─────────────────────────────────

    /**
     * The primary entry point for MenuService.
     * <p>
     * Algorithm:
     *   1. Try cache → hit → return immediately (hot path, no lock needed)
     *   2. Cache miss → try to acquire Redis lock (returns a UUID token)
     *      a. Lock acquired:
     *         i.  Double-check cache (another thread may have just populated it)
     *         ii. Call loader (DB query) → write to cache → release lock with token → return
     *      b. Lock not acquired → wait RETRY_WAIT_MS → re-check cache → retry
     *   3. After MAX_RETRY_ATTEMPTS with no lock and no cache hit:
     *      → DB fallback (user still gets data, just uncached this time)
     *      → Log warning so this can be monitored and tuned
     *
     * @param restaurantId the restaurant whose menu we're loading
     * @param loader       a Supplier that queries the DB — called at most once under normal conditions
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

        // ── Step 2: Cache miss — compete for distributed lock ─────────────────
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {

            Optional<String> tokenOpt = tryAcquireLock(restaurantId);

            if (tokenOpt.isPresent()) {
                String token = tokenOpt.get();
                try {
                    // Double-check: another thread may have populated the cache
                    // between our miss and our lock acquisition.
                    Optional<List<MenuCategoryWithItemsResponse>> doubleChecked = getFromCache(restaurantId);
                    if (doubleChecked.isPresent()) {
                        log.debug("Menu cache HIT (double-check after lock): restaurantId={}", restaurantId);
                        return doubleChecked.get();
                    }

                    // We are the chosen thread — load from DB and populate cache.
                    log.debug("Menu cache MISS — loading from DB (lock holder): restaurantId={}", restaurantId);
                    List<MenuCategoryWithItemsResponse> menu = loader.get();
                    putInCache(restaurantId, menu);
                    return menu;

                } finally {
                    // Safe release: Lua script checks our token before deleting.
                    // If our lock TTL expired while we held it, this is a no-op
                    // (returns 0) rather than accidentally deleting another thread's lock.
                    releaseLock(restaurantId, token);
                }
            }

            // Lock held by another thread — wait, then re-check cache.
            log.debug("Menu lock contention (attempt {}/{}): restaurantId={}", attempt, MAX_RETRY_ATTEMPTS, restaurantId);
            sleepQuietly(RETRY_WAIT_MS);

            Optional<List<MenuCategoryWithItemsResponse>> retryCheck = getFromCache(restaurantId);
            if (retryCheck.isPresent()) {
                log.debug("Menu cache HIT (after lock wait): restaurantId={}", restaurantId);
                return retryCheck.get();
            }
        }

        // ── Step 3: Fallback — all retries exhausted ──────────────────────────
        // Rare: lock held for > MAX_RETRIES * RETRY_WAIT_MS and cache still empty.
        // Serve directly from DB without caching to avoid a write storm on Redis.
        log.warn("Menu cache stampede protection exhausted (restaurantId={}). Falling back to direct DB.", restaurantId);
        return loader.get();
    }

    // ── Invalidation ──────────────────────────────────────────────────────────

    /**
     * Evicts the cached menu for a restaurant.
     * Called after any menu mutation (create/update/delete category or item).
     * The next call to getOrLoad() will rebuild the cache from the DB.
     */
    public void evict(Long restaurantId) {
        try {
            Boolean deleted = redisTemplate.delete(cacheKey(restaurantId));
            if (deleted) {
                log.info("Menu cache evicted: restaurantId={}", restaurantId);
            } else {
                log.debug("Menu cache evict (key not present): restaurantId={}", restaurantId);
            }
        } catch (Exception e) {
            log.error("Menu cache evict failed (Redis error): restaurantId={}, error={}", restaurantId, e.getMessage());
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
            // Redis down or deserialization error — treat as cache miss, never crash.
            log.error("Menu cache GET failed: restaurantId={}, error={}", restaurantId, e.getMessage());
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
            // Redis down — log and continue. Result is still returned from DB.
            log.error("Menu cache PUT failed: restaurantId={}, error={}", restaurantId, e.getMessage());
        }
    }

    // ── Internal: Distributed Lock ────────────────────────────────────────────

    /**
     * Tries to acquire the distributed lock using Redis SET NX EX.
     * <p>
     * Each acquisition generates a unique UUID token stored as the lock value.
     * This token is later used by the Lua release script to prove ownership.
     * <p>
     * Command: SET {lockKey} {uuid} NX EX {LOCK_TTL_SECONDS}
     *   NX → only set if key does NOT already exist (atomic)
     *   EX → expire after LOCK_TTL_SECONDS (prevents deadlock on crash)
     *
     * @return the UUID token wrapped in Optional if acquired; empty if lock is held by another thread
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
            if (Boolean.TRUE.equals(acquired)) {
                return Optional.of(token);
            }
        } catch (Exception e) {
            log.error("Menu lock acquire failed (Redis error): restaurantId={}, error={}", restaurantId, e.getMessage());
            // Treat as "not acquired" — caller will fall through to DB fallback.
        }
        return Optional.empty();
    }

    /**
     * Releases the distributed lock atomically using a Lua script.
     * <p>
     * The script checks that the lock's current value matches our token before
     * deleting — preventing accidental release of another thread's lock.
     * <p>
     * Why this matters:
     *   Without token check: Thread A's lock expires → Thread B acquires lock →
     *   Thread A calls DEL → deletes Thread B's lock. Thread C now also acquires
     *   the lock → two threads both believe they hold it simultaneously. ❌
     * <p>
     *   With Lua + token: Thread A's lock expires → Thread B acquires lock with
     *   token-B → Thread A's Lua script: GET(key) == token-A? NO → no-op. ✅
     *
     * @param restaurantId the restaurant whose lock we hold
     * @param token        the UUID we stored when we acquired the lock
     */
    private void releaseLock(Long restaurantId, String token) {
        try {
            Long result = redisTemplate.execute(
                    RELEASE_LOCK_SCRIPT,
                    Collections.singletonList(lockKey(restaurantId)),
                    token
            );
            if (Long.valueOf(1L).equals(result)) {
                log.debug("Menu lock released (Lua): restaurantId={}", restaurantId);
            } else {
                // Lock had already expired or was taken by another thread.
                // This is acceptable — the TTL protected the system.
                log.warn("Menu lock NOT released (expired or stolen): restaurantId={}, token={}", restaurantId, token);
            }
        } catch (Exception e) {
            // Lock will auto-expire via TTL — not fatal.
            log.error("Menu lock release failed (Redis error): restaurantId={}", restaurantId);
        }
    }

    // ── Key Builders ──────────────────────────────────────────────────────────

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
