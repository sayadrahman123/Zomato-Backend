package com.zomato.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Payload for PUT /api/restaurants/{restaurantId}/menu/categories/{id}
 * All fields are optional — only non-null values are applied.
 */
public record UpdateMenuCategoryRequest(

        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @Size(max = 255, message = "Description must be under 255 characters")
        String description,

        @Min(value = 0, message = "Display order must be 0 or greater")
        Integer displayOrder,

        Boolean isActive        // null = don't change active status
) {}
