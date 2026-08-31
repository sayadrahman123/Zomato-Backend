package com.zomato.backend.entity;

import com.zomato.backend.entity.enums.VehicleType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Delivery partner profile — extends the platform user account
 * with delivery-specific fields.
 *
 * Relationship to User:
 *   OneToOne — one User account ↔ one DeliveryPartner profile.
 *   The User entity stores authentication and contact details;
 *   this entity stores delivery-specific state (vehicle, location, stats).
 *
 * Lifecycle flags:
 *   isVerified → Admin must verify before partner can accept deliveries.
 *   isAvailable → Partner toggles this when they are ready to receive jobs.
 *   isActive → Platform-level account suspension (admin-controlled).
 *
 * Location:
 *   currentLatitude / currentLongitude are updated in real-time by the
 *   partner's mobile app (typically via a PATCH endpoint).
 *   These are stored here for order assignment queries.
 *
 * Audit timestamps (createdAt, updatedAt) inherited from {@link BaseEntity}.
 */
@Entity
@Table(
    name = "delivery_partners",
    indexes = {
        @Index(name = "idx_dp_user",       columnList = "user_id",      unique = true),
        @Index(name = "idx_dp_available",  columnList = "is_available"),
        @Index(name = "idx_dp_verified",   columnList = "is_verified")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryPartner extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── User Account ──────────────────────────────────────────────────────────

    /**
     * The user account for authentication, name, email, and phone.
     * CascadeType not set — User has its own lifecycle.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_delivery_partner_user")
    )
    private User user;

    // ── Vehicle ───────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 15)
    private VehicleType vehicleType;

    /**
     * Vehicle registration number (number plate).
     * Example: "MH02AB1234"
     */
    @Column(name = "vehicle_number", nullable = false, length = 20)
    private String vehicleNumber;

    // ── Real-time Location ────────────────────────────────────────────────────

    /**
     * Last known GPS latitude — updated by the partner's app.
     * Null if partner has never shared location.
     */
    @Column(name = "current_latitude")
    private Double currentLatitude;

    @Column(name = "current_longitude")
    private Double currentLongitude;

    // ── Status Flags ──────────────────────────────────────────────────────────

    /**
     * Admin must verify documents (license, registration) before
     * the partner can accept delivery jobs.
     * Default: false — newly registered partners are unverified.
     */
    @Column(name = "is_verified", nullable = false)
    @Builder.Default
    private Boolean isVerified = false;

    /**
     * Partner goes online/offline via their app.
     * Only verified partners can set this to true.
     * Default: false — offline on registration.
     */
    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private Boolean isAvailable = false;

    /**
     * Platform-level account status. Admins use this to suspend a partner.
     * Default: true — account is active.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // ── Statistics ─────────────────────────────────────────────────────────────

    /**
     * Total successfully completed deliveries.
     * Incremented by OrderService when an order reaches DELIVERED status.
     */
    @Column(name = "total_deliveries", nullable = false)
    @Builder.Default
    private Integer totalDeliveries = 0;

    /**
     * Average rating given by customers (1.0 – 5.0).
     * Recomputed after each new review in a future ReviewService.
     * Null if no ratings yet.
     */
    @Column(name = "average_rating")
    private Double averageRating;
}
