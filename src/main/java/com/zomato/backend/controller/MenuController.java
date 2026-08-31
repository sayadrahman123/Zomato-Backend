package com.zomato.backend.controller;

import com.zomato.backend.dto.request.*;
import com.zomato.backend.dto.response.*;
import com.zomato.backend.entity.enums.FoodType;
import com.zomato.backend.service.MenuService;
import com.zomato.backend.util.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * All menu-related endpoints, nested under a restaurant.
 *
 * Base path: /api/restaurants/{restaurantId}/menu
 *
 * Access rules:
 *   GET  (public menu, search, food-type filter) → open, no token needed
 *   POST / PUT / DELETE / PATCH                  → RESTAURANT_OWNER only
 *   GET  /categories (owner dashboard view)      → RESTAURANT_OWNER only
 */
@RestController
@RequestMapping("/api/restaurants/{restaurantId}/menu")
@RequiredArgsConstructor
@Tag(name = "Menu", description = "Menu category and item management + public menu browsing")
public class MenuController {

    private final MenuService menuService;
    private final AuthUtils   authUtils;

    // ══════════════════════════════════════════════════════════════════════════
    // PUBLIC ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "Get full restaurant menu",
        description = "Returns all active categories with their available items, " +
                      "sorted by displayOrder. Result is cached in Redis."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<List<MenuCategoryWithItemsResponse>>> getFullMenu(
            @PathVariable Long restaurantId
    ) {
        List<MenuCategoryWithItemsResponse> menu = menuService.getFullMenu(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Menu fetched successfully", menu));
    }

    @Operation(
        summary     = "Search menu items",
        description = "Keyword search across item name and description. " +
                      "Only returns active + available items."
    )
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<MenuItemResponse>>> searchItems(
            @PathVariable Long restaurantId,
            @RequestParam String keyword
    ) {
        List<MenuItemResponse> results = menuService.searchItems(restaurantId, keyword);
        return ResponseEntity.ok(ApiResponse.success("Search results", results));
    }

    @Operation(
        summary     = "Filter items by food type",
        description = "Returns all available items filtered by VEG / NON_VEG / EGG / VEGAN. " +
                      "Used for the 'Veg only' toggle."
    )
    @GetMapping("/items")
    public ResponseEntity<ApiResponse<List<MenuItemResponse>>> getItemsByFoodType(
            @PathVariable Long restaurantId,
            @RequestParam FoodType foodType
    ) {
        List<MenuItemResponse> items = menuService.getItemsByFoodType(restaurantId, foodType);
        return ResponseEntity.ok(ApiResponse.success("Items fetched successfully", items));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CATEGORY ENDPOINTS — OWNER ONLY
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "Get all categories (owner view)",
        description = "Returns all categories including inactive ones. " +
                      "For the owner's menu management dashboard."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<MenuCategoryResponse>>> getCategoriesForOwner(
            @PathVariable Long restaurantId,
            HttpServletRequest httpRequest
    ) {
        Long ownerId = authUtils.getCurrentUserId(httpRequest);
        List<MenuCategoryResponse> categories =
                menuService.getCategoriesForOwner(restaurantId, ownerId);
        return ResponseEntity.ok(ApiResponse.success("Categories fetched", categories));
    }

    @Operation(
        summary     = "Add a menu category",
        description = "Creates a new category (e.g., Starters, Main Course). " +
                      "Evicts the menu cache."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<MenuCategoryResponse>> createCategory(
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateMenuCategoryRequest request,
            HttpServletRequest httpRequest
    ) {
        Long ownerId = authUtils.getCurrentUserId(httpRequest);
        MenuCategoryResponse response = menuService.createCategory(request, restaurantId, ownerId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", response));
    }

    @Operation(
        summary     = "Update a menu category",
        description = "Partial update — only provided fields are applied. Evicts menu cache."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @PutMapping("/categories/{categoryId}")
    public ResponseEntity<ApiResponse<MenuCategoryResponse>> updateCategory(
            @PathVariable Long restaurantId,
            @PathVariable Long categoryId,
            @Valid @RequestBody UpdateMenuCategoryRequest request,
            HttpServletRequest httpRequest
    ) {
        Long ownerId = authUtils.getCurrentUserId(httpRequest);
        MenuCategoryResponse response =
                menuService.updateCategory(categoryId, request, restaurantId, ownerId);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", response));
    }

    @Operation(
        summary     = "Delete (deactivate) a menu category",
        description = "Soft-deletes the category. Items within it are preserved. Evicts menu cache."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @DeleteMapping("/categories/{categoryId}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(
            @PathVariable Long restaurantId,
            @PathVariable Long categoryId,
            HttpServletRequest httpRequest
    ) {
        Long ownerId = authUtils.getCurrentUserId(httpRequest);
        menuService.deleteCategory(categoryId, restaurantId, ownerId);
        return ResponseEntity.ok(ApiResponse.success("Category deactivated successfully"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ITEM ENDPOINTS — OWNER ONLY
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "Add a menu item",
        description = "Creates a new item under the specified category. " +
                      "discountedPrice must be less than price. Evicts menu cache."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<MenuItemResponse>> createItem(
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateMenuItemRequest request,
            HttpServletRequest httpRequest
    ) {
        Long ownerId = authUtils.getCurrentUserId(httpRequest);
        MenuItemResponse response = menuService.createItem(request, restaurantId, ownerId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Menu item created successfully", response));
    }

    @Operation(
        summary     = "Update a menu item",
        description = "Partial update. Send removeDiscount=true to clear an existing discount. " +
                      "Evicts menu cache."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<MenuItemResponse>> updateItem(
            @PathVariable Long restaurantId,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateMenuItemRequest request,
            HttpServletRequest httpRequest
    ) {
        Long ownerId = authUtils.getCurrentUserId(httpRequest);
        MenuItemResponse response = menuService.updateItem(itemId, request, restaurantId, ownerId);
        return ResponseEntity.ok(ApiResponse.success("Menu item updated successfully", response));
    }

    @Operation(
        summary     = "Toggle item availability",
        description = "Flips isAvailable — use for temporary out-of-stock situations. " +
                      "Evicts menu cache."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @PatchMapping("/items/{itemId}/toggle-availability")
    public ResponseEntity<ApiResponse<MenuItemResponse>> toggleItemAvailability(
            @PathVariable Long restaurantId,
            @PathVariable Long itemId,
            HttpServletRequest httpRequest
    ) {
        Long ownerId = authUtils.getCurrentUserId(httpRequest);
        MenuItemResponse response =
                menuService.toggleItemAvailability(itemId, restaurantId, ownerId);
        return ResponseEntity.ok(ApiResponse.success(
                "Item is now " + (response.isAvailable() ? "AVAILABLE" : "UNAVAILABLE"), response
        ));
    }

    @Operation(
        summary     = "Delete (deactivate) a menu item",
        description = "Soft-deletes the item. Historical order data is preserved. Evicts menu cache."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteItem(
            @PathVariable Long restaurantId,
            @PathVariable Long itemId,
            HttpServletRequest httpRequest
    ) {
        Long ownerId = authUtils.getCurrentUserId(httpRequest);
        menuService.deleteItem(itemId, restaurantId, ownerId);
        return ResponseEntity.ok(ApiResponse.success("Menu item deactivated successfully"));
    }
}
