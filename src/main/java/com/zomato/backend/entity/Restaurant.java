package com.zomato.backend.entity;

import com.zomato.backend.entity.enums.CuisineType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Represents a restaurant registered on the platform.
 *
 * Lifecycle:
 *  - Owner registers → isActive=false, isOpen=false
 *  - Admin approves  → isActive=true
 *  - Owner toggles   → isOpen=true/false  (daily open/close)
 *
 * Rating fields (avgRating, totalRatings) are updated in ReviewService
 * every time a new review is submitted — kept denormalized here
 * so the restaurant listing API doesn't need an aggregate query.
 *
 * Audit timestamps (createdAt, updatedAt) inherited from {@link BaseEntity}.
 */
@Entity
@Table(
    name = "restaurants",
    indexes = {
        @Index(name = "idx_restaurant_owner",   columnList = "owner_id"),
        @Index(name = "idx_restaurant_active",  columnList = "is_active"),
        @Index(name = "idx_restaurant_cuisine", columnList = "cuisine_type")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Restaurant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Basic Info ─────────────────────────────────────────────────────────────

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(length = 150)
    private String email;

    // ── Cuisine ────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "cuisine_type", nullable = false, length = 30)
    private CuisineType cuisineType;

    // ── Location ───────────────────────────────────────────────────────────────

    /**
     * City name — used for filtering restaurants by city.
     * Kept as a plain column (not FK) for simplicity;
     * full address details are in the Address entity (Step 2.2).
     */
    @Column(nullable = false, length = 100)
    private String city;

    // ── Rating (Denormalized) ─────────────────────────────────────────────────

    /**
     * Running average rating (1.0 – 5.0).
     * Recalculated in ReviewService.updateRestaurantAvgRating().
     * precision=3, scale=2 → stores values like 4.35
     */
    @Column(name = "avg_rating",
            nullable = false,
            precision = 3,
            scale = 2)
    @Builder.Default
    private BigDecimal avgRating = BigDecimal.ZERO;

    /**
     * Total number of ratings received.
     * Used together with avgRating to compute the new average on each review.
     */
    @Column(name = "total_ratings", nullable = false)
    @Builder.Default
    private Integer totalRatings = 0;

    // ── Status ─────────────────────────────────────────────────────────────────

    /**
     * Admin-controlled flag.
     * false = pending approval or banned.
     * true  = visible to customers.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = false;

    /**
     * Owner-controlled flag — toggled daily to indicate open/closed.
     * Only meaningful when isActive = true.
     */
    @Column(name = "is_open", nullable = false)
    @Builder.Default
    private Boolean isOpen = false;

    // ── Relationships ──────────────────────────────────────────────────────────

    /**
     * The user who owns this restaurant (must have RESTAURANT_OWNER role).
     *
     * FetchType.LAZY — don't load the full User object unless explicitly needed.
     * RESTRICT on delete — cannot delete a user who owns restaurants
     * (must transfer or delete restaurants first).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "owner_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_restaurant_owner")
    )
    private User owner;

    // Address will be added in Step 2.2 as @OneToOne with Address entity
}
