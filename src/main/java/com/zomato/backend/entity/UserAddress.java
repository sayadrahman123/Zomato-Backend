package com.zomato.backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A customer's saved delivery address (e.g. Home, Office, Other).
 *
 * Why a separate entity from {@link Address}?
 *  - Address is scoped to restaurants (has its own lifecycle, no user FK).
 *  - UserAddress belongs to a User, has a label, an isDefault flag,
 *    and can be soft-managed independently.
 *
 * isDefault:
 *  At most ONE address per user can be the default.
 *  UserAddressService enforces this by clearing other defaults
 *  whenever a new one is set — the DB does NOT have a unique constraint
 *  on (user_id, is_default=true) because JPA doesn't support partial indexes
 *  portably. The service is the single point of truth.
 *
 * Audit timestamps (createdAt, updatedAt) inherited from {@link BaseEntity}.
 */
@Entity
@Table(
    name = "user_addresses",
    indexes = {
        @Index(name = "idx_user_address_user", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAddress extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Relationship ──────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_user_address_user")
    )
    private User user;

    // ── Label ─────────────────────────────────────────────────────────────────

    /**
     * Short name to identify the address — e.g. "Home", "Office", "Parent's House".
     * Shown in the address picker during checkout.
     */
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String label = "Home";

    // ── Address Fields ────────────────────────────────────────────────────────

    @Column(nullable = false, length = 255)
    private String street;

    /**
     * Locality / Area — e.g. "Koramangala", "Bandra West".
     */
    @Column(length = 100)
    private String area;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(nullable = false, length = 100)
    private String state;

    /**
     * Indian PIN code — stored as String to preserve leading zeros.
     */
    @Column(nullable = false, length = 10)
    private String pincode;

    // ── Geo Coordinates ───────────────────────────────────────────────────────

    /**
     * GPS latitude — used for delivery partner radius search and map display.
     * Nullable if the customer doesn't grant location permission.
     */
    @Column
    private Double latitude;

    @Column
    private Double longitude;

    // ── Default Flag ──────────────────────────────────────────────────────────

    /**
     * When true, this address is pre-selected at checkout.
     * At most one address per user should have this set to true.
     * Enforced by UserAddressService (not by a DB unique partial index).
     * Default: false — customer explicitly marks an address as default.
     */
    @Column(name = "is_default", nullable = false)
    @Builder.Default
    private Boolean isDefault = false;
}
