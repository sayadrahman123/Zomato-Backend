package com.zomato.backend.dto.request;

import com.zomato.backend.entity.enums.FoodType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Payload for POST /api/restaurants/{restaurantId}/menu/items
 */
public record CreateMenuItemRequest(

        @NotBlank(message = "Item name is required")
        @Size(min = 2, max = 150, message = "Name must be between 2 and 150 characters")
        String name,

        @Size(max = 2000, message = "Description must be under 2000 characters")
        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.01", message = "Price must be greater than 0")
        @Digits(integer = 8, fraction = 2, message = "Price format is invalid (max 8 digits, 2 decimal places)")
        BigDecimal price,

        /**
         * Optional discounted/sale price.
         * Must be less than price — validated in MenuService.
         */
        @DecimalMin(value = "0.01", message = "Discounted price must be greater than 0")
        @Digits(integer = 8, fraction = 2, message = "Discounted price format is invalid")
        BigDecimal discountedPrice,

        @NotNull(message = "Food type is required")
        FoodType foodType,

        @NotNull(message = "Category ID is required")
        Long categoryId,

        @Min(value = 0, message = "Display order must be 0 or greater")
        Integer displayOrder
) {}
