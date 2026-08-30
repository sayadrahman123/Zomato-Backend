package com.zomato.backend.dto.response;

import com.zomato.backend.entity.enums.CuisineType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Full restaurant details — returned to the restaurant owner and admin.
 *
 * Contains the complete address, owner info, approval status, and timestamps.
 * NOT used for the public customer listing (use {@link RestaurantSummaryResponse} instead).
 */
public record RestaurantResponse(
        Long            id,
        String          name,
        String          description,
        String          phone,
        String          email,
        CuisineType     cuisineType,
        String          city,
        BigDecimal      avgRating,
        Integer         totalRatings,
        Boolean         isActive,
        Boolean         isOpen,
        AddressResponse address,
        Long            ownerId,
        String          ownerName,
        LocalDateTime   createdAt
) {}
