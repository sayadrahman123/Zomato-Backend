package com.zomato.backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A customer's review of a restaurant, tied to a verified order.
 *
 * Design decisions:
 *
 * 1. One review per order (UNIQUE on order_id):
 *    A customer can only review a restaurant once per order.
 *    This prevents spam and ensures reviews reflect real experiences.
 *
 * 2. Verified-purchase gate:
 *    Reviews are linked to a completed Order. The service enforces that
 *    the order must be in DELIVERED status before a review is allowed.
 *
 * 3. Denormalized rating fields on Restaurant:
 *    Restaurant.avgRating and totalRatings are recomputed by ReviewService
 *    after each insert/update/soft-delete — no aggregate query needed at
 *    read time. Formula: newAvg = ((oldAvg × oldCount) + newRating) / (oldCount + 1)
 *
 * 4. Soft-delete via isActive:
 *    Admins can hide abusive reviews without losing the data.
 *    Hidden reviews are excluded from the public listing and avg recalculation.
 *
 * Audit timestamps (createdAt, updatedAt) inherited from {@link BaseEntity}.
 */
@Entity
@Table(
    name = "reviews",
    indexes = {
        @Index(name = "idx_review_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_review_customer",   columnList = "customer_id"),
        @Index(name = "idx_review_order",      columnList = "order_id", unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Relationships ──────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "customer_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_review_customer")
    )
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "restaurant_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_review_restaurant")
    )
    private Restaurant restaurant;

    /**
     * The completed order this review is for.
     * UNIQUE — one review per order enforced at DB level too.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "order_id",
        nullable = false,
        unique = true,
        foreignKey = @ForeignKey(name = "fk_review_order")
    )
    private Order order;

    // ── Review Content ────────────────────────────────────────────────────────

    /**
     * Star rating: 1 (worst) to 5 (best).
     * Validated in ReviewService — not using @Column check constraint
     * for broader DB compatibility.
     */
    @Column(nullable = false)
    private Integer rating;

    /**
     * Optional short heading — e.g. "Amazing biryani!"
     */
    @Column(length = 150)
    private String title;

    /**
     * Full review text. Nullable — a star rating alone is valid.
     */
    @Column(columnDefinition = "TEXT")
    private String comment;

    // ── Moderation ────────────────────────────────────────────────────────────

    /**
     * Admin soft-delete: false = hidden from public listing and excluded
     * from the restaurant's average rating recalculation.
     * Default: true (visible).
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
