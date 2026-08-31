package com.zomato.backend.service;

import com.zomato.backend.config.AppProperties;
import com.zomato.backend.dto.request.AddToCartRequest;
import com.zomato.backend.dto.response.CartResponse;
import com.zomato.backend.entity.MenuItem;
import com.zomato.backend.exception.BusinessException;
import com.zomato.backend.exception.ResourceNotFoundException;
import com.zomato.backend.model.CartItem;
import com.zomato.backend.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manages the shopping cart stored as a Redis Hash.
 *
 * Redis key structure:
 *   Key:   "cart:{userId}"                     ← one hash per customer
 *   Field: "{itemId}"   (String)
 *   Value: CartItem     (serialised as JSON)
 *   TTL:   app.cache.cart-ttl-days (default 1 day)
 *
 * Single-restaurant constraint:
 *   All items in a cart must belong to the same restaurant.
 *   Adding an item from a different restaurant throws a BusinessException
 *   prompting the customer to clear their cart first.
 *
 * Max quantity cap: 20 units per item (anti-abuse).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private static final String CART_KEY_PREFIX = "cart:";
    private static final int    MAX_ITEM_QTY    = 20;

    private final RedisTemplate<String, Object> redisTemplate;
    private final MenuItemRepository            menuItemRepository;
    private final AppProperties                 appProperties;

    // ── Add / Update Item ─────────────────────────────────────────────────────

    /**
     * Adds an item to the cart or updates its quantity.
     * Sending quantity=0 removes the item from the cart.
     *
     * Steps:
     *  1. Load and validate the MenuItem (must be active + available)
     *  2. Enforce single-restaurant constraint
     *  3. Put/update/delete the item in the Redis Hash
     *  4. Refresh TTL
     *  5. Return the updated CartResponse
     *
     * @param userId  authenticated customer's user ID
     * @param request { itemId, quantity }
     * @return updated cart state
     */
    public CartResponse addOrUpdateItem(Long userId, AddToCartRequest request) {
        // ── 1. Validate the menu item ─────────────────────────────────────────
        MenuItem item = menuItemRepository.findById(request.itemId())
                .orElseThrow(() -> new ResourceNotFoundException("MenuItem", "id", request.itemId()));

        if (!item.getIsActive() || !item.getIsAvailable()) {
            throw new BusinessException("'" + item.getName() + "' is currently unavailable.");
        }

        // ── 2. Remove shortcut: quantity = 0 ─────────────────────────────────
        if (request.quantity() == 0) {
            return removeItem(userId, request.itemId());
        }

        // ── 3. Quantity cap ───────────────────────────────────────────────────
        if (request.quantity() > MAX_ITEM_QTY) {
            throw new BusinessException(
                    "Maximum " + MAX_ITEM_QTY + " units per item allowed.");
        }

        // ── 4. Single-restaurant constraint ───────────────────────────────────
        String          cartKey    = cartKey(userId);
        HashOperations<String, String, Object> hashOps = redisTemplate.opsForHash();
        Map<String, Object> existingEntries = hashOps.entries(cartKey);

        if (!existingEntries.isEmpty()) {
            // Peek at any existing item to get its restaurantId
            CartItem any = toCartItem(existingEntries.values().iterator().next());
            if (any != null && !any.getRestaurantId().equals(item.getRestaurant().getId())) {
                throw new BusinessException(
                        "Your cart has items from '" + any.getRestaurantName() +
                        "'. Clear your cart to order from a different restaurant.");
            }
        }

        // ── 5. Build the CartItem snapshot ────────────────────────────────────
        BigDecimal effective = (item.getDiscountedPrice() != null
                && item.getDiscountedPrice().compareTo(item.getPrice()) < 0)
                ? item.getDiscountedPrice()
                : item.getPrice();

        CartItem cartItem = CartItem.builder()
                .itemId(item.getId())
                .itemName(item.getName())
                .price(item.getPrice())
                .effectivePrice(effective)
                .quantity(request.quantity())
                .restaurantId(item.getRestaurant().getId())
                .restaurantName(item.getRestaurant().getName())
                .build();

        // ── 6. Persist to Redis Hash ──────────────────────────────────────────
        hashOps.put(cartKey, item.getId().toString(), cartItem);
        refreshTtl(cartKey);

        log.info("Cart updated: userId={}, itemId={}, qty={}", userId, request.itemId(), request.quantity());
        return buildCartResponse(cartKey, hashOps);
    }

    // ── Remove Item ───────────────────────────────────────────────────────────

    /**
     * Removes a single item from the cart by itemId.
     * If the cart becomes empty, the Redis key is deleted.
     */
    public CartResponse removeItem(Long userId, Long itemId) {
        String cartKey = cartKey(userId);
        HashOperations<String, String, Object> hashOps = redisTemplate.opsForHash();

        Long deleted = hashOps.delete(cartKey, itemId.toString());
        if (deleted == null || deleted == 0) {
            log.debug("removeItem: item {} not found in cart for user {}", itemId, userId);
        }

        // Clean up the key entirely if the cart is now empty
        Map<String, Object> remaining = hashOps.entries(cartKey);
        if (remaining.isEmpty()) {
            redisTemplate.delete(cartKey);
            return CartResponse.empty();
        }

        refreshTtl(cartKey);
        return buildCartResponse(cartKey, hashOps);
    }

    // ── Get Cart ──────────────────────────────────────────────────────────────

    /**
     * Returns the current cart state.
     * Returns an empty CartResponse if the cart doesn't exist or has expired.
     */
    public CartResponse getCart(Long userId) {
        String cartKey = cartKey(userId);
        HashOperations<String, String, Object> hashOps = redisTemplate.opsForHash();

        Map<String, Object> entries = hashOps.entries(cartKey);
        if (entries.isEmpty()) {
            return CartResponse.empty();
        }
        return buildCartResponse(entries);
    }

    // ── Clear Cart ────────────────────────────────────────────────────────────

    /**
     * Deletes the entire cart. Called after a successful order placement.
     */
    public void clearCart(Long userId) {
        Boolean deleted = redisTemplate.delete(cartKey(userId));
        log.info("Cart cleared: userId={}, keyExisted={}", userId, Boolean.TRUE.equals(deleted));
    }

    // ── Internal Helpers ──────────────────────────────────────────────────────

    /** Redis key for a user's cart. */
    private String cartKey(Long userId) {
        return CART_KEY_PREFIX + userId;
    }

    /** Resets the TTL every time the cart is touched. */
    private void refreshTtl(String cartKey) {
        long ttlDays = appProperties.getCache().getCartTtlDays();
        long ttlSeconds = (ttlDays > 0 ? ttlDays : 1) * 24 * 60 * 60;
        redisTemplate.expire(cartKey, ttlSeconds, TimeUnit.SECONDS);
    }

    /**
     * Builds CartResponse from the current Redis Hash.
     * Loads entries fresh from Redis.
     */
    private CartResponse buildCartResponse(
            String cartKey,
            HashOperations<String, String, Object> hashOps
    ) {
        return buildCartResponse(hashOps.entries(cartKey));
    }

    /**
     * Builds CartResponse from a pre-loaded entries map.
     * Computes subtotal, totalItems, and totalQuantity.
     */
    private CartResponse buildCartResponse(Map<String, Object> entries) {
        if (entries.isEmpty()) return CartResponse.empty();

        List<CartItem> items    = new ArrayList<>();
        BigDecimal     subtotal = BigDecimal.ZERO;
        int            totalQty = 0;
        Long           restaurantId   = null;
        String         restaurantName = null;

        for (Object value : entries.values()) {
            CartItem ci = toCartItem(value);
            if (ci == null) continue;
            items.add(ci);
            subtotal = subtotal.add(ci.getLineTotal());
            totalQty += ci.getQuantity();
            restaurantId   = ci.getRestaurantId();
            restaurantName = ci.getRestaurantName();
        }

        return new CartResponse(
                restaurantId,
                restaurantName,
                items,
                items.size(),
                totalQty,
                subtotal
        );
    }

    /**
     * Casts a Redis hash value to CartItem.
     * GenericJackson2JsonRedisSerializer deserializes the stored JSON
     * back to the correct type using the embedded "@class" field.
     */
    private CartItem toCartItem(Object value) {
        if (value instanceof CartItem ci) return ci;
        return null;    // unexpected type — skip
    }
}
