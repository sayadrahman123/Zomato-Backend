package com.zomato.backend.dto.response;

import java.math.BigDecimal;

/**
 * A single line item within an order response.
 * All values are snapshots — reflect what the customer paid at order time.
 */
public record OrderItemResponse(
        Long       id,
        Long       menuItemId,      // nullable — item may have been deleted
        String     itemName,        // snapshot
        BigDecimal price,           // snapshot (effective price paid)
        Integer    quantity,
        BigDecimal lineTotal        // price × quantity
) {}
