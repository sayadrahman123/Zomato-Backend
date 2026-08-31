package com.zomato.backend.mapper;

import com.zomato.backend.dto.response.MenuCategoryResponse;
import com.zomato.backend.dto.response.MenuCategoryWithItemsResponse;
import com.zomato.backend.dto.response.MenuItemResponse;
import com.zomato.backend.entity.MenuCategory;
import com.zomato.backend.entity.MenuItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * Manual mapper between menu entities and their DTOs.
 */
@Component
public class MenuMapper {

    // ── MenuCategory ──────────────────────────────────────────────────────────

    /**
     * Flat category response — for owner management views.
     */
    public MenuCategoryResponse toCategoryResponse(MenuCategory category) {
        if (category == null) return null;

        return new MenuCategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getDisplayOrder(),
                category.getIsActive(),
                category.getRestaurant() != null ? category.getRestaurant().getId() : null
        );
    }

    /**
     * Category with nested items — for the public menu page.
     *
     * @param category the category entity
     * @param items    pre-loaded items for this category (from MenuItemRepository)
     */
    public MenuCategoryWithItemsResponse toCategoryWithItemsResponse(
            MenuCategory category,
            List<MenuItem> items
    ) {
        if (category == null) return null;

        List<MenuItemResponse> itemResponses = (items != null)
                ? items.stream().map(this::toItemResponse).toList()
                : Collections.emptyList();

        return new MenuCategoryWithItemsResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getDisplayOrder(),
                itemResponses
        );
    }

    // ── MenuItem ──────────────────────────────────────────────────────────────

    /**
     * Maps a MenuItem to its response DTO.
     *
     * Computes two derived fields:
     *  - effectivePrice: what the customer actually pays
     *  - hasDiscount: whether to show the strikethrough original price
     */
    public MenuItemResponse toItemResponse(MenuItem item) {
        if (item == null) return null;

        BigDecimal discounted  = item.getDiscountedPrice();
        boolean    hasDiscount = discounted != null
                && discounted.compareTo(item.getPrice()) < 0;
        BigDecimal effective   = hasDiscount ? discounted : item.getPrice();

        return new MenuItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getPrice(),
                discounted,
                effective,
                hasDiscount,
                item.getFoodType(),
                item.getDisplayOrder(),
                item.getIsActive(),
                item.getIsAvailable(),
                item.getCategory() != null ? item.getCategory().getId()   : null,
                item.getCategory() != null ? item.getCategory().getName() : null,
                item.getRestaurant() != null ? item.getRestaurant().getId() : null
        );
    }

    /**
     * Convenience overload — maps a list of items.
     */
    public List<MenuItemResponse> toItemResponseList(List<MenuItem> items) {
        if (items == null) return Collections.emptyList();
        return items.stream().map(this::toItemResponse).toList();
    }
}
