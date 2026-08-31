package com.zomato.backend.dto.request;

import com.zomato.backend.entity.enums.FoodType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Payload for PUT /api/restaurants/{restaurantId}/menu/items/{id}
 * All fields are optional — only non-null values are applied.
 */
public record UpdateMenuItemRequest(

        @Size(min = 2, max = 150, message = "Name must be between 2 and 150 characters")
        String name,

        @Size(max = 2000, message = "Description must be under 2000 characters")
        String description,

        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        @Digits(integer = 8, fraction = 2, message = "Price format is invalid")
        BigDecimal price,

        /**
         * Set to null explicitly in JSON to remove an existing discount.
         * The service treats null as "keep existing" unless removeDiscount=true.
         */
        @DecimalMin(value = "0.01", message = "Discounted price must be greater than 0")
        @Digits(integer = 8, fraction = 2, message = "Discounted price format is invalid")
        BigDecimal discountedPrice,

        /** Send true to remove existing discount (sets discountedPrice = null). */
        Boolean removeDiscount,

        FoodType foodType,

        /** Move item to a different category. */
        Long categoryId,

        @Min(value = 0, message = "Display order must be 0 or greater")
        Integer displayOrder,

        Boolean isAvailable,    // null = don't change
        Boolean isActive        // null = don't change
) {}
