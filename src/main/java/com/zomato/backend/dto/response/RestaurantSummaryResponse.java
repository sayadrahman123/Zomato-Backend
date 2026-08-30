package com.zomato.backend.dto.response;

import com.zomato.backend.entity.enums.CuisineType;

import java.math.BigDecimal;

/**
 * Compact restaurant card — used in listing and search results.
 *
 * Deliberately minimal to keep list API responses lightweight.
 * The customer only needs enough info to decide which restaurant to click.
 * Full details (address, owner, timestamps) are in {@link RestaurantResponse}.
 */
public record RestaurantSummaryResponse(
        Long        id,
        String      name,
        CuisineType cuisineType,
        String      city,
        BigDecimal  avgRating,
        Integer     totalRatings,
        Boolean     isOpen
) {}
