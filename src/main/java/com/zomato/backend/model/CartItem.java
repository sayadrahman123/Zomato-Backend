package com.zomato.backend.model;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Represents one line item stored inside a user's Redis cart.
 *
 * Stored as a JSON value inside a Redis Hash:
 *   Key:   "cart:{userId}"
 *   Field: "{itemId}"           ← String key
 *   Value: CartItem (as JSON)   ← this class
 *
 * Why is this NOT a JPA entity?
 *   Carts are ephemeral — they expire (TTL 2h) and should not clutter
 *   the database with potentially millions of unconfirmed carts.
 *   Redis gives us speed (sub-millisecond reads) and automatic expiry.
 *
 * Why snapshot itemName/price at add-time?
 *   If the owner updates a menu item's price while it's in a customer's
 *   cart, the cart should show the price at the time the item was added.
 *   This prevents customers from being surprised by price changes at checkout.
 *
 * Serializable: required for certain Redis serialization strategies.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem implements Serializable {

    private Long       itemId;
    private String     itemName;

    /**
     * Snapshot of price at the time the item was added to cart.
     */
    private BigDecimal price;

    /**
     * Snapshot of effective (discounted) price at add time.
     * = discountedPrice if active, otherwise = price.
     */
    private BigDecimal effectivePrice;

    /**
     * Number of units ordered. Always >= 1.
     * Updating quantity to 0 removes the item from the cart.
     */
    private Integer quantity;

    /**
     * Restaurant this item belongs to.
     * All items in a cart must have the same restaurantId —
     * cross-restaurant ordering is not allowed.
     */
    private Long   restaurantId;
    private String restaurantName;

    /**
     * Convenience method: line total = effectivePrice × quantity.
     */
    public BigDecimal getLineTotal() {
        return effectivePrice.multiply(BigDecimal.valueOf(quantity));
    }
}
