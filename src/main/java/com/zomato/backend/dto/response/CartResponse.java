package com.zomato.backend.dto.response;

import com.zomato.backend.model.CartItem;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

/**
 * Full cart state returned to the client.
 *
 * Example response:
 * <pre>
 * {
 *   "restaurantId":   5,
 *   "restaurantName": "Spice Garden",
 *   "items": [
 *     { "itemId": 12, "itemName": "Paneer Tikka", "effectivePrice": 200.00, "quantity": 2, "lineTotal": 400.00 },
 *     { "itemId": 15, "itemName": "Naan",         "effectivePrice":  40.00, "quantity": 3, "lineTotal": 120.00 }
 *   ],
 *   "totalItems": 2,
 *   "totalQuantity": 5,
 *   "subtotal": 520.00
 * }
 * </pre>
 *
 * restaurantId/restaurantName are null for an empty cart.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CartResponse(

        Long          restaurantId,
        String        restaurantName,
        List<CartItem> items,

        /** Number of distinct items (line count). */
        Integer       totalItems,

        /** Sum of all quantities across all items. */
        Integer       totalQuantity,

        /** Sum of lineTotal across all items (what the customer pays before delivery fee). */
        BigDecimal    subtotal
) {
    /** Convenience factory for an empty cart. */
    public static CartResponse empty() {
        return new CartResponse(null, null, List.of(), 0, 0, BigDecimal.ZERO);
    }
}
