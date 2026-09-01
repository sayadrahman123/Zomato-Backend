package com.zomato.backend.repository;

import com.zomato.backend.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link Review} entity.
 *
 * Actor perspectives:
 *  - Customer  : submit review, view own reviews
 *  - Public    : view active reviews for a restaurant
 *  - Admin     : moderate (soft-delete) reviews
 *
 * Rating recalculation helpers:
 *  avgRatingForRestaurant + countActiveByRestaurant are used by ReviewService
 *  to recompute Restaurant.avgRating and totalRatings after every change.
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // ── Existence checks ──────────────────────────────────────────────────────

    /** Prevents a customer from reviewing the same order twice. */
    boolean existsByOrderId(Long orderId);

    /** Check if a specific customer already reviewed a specific order. */
    boolean existsByOrderIdAndCustomerId(Long orderId, Long customerId);

    // ── Public restaurant reviews ─────────────────────────────────────────────

    /**
     * Paginated active reviews for a restaurant — shown on the restaurant page.
     * Excludes soft-deleted (isActive=false) reviews.
     */
    Page<Review> findByRestaurantIdAndIsActiveTrueOrderByCreatedAtDesc(
            Long restaurantId, Pageable pageable
    );

    /**
     * Count of active reviews for a restaurant.
     * Used together with avgRatingForRestaurant() for recalculation.
     */
    long countByRestaurantIdAndIsActiveTrue(Long restaurantId);

    /**
     * Running average rating of all active reviews for a restaurant.
     * Returns null if no active reviews exist.
     * Used by ReviewService to recompute Restaurant.avgRating after changes.
     */
    @Query("""
            SELECT AVG(r.rating) FROM Review r
            WHERE r.restaurant.id = :restaurantId
              AND r.isActive = true
            """)
    Double avgRatingForRestaurant(@Param("restaurantId") Long restaurantId);

    // ── Customer queries ──────────────────────────────────────────────────────

    /**
     * Paginated list of reviews written by a specific customer.
     * Only shows active reviews (customer's own still-visible reviews).
     */
    Page<Review> findByCustomerIdAndIsActiveTrueOrderByCreatedAtDesc(
            Long customerId, Pageable pageable
    );

    /**
     * Finds the review a specific customer left for a specific order.
     * Used to fetch and update an existing review.
     */
    Optional<Review> findByOrderIdAndCustomerId(Long orderId, Long customerId);

    /**
     * A specific review by ID — only if it belongs to the given customer.
     * Used as an ownership guard on the update endpoint.
     */
    Optional<Review> findByIdAndCustomerId(Long id, Long customerId);

    // ── Admin queries ─────────────────────────────────────────────────────────

    /**
     * All reviews for a restaurant (active and inactive) — admin moderation view.
     */
    Page<Review> findByRestaurantIdOrderByCreatedAtDesc(
            Long restaurantId, Pageable pageable
    );
}
