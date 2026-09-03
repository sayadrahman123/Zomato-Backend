package com.zomato.backend.service;

import com.zomato.backend.dto.request.SubmitReviewRequest;
import com.zomato.backend.dto.request.UpdateReviewRequest;
import com.zomato.backend.dto.response.ReviewResponse;
import com.zomato.backend.entity.Order;
import com.zomato.backend.entity.Restaurant;
import com.zomato.backend.entity.Review;
import com.zomato.backend.entity.User;
import com.zomato.backend.entity.enums.OrderStatus;
import com.zomato.backend.exception.BusinessException;
import com.zomato.backend.exception.ResourceNotFoundException;
import com.zomato.backend.mapper.ReviewMapper;
import com.zomato.backend.repository.OrderRepository;
import com.zomato.backend.repository.RestaurantRepository;
import com.zomato.backend.repository.ReviewRepository;
import com.zomato.backend.repository.UserRepository;
import com.zomato.backend.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Business logic for restaurant reviews.
 *
 * After every change (submit / update rating / soft-delete / admin toggle),
 * {@link #recalculateRestaurantRating(Long)} is called to keep the
 * denormalized avgRating and totalRatings on Restaurant in sync.
 *
 * Incremental formula:
 *   newAvg   = AVG(all active reviews)   via ReviewRepository.avgRatingForRestaurant()
 *   newCount = COUNT(all active reviews) via ReviewRepository.countByRestaurantIdAndIsActiveTrue()
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository     reviewRepository;
    private final OrderRepository      orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository       userRepository;
    private final ReviewMapper         reviewMapper;

    // ── Submit Review ─────────────────────────────────────────────────────────

    /**
     * Customer submits a review tied to a delivered order.
     *
     * Guards:
     *  1. Order must exist and belong to the customer
     *  2. Order must be DELIVERED
     *  3. No existing review for this order (one per order)
     *
     * After saving, updates Restaurant.avgRating and totalRatings.
     */
    @Transactional
    public ReviewResponse submitReview(Long customerId, SubmitReviewRequest request) {

        // Guard 1: load order + ownership
        Order order = orderRepository.findByIdAndCustomerId(request.orderId(), customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.orderId()));

        // Guard 2: must be delivered
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessException("You can only review an order after it has been delivered.");
        }

        // Guard 3: one review per order
        if (reviewRepository.existsByOrderId(request.orderId())) {
            throw new BusinessException("You have already submitted a review for this order.");
        }

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", customerId));

        Restaurant restaurant = order.getRestaurant();

        Review review = Review.builder()
                .customer(customer)
                .restaurant(restaurant)
                .order(order)
                .rating(request.rating())
                .title(request.title())
                .comment(request.comment())
                .isActive(true)
                .build();

        Review saved = reviewRepository.save(review);
        recalculateRestaurantRating(restaurant.getId());

        log.info("Review submitted: reviewId={}, orderId={}, restaurantId={}, rating={}",
                saved.getId(), request.orderId(), restaurant.getId(), request.rating());

        return reviewMapper.toReviewResponse(saved);
    }

    // ── Update Review ─────────────────────────────────────────────────────────

    /**
     * Customer updates their own review.
     * Only provided (non-null) fields are changed.
     * At least one field must be present.
     */
    @Transactional
    public ReviewResponse updateReview(Long reviewId, Long customerId, UpdateReviewRequest request) {

        if (request.rating() == null && request.title() == null && request.comment() == null) {
            throw new BusinessException("Provide at least one field to update.");
        }

        Review review = reviewRepository.findByIdAndCustomerId(reviewId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));

        if (!review.getIsActive()) {
            throw new BusinessException("This review has been removed and can no longer be edited.");
        }

        boolean ratingChanged = false;
        if (request.rating() != null) {
            review.setRating(request.rating());
            ratingChanged = true;
        }
        if (request.title() != null)   review.setTitle(request.title());
        if (request.comment() != null) review.setComment(request.comment());

        Review saved = reviewRepository.save(review);

        if (ratingChanged) {
            recalculateRestaurantRating(review.getRestaurant().getId());
        }

        return reviewMapper.toReviewResponse(saved);
    }

    // ── Delete (Soft) Review ──────────────────────────────────────────────────

    /**
     * Customer soft-deletes their own review (sets isActive=false).
     * Recalculates restaurant rating after removal.
     */
    @Transactional
    public void deleteMyReview(Long reviewId, Long customerId) {
        Review review = reviewRepository.findByIdAndCustomerId(reviewId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));

        if (!review.getIsActive()) {
            throw new BusinessException("This review is already removed.");
        }

        review.setIsActive(false);
        reviewRepository.save(review);
        recalculateRestaurantRating(review.getRestaurant().getId());

        log.info("Review soft-deleted: reviewId={} by customerId={}", reviewId, customerId);
    }

    // ── Admin Moderation ──────────────────────────────────────────────────────

    /**
     * Admin toggles a review's visibility.
     * Flips isActive: true→false (hide) or false→true (restore).
     * Always recalculates restaurant rating.
     */
    @Transactional
    public ReviewResponse adminToggleReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));

        review.setIsActive(!review.getIsActive());
        Review saved = reviewRepository.save(review);
        recalculateRestaurantRating(review.getRestaurant().getId());

        log.info("Review toggled by admin: reviewId={}, isActive={}", reviewId, saved.getIsActive());
        return reviewMapper.toReviewResponse(saved);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Paginated active reviews for a restaurant — public listing.
     */
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getRestaurantReviews(Long restaurantId, int page, int size) {
        Pageable pageable = PaginationUtils.createPageable(page, size, 20, Sort.by("createdAt").descending());
        return reviewRepository
                .findByRestaurantIdAndIsActiveTrueOrderByCreatedAtDesc(restaurantId, pageable)
                .map(reviewMapper::toReviewResponse);
    }

    /**
     * Paginated reviews written by the authenticated customer.
     */
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getMyReviews(Long customerId, int page, int size) {
        Pageable pageable = PaginationUtils.createPageable(page, size, 20, Sort.by("createdAt").descending());
        return reviewRepository
                .findByCustomerIdAndIsActiveTrueOrderByCreatedAtDesc(customerId, pageable)
                .map(reviewMapper::toReviewResponse);
    }

    /**
     * Admin: all reviews for a restaurant (active + inactive).
     */
    @Transactional(readOnly = true)
    public Page<ReviewResponse> getAllRestaurantReviews(Long restaurantId, int page, int size) {
        Pageable pageable = PaginationUtils.createPageable(page, size, 20, Sort.by("createdAt").descending());
        return reviewRepository
                .findByRestaurantIdOrderByCreatedAtDesc(restaurantId, pageable)
                .map(reviewMapper::toReviewResponse);
    }

    // ── Rating Recalculation ──────────────────────────────────────────────────

    /**
     * Recomputes and persists the restaurant's avgRating and totalRatings
     * from the current set of active reviews.
     *
     * Called after every review mutation (submit, update rating,
     * soft-delete, admin toggle).
     *
     * Uses two lightweight aggregate queries instead of loading all reviews.
     */
    private void recalculateRestaurantRating(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        Double avg   = reviewRepository.avgRatingForRestaurant(restaurantId);
        long   count = reviewRepository.countByRestaurantIdAndIsActiveTrue(restaurantId);

        BigDecimal newAvg = (avg != null)
                ? BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        restaurant.setAvgRating(newAvg);
        restaurant.setTotalRatings((int) count);
        restaurantRepository.save(restaurant);

        log.debug("Rating recalculated: restaurantId={}, avg={}, count={}", restaurantId, newAvg, count);
    }
}
