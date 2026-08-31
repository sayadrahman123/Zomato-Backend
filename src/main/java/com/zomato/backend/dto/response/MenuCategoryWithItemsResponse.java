package com.zomato.backend.dto.response;

import java.util.List;

/**
 * A menu category with its nested items — used for the full menu page.
 *
 * GET /api/restaurants/{id}/menu returns a List of this DTO,
 * giving the client a complete structured menu in a single API call.
 *
 * Example structure:
 * <pre>
 * [
 *   { "id": 1, "name": "Starters",   "items": [ {...}, {...} ] },
 *   { "id": 2, "name": "Main Course","items": [ {...}, {...} ] },
 *   { "id": 3, "name": "Desserts",   "items": [ {...} ] }
 * ]
 * </pre>
 */
public record MenuCategoryWithItemsResponse(
        Long                   id,
        String                 name,
        String                 description,
        Integer                displayOrder,
        List<MenuItemResponse> items
) {}
