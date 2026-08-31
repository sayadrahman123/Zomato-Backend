package com.zomato.backend.dto.response;

import com.zomato.backend.entity.enums.OrderStatus;
import com.zomato.backend.entity.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Compact order card — used in paginated order history lists.
 *
 * Deliberately lightweight (no nested items, no full address)
 * to keep list API payloads small when a customer has dozens of orders.
 *
 * Full details (items, address) are in {@link OrderResponse}.
 */
public record OrderSummaryResponse(
        Long          id,
        String        orderNumber,
        String        restaurantName,
        OrderStatus   status,
        PaymentStatus paymentStatus,
        String        paymentMethod,
        BigDecimal    totalAmount,
        Integer       itemCount,        // number of distinct items
        LocalDateTime createdAt
) {}
