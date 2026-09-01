package com.zomato.backend.dto.request;

import jakarta.validation.constraints.*;

/**
 * Payload for POST /api/reviews
 *
 * orderId links the review to a specific delivered order (verified-purchase gate).
 * title and comment are optional — a star rating alone is a valid review.
 */
public record SubmitReviewRequest(

        @NotNull(message = "Order ID is required")
        Long orderId,

        @NotNull(message = "Rating is required")
        @Min(value = 1, message = "Rating must be at least 1")
        @Max(value = 5, message = "Rating must be at most 5")
        Integer rating,

        @Size(max = 150, message = "Title must be under 150 characters")
        String title,

        @Size(max = 2000, message = "Comment must be under 2000 characters")
        String comment
) {}
