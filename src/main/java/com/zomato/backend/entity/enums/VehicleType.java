package com.zomato.backend.entity.enums;

/**
 * Vehicle type used by a delivery partner.
 * Stored as VARCHAR (EnumType.STRING) — safe against reordering.
 *
 *  BICYCLE    → zero-emission, short distances, low-value orders
 *  SCOOTER    → most common for food delivery in Indian cities
 *  MOTORCYCLE → faster, covers more area
 *  CAR        → large orders, premium deliveries, bad weather
 */
public enum VehicleType {
    BICYCLE,
    SCOOTER,
    MOTORCYCLE,
    CAR
}
