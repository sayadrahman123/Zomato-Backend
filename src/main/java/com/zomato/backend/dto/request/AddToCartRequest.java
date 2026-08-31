package com.zomato.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for POST /api/cart/items
 *
 * Adds a menu item to the cart or updates its quantity.
 * Sending quantity=0 removes the item from the cart.
 */
public record AddToCartRequest(

        @NotNull(message = "Item ID is required")
        Long itemId,

        /**
         * Quantity to set (not increment).
         * 0 = remove the item.
         * Max is validated in CartService (anti-abuse cap).
         */
        @NotNull(message = "Quantity is required")
        @Min(value = 0, message = "Quantity cannot be negative")
        Integer quantity
) {}
