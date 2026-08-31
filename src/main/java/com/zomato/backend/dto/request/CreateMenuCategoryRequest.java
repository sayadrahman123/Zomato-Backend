package com.zomato.backend.dto.request;

import jakarta.validation.constraints.*;

/**
 * Payload for POST /api/restaurants/{restaurantId}/menu/categories
 */
public record CreateMenuCategoryRequest(

        @NotBlank(message = "Category name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @Size(max = 255, message = "Description must be under 255 characters")
        String description,

        /**
         * Display order within the menu. Lower = appears first.
         * Defaults to 0 if not provided.
         */
        @Min(value = 0, message = "Display order must be 0 or greater")
        Integer displayOrder
) {}
