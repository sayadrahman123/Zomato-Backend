package com.zomato.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * Snapshot of the delivery address captured at order-placement time.
 *
 * Why @Embeddable and NOT a FK to Address entity?
 *   The delivery address is a historical record. If the customer later edits
 *   their saved address, the order's delivery address must remain unchanged.
 *   Embedding the address as columns directly in the orders table achieves this.
 *
 * Columns are prefixed with "delivery_" to avoid naming collisions
 * when embedded inside the Order entity.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryAddress {

    @Column(name = "delivery_street", nullable = false, length = 255)
    private String street;

    @Column(name = "delivery_area", length = 100)
    private String area;

    @Column(name = "delivery_city", nullable = false, length = 100)
    private String city;

    @Column(name = "delivery_state", nullable = false, length = 100)
    private String state;

    @Column(name = "delivery_pincode", nullable = false, length = 10)
    private String pincode;

    @Column(name = "delivery_latitude")
    private Double latitude;

    @Column(name = "delivery_longitude")
    private Double longitude;
}
