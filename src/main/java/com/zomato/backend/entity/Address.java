package com.zomato.backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a physical address linked to a restaurant.
 * <p>
 * Designed as a standalone entity (not @Embeddable) so it can
 * be reused as a snapshot in Order delivery addresses later.
 * <p>
 * Coordinates (latitude, longitude) enable:
 *  - Distance-based restaurant sorting (future)
 *  - Delivery partner assignment (Phase 6)
 *  - Map integrations
 * <p>
 * Note: {@link com.zomato.backend.entity.UserAddress} (Phase 8) is
 * a separate entity for users' saved delivery addresses.
 */
@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Address {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Street-level details ───────────────────────────────────────────────────

    @Column(nullable = false, length = 255)
    private String street;

    /**
     * Locality / Area — e.g. "Koramangala", "Bandra West".
     * Optional but useful for display.
     */
    @Column(length = 100)
    private String area;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String state;

    /**
     * Indian PIN code — 6 digits, stored as String to preserve leading zeros
     * and avoid arithmetic operations on postal codes.
     */
    @Column(nullable = false, length = 10)
    private String pincode;

    // ── Geo Coordinates ───────────────────────────────────────────────────────

    /**
     * Latitude — positive = North, negative = South.
     * Nullable because coordinates may not always be available at creation.
     */
    @Column(precision = 10)
    private Double latitude;

    /**
     * Longitude — positive = East, negative = West.
     */
    @Column(precision = 10)
    private Double longitude;
}
