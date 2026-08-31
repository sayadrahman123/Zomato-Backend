package com.zomato.backend.controller;

import com.zomato.backend.dto.request.AddToCartRequest;
import com.zomato.backend.dto.response.ApiResponse;
import com.zomato.backend.dto.response.CartResponse;
import com.zomato.backend.service.CartService;
import com.zomato.backend.util.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Shopping cart endpoints — all require authentication.
 *
 * Base path: /api/cart
 *
 * The cart is stored entirely in Redis.
 * No database writes happen here — only Redis Hash operations.
 *
 * Access: CUSTOMER only (restaurant owners don't have personal carts).
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Cart", description = "Redis-backed shopping cart — add, update, remove, view and clear")
public class CartController {

    private final CartService cartService;
    private final AuthUtils   authUtils;

    // ── GET /api/cart ─────────────────────────────────────────────────────────

    @Operation(
        summary     = "Get current cart",
        description = "Returns the authenticated customer's cart. " +
                      "Returns an empty cart if nothing has been added yet or the cart has expired."
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(HttpServletRequest httpRequest) {
        Long userId = authUtils.getCurrentUserId(httpRequest);
        CartResponse cart = cartService.getCart(userId);
        return ResponseEntity.ok(ApiResponse.success("Cart fetched successfully", cart));
    }

    // ── POST /api/cart/items ──────────────────────────────────────────────────

    @Operation(
        summary     = "Add or update a cart item",
        description = "Adds a menu item to the cart, or updates its quantity if already present. " +
                      "Sending quantity=0 removes the item. " +
                      "All items must belong to the same restaurant — adding from a different " +
                      "restaurant returns 400 (clear cart first)."
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addOrUpdateItem(
            @Valid @RequestBody AddToCartRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = authUtils.getCurrentUserId(httpRequest);
        CartResponse cart = cartService.addOrUpdateItem(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Cart updated", cart));
    }

    // ── DELETE /api/cart/items/{itemId} ───────────────────────────────────────

    @Operation(
        summary     = "Remove a single item from the cart",
        description = "Removes one line item by its menu item ID. " +
                      "Returns the updated cart. If the cart is now empty, returns an empty cart."
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @PathVariable Long itemId,
            HttpServletRequest httpRequest
    ) {
        Long userId = authUtils.getCurrentUserId(httpRequest);
        CartResponse cart = cartService.removeItem(userId, itemId);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart", cart));
    }

    // ── DELETE /api/cart ──────────────────────────────────────────────────────

    @Operation(
        summary     = "Clear the entire cart",
        description = "Deletes all items from the cart. " +
                      "Called automatically after a successful order is placed."
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart(HttpServletRequest httpRequest) {
        Long userId = authUtils.getCurrentUserId(httpRequest);
        cartService.clearCart(userId);
        return ResponseEntity.ok(ApiResponse.success("Cart cleared"));
    }
}
