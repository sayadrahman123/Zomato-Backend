package com.zomato.backend.mapper;

import com.zomato.backend.dto.response.*;
import com.zomato.backend.entity.DeliveryAddress;
import com.zomato.backend.entity.Order;
import com.zomato.backend.entity.OrderItem;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Manual mapper between Order/OrderItem entities and their response DTOs.
 */
@Component
public class OrderMapper {

    // ── Order → OrderResponse ─────────────────────────────────────────────────

    /**
     * Full order detail — used on confirmation and tracking pages.
     * Includes nested items and delivery address.
     *
     * NOTE: Requires Order.orderItems and Order.customer/restaurant
     *       to be loaded (call within a @Transactional context).
     */
    public OrderResponse toOrderResponse(Order order) {
        if (order == null) return null;

        List<OrderItemResponse> itemResponses = order.getOrderItems() != null
                ? order.getOrderItems().stream().map(this::toItemResponse).toList()
                : Collections.emptyList();

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),

                // Restaurant
                order.getRestaurant() != null ? order.getRestaurant().getId()   : null,
                order.getRestaurant() != null ? order.getRestaurant().getName() : null,

                // Customer
                order.getCustomer() != null ? order.getCustomer().getId()   : null,
                order.getCustomer() != null ? order.getCustomer().getName() : null,

                // Items
                itemResponses,

                // Status
                order.getStatus(),
                order.getPaymentStatus(),

                // Payment
                order.getPaymentMethod(),
                order.getPaymentId(),

                // Pricing
                order.getSubtotal(),
                order.getDeliveryFee(),
                order.getTotalAmount(),

                // Delivery
                toDeliveryAddressResponse(order.getDeliveryAddress()),
                order.getSpecialInstructions(),
                order.getEstimatedDeliveryTime(),

                // Audit
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    // ── Order → OrderSummaryResponse ──────────────────────────────────────────

    /**
     * Compact card for paginated order history.
     * Does NOT include nested items — uses itemCount instead.
     */
    public OrderSummaryResponse toOrderSummaryResponse(Order order) {
        if (order == null) return null;

        int itemCount = order.getOrderItems() != null
                ? order.getOrderItems().size()
                : 0;

        return new OrderSummaryResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getRestaurant() != null ? order.getRestaurant().getName() : null,
                order.getStatus(),
                order.getPaymentStatus(),
                order.getPaymentMethod(),
                order.getTotalAmount(),
                itemCount,
                order.getCreatedAt()
        );
    }

    // ── OrderItem → OrderItemResponse ─────────────────────────────────────────

    public OrderItemResponse toItemResponse(OrderItem item) {
        if (item == null) return null;

        return new OrderItemResponse(
                item.getId(),
                item.getMenuItem() != null ? item.getMenuItem().getId() : null,
                item.getItemName(),
                item.getPrice(),
                item.getQuantity(),
                item.getLineTotal()
        );
    }

    // ── DeliveryAddress → DeliveryAddressResponse ─────────────────────────────

    public DeliveryAddressResponse toDeliveryAddressResponse(DeliveryAddress address) {
        if (address == null) return null;

        return new DeliveryAddressResponse(
                address.getStreet(),
                address.getArea(),
                address.getCity(),
                address.getState(),
                address.getPincode(),
                address.getLatitude(),
                address.getLongitude()
        );
    }
}
