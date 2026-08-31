package com.zomato.backend.dto.response;

import com.zomato.backend.entity.enums.FoodType;

import java.math.BigDecimal;

/**
 * Full menu item details.
 *
 * effectivePrice: the price the customer actually pays.
 *   = discountedPrice if set, otherwise = price.
 *
 * hasDiscount: true when discountedPrice < price.
 *   Client can use this to show the strikethrough original price.
 */
public record MenuItemResponse(
        Long        id,
        String      name,
        String      description,
        BigDecimal  price,
        BigDecimal  discountedPrice,    // null = no discount
        BigDecimal  effectivePrice,     // price customer actually pays
        Boolean     hasDiscount,
        FoodType    foodType,
        Integer     displayOrder,
        Boolean     isActive,
        Boolean     isAvailable,
        Long        categoryId,
        String      categoryName,
        Long        restaurantId
) {}
