package com.zomato.backend.dto.request;

import com.zomato.backend.entity.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for PATCH /api/orders/{id}/status
 *
 * Used by restaurant owners to advance the order through its lifecycle:
 *   PENDING → CONFIRMED → PREPARING → OUT_FOR_DELIVERY → DELIVERED
 *
 * Transition validation is enforced in OrderService (state machine logic),
 * not here — this DTO just carries the intended next status.
 */
public record UpdateOrderStatusRequest(

        @NotNull(message = "New status is required")
        OrderStatus status
) {}
