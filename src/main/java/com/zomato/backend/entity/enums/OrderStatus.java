package com.zomato.backend.entity.enums;

/**
 * Lifecycle states of a customer order.
 *
 * State machine:
 *
 *  PENDING ──→ CONFIRMED ──→ PREPARING ──→ OUT_FOR_DELIVERY ──→ DELIVERED
 *     │              │
 *     └──→ CANCELLED ←┘     (only before PREPARING)
 *
 *  PENDING     : Order placed, awaiting restaurant confirmation.
 *  CONFIRMED   : Restaurant accepted the order.
 *  PREPARING   : Restaurant is cooking the food.
 *  OUT_FOR_DELIVERY : Delivery partner picked up the order.
 *  DELIVERED   : Customer received the order. Terminal state.
 *  CANCELLED   : Cancelled by customer or restaurant. Terminal state.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PREPARING,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED
}
