package com.zomato.backend.entity.enums;

/**
 * Lifecycle states of a single delivery assignment.
 *
 * State machine:
 *
 *  ASSIGNED ──→ PICKED_UP ──→ DELIVERED
 *      │                          │
 *      └──────────────→ FAILED ←──┘
 *
 *  ASSIGNED   : Partner has been assigned and is heading to the restaurant.
 *  PICKED_UP  : Partner collected the order from the restaurant.
 *  DELIVERED  : Partner handed the order to the customer. Terminal.
 *  FAILED     : Delivery could not be completed (customer unreachable,
 *               address not found, etc.). Terminal.
 */
public enum DeliveryStatus {
    ASSIGNED,
    PICKED_UP,
    DELIVERED,
    FAILED
}
