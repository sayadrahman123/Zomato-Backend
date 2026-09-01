package com.zomato.backend.controller;

import com.zomato.backend.dto.request.SubmitReviewRequest;
import com.zomato.backend.dto.request.UpdateReviewRequest;
import com.zomato.backend.dto.response.ApiResponse;
import com.zomato.backend.dto.response.ReviewResponse;
import com.zomato.backend.service.ReviewService;
import com.zomato.backend.util.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Review lifecycle endpoints.
 *
 * Base path: /api/reviews
 *
 * Access rules:
 *   PUBLIC    : GET reviews for a restaurant (no auth needed)
 *   CUSTOMER  : submit, update, delete own review + view own reviews
 *   ADMIN     : moderate (toggle visibility) + view all reviews
 */
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reviews", description = "Submit, update, moderate and view restaurant reviews")
public class ReviewController {

    private final ReviewService reviewService;
    private final AuthUtils     authUtils;

    // ══════════════════════════════════════════════════════════════════════════
    // PUBLIC
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "Get reviews for a restaurant",
        description = "Returns paginated active reviews for a restaurant. No authentication required."
    )
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getRestaurantReviews(
            @PathVariable Long restaurantId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<ReviewResponse> reviews = reviewService.getRestaurantReviews(restaurantId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Reviews fetched successfully", reviews));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CUSTOMER
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "Submit a review",
        description = "Customer submits a review for a delivered order. " +
                      "One review per order. Order must be in DELIVERED status."
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    public ResponseEntity<ApiResponse<ReviewResponse>> submitReview(
            @Valid @RequestBody SubmitReviewRequest request,
            HttpServletRequest httpRequest
    ) {
        Long customerId = authUtils.getCurrentUserId(httpRequest);
        ReviewResponse review = reviewService.submitReview(customerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Review submitted successfully", review));
    }

    @Operation(
        summary     = "Update my review",
        description = "Customer updates their own review. " +
                      "Only provided fields are updated (PATCH semantics). " +
                      "Cannot edit a review that has been hidden by an admin."
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    @PatchMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<ReviewResponse>> updateReview(
            @PathVariable Long reviewId,
            @Valid @RequestBody UpdateReviewRequest request,
            HttpServletRequest httpRequest
    ) {
        Long customerId = authUtils.getCurrentUserId(httpRequest);
        ReviewResponse review = reviewService.updateReview(reviewId, customerId, request);
        return ResponseEntity.ok(ApiResponse.success("Review updated successfully", review));
    }

    @Operation(
        summary     = "Delete my review",
        description = "Customer soft-deletes their own review. " +
                      "The review is hidden from public listing and the restaurant's rating is recalculated."
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<ApiResponse<Void>> deleteMyReview(
            @PathVariable Long reviewId,
            HttpServletRequest httpRequest
    ) {
        Long customerId = authUtils.getCurrentUserId(httpRequest);
        reviewService.deleteMyReview(reviewId, customerId);
        return ResponseEntity.ok(ApiResponse.success("Review deleted successfully"));
    }

    @Operation(
        summary     = "Get my reviews",
        description = "Returns the authenticated customer's own active reviews, newest first."
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getMyReviews(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest
    ) {
        Long customerId = authUtils.getCurrentUserId(httpRequest);
        Page<ReviewResponse> reviews = reviewService.getMyReviews(customerId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Your reviews fetched successfully", reviews));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "Toggle review visibility (admin)",
        description = "Admin hides or restores a review. " +
                      "Flips isActive: true→false (hide abusive review) or false→true (restore). " +
                      "Restaurant rating is recalculated automatically."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{reviewId}/toggle")
    public ResponseEntity<ApiResponse<ReviewResponse>> toggleReview(
            @PathVariable Long reviewId
    ) {
        ReviewResponse review = reviewService.adminToggleReview(reviewId);
        String msg = Boolean.TRUE.equals(review.isActive())
                ? "Review restored successfully"
                : "Review hidden successfully";
        return ResponseEntity.ok(ApiResponse.success(msg, review));
    }

    @Operation(
        summary     = "Get all reviews for a restaurant (admin)",
        description = "Returns all reviews including hidden ones — for admin moderation."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/restaurant/{restaurantId}")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getAllRestaurantReviews(
            @PathVariable Long restaurantId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<ReviewResponse> reviews =
                reviewService.getAllRestaurantReviews(restaurantId, page, size);
        return ResponseEntity.ok(ApiResponse.success("All reviews fetched", reviews));
    }
}
