package com.zomato.backend.dto.response;

/**
 * Flat category response — used in owner management views.
 * For the customer-facing menu (categories with nested items),
 * use {@link MenuCategoryWithItemsResponse}.
 */
public record MenuCategoryResponse(
        Long    id,
        String  name,
        String  description,
        Integer displayOrder,
        Boolean isActive,
        Long    restaurantId
) {}
