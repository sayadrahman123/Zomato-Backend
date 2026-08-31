package com.zomato.backend.dto.response;

import com.zomato.backend.entity.enums.OrderStatus;
import com.zomato.backend.entity.enums.PaymentStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full order detail — returned on the order confirmation and tracking pages.
 *
 * Contains nested items, delivery address, payment info, and all timestamps.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(
        Long                    id,
        String                  orderNumber,

        // ── Restaurant
        Long                    restaurantId,
        String                  restaurantName,

        // ── Customer
        Long                    customerId,
        String                  customerName,

        // ── Items
        List<OrderItemResponse> items,

        // ── Status
        OrderStatus             status,
        PaymentStatus           paymentStatus,

        // ── Payment
        String                  paymentMethod,
        String                  paymentId,          // Razorpay ID, null for COD

        // ── Pricing
        BigDecimal              subtotal,
        BigDecimal              deliveryFee,
        BigDecimal              totalAmount,

        // ── Delivery
        DeliveryAddressResponse deliveryAddress,
        String                  specialInstructions,
        LocalDateTime           estimatedDeliveryTime,

        // ── Audit
        LocalDateTime           createdAt,
        LocalDateTime           updatedAt
) {}
