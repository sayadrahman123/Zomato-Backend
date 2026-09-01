package com.zomato.backend.mapper;

import com.zomato.backend.dto.response.ReviewResponse;
import com.zomato.backend.entity.Review;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

    public ReviewResponse toReviewResponse(Review review) {
        if (review == null) return null;

        return new ReviewResponse(
                review.getId(),
                review.getCustomer() != null ? review.getCustomer().getId()   : null,
                review.getCustomer() != null ? review.getCustomer().getName() : null,
                review.getRestaurant() != null ? review.getRestaurant().getId() : null,
                review.getOrder() != null ? review.getOrder().getId() : null,
                review.getRating(),
                review.getTitle(),
                review.getComment(),
                review.getIsActive(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
