package com.zomato.backend.service;

import com.zomato.backend.dto.request.CreateMenuCategoryRequest;
import com.zomato.backend.dto.request.CreateMenuItemRequest;
import com.zomato.backend.dto.request.UpdateMenuCategoryRequest;
import com.zomato.backend.dto.request.UpdateMenuItemRequest;
import com.zomato.backend.dto.response.MenuCategoryResponse;
import com.zomato.backend.dto.response.MenuCategoryWithItemsResponse;
import com.zomato.backend.dto.response.MenuItemResponse;
import com.zomato.backend.entity.MenuCategory;
import com.zomato.backend.entity.MenuItem;
import com.zomato.backend.entity.Restaurant;
import com.zomato.backend.entity.enums.FoodType;
import com.zomato.backend.exception.BusinessException;
import com.zomato.backend.exception.ResourceNotFoundException;
import com.zomato.backend.mapper.MenuMapper;
import com.zomato.backend.repository.MenuCategoryRepository;
import com.zomato.backend.repository.MenuItemRepository;
import com.zomato.backend.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Handles all menu operations — categories and items.
 *
 * Ownership guard:
 *   Every write operation first checks that the authenticated owner
 *   actually owns the target restaurant (via existsByIdAndOwnerId).
 *
 * Caching:
 *   The full menu (getFullMenu) is cached under the "menus" cache key.
 *   Any category/item mutation evicts the menu cache for that restaurant
 *   so the next GET re-builds it from the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuCategoryRepository categoryRepository;
    private final MenuItemRepository     itemRepository;
    private final RestaurantRepository   restaurantRepository;
    private final MenuMapper             menuMapper;

    // ══════════════════════════════════════════════════════════════════════════
    // CATEGORY OPERATIONS
    // ══════════════════════════════════════════════════════════════════════════

    // ── Create Category ───────────────────────────────────────────────────────

    /**
     * Creates a new menu category for a restaurant.
     *
     * @param request      category name, description, displayOrder
     * @param restaurantId the target restaurant
     * @param ownerId      JWT-extracted user ID
     * @return the saved category as DTO
     * @throws BusinessException if caller doesn't own the restaurant
     * @throws BusinessException if a category with the same name already exists
     */
    @CacheEvict(value = "menus", key = "#restaurantId")
    @Transactional
    public MenuCategoryResponse createCategory(
            CreateMenuCategoryRequest request,
            Long restaurantId,
            Long ownerId
    ) {
        Restaurant restaurant = getRestaurantOwnedBy(restaurantId, ownerId);

        // Guard: no duplicate category names per restaurant
        if (categoryRepository.existsByRestaurantIdAndNameIgnoreCase(
                restaurantId, request.name())) {
            throw new BusinessException(
                    "A category named '" + request.name() + "' already exists in this menu.");
        }

        MenuCategory category = MenuCategory.builder()
                .name(request.name().trim())
                .description(request.description())
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .restaurant(restaurant)
                .isActive(true)
                .build();

        MenuCategory saved = categoryRepository.save(category);
        log.info("Category created: id={}, name='{}', restaurantId={}", saved.getId(), saved.getName(), restaurantId);
        return menuMapper.toCategoryResponse(saved);
    }

    // ── Update Category ───────────────────────────────────────────────────────

    /**
     * Partially updates a menu category.
     * Only non-null fields in the request are applied.
     */
    @CacheEvict(value = "menus", key = "#restaurantId")
    @Transactional
    public MenuCategoryResponse updateCategory(
            Long categoryId,
            UpdateMenuCategoryRequest request,
            Long restaurantId,
            Long ownerId
    ) {
        getRestaurantOwnedBy(restaurantId, ownerId);    // ownership check
        MenuCategory category = getCategoryBelongingToRestaurant(categoryId, restaurantId);

        if (StringUtils.hasText(request.name())) {
            // Check duplicate only if the name is actually changing
            if (!request.name().equalsIgnoreCase(category.getName())
                    && categoryRepository.existsByRestaurantIdAndNameIgnoreCase(
                            restaurantId, request.name())) {
                throw new BusinessException(
                        "A category named '" + request.name() + "' already exists in this menu.");
            }
            category.setName(request.name().trim());
        }

        if (StringUtils.hasText(request.description())) category.setDescription(request.description());
        if (request.displayOrder() != null) category.setDisplayOrder(request.displayOrder());
        if (request.isActive()     != null) category.setIsActive(request.isActive());

        MenuCategory updated = categoryRepository.save(category);
        return menuMapper.toCategoryResponse(updated);
    }

    // ── Delete Category (soft) ────────────────────────────────────────────────

    /**
     * Soft-deletes a category by setting isActive=false.
     * All items in this category remain in the database (preserves history).
     */
    @CacheEvict(value = "menus", key = "#restaurantId")
    @Transactional
    public void deleteCategory(Long categoryId, Long restaurantId, Long ownerId) {
        getRestaurantOwnedBy(restaurantId, ownerId);
        MenuCategory category = getCategoryBelongingToRestaurant(categoryId, restaurantId);
        category.setIsActive(false);
        categoryRepository.save(category);
        log.info("Category soft-deleted: id={}, restaurantId={}", categoryId, restaurantId);
    }

    // ── Get Categories (Owner) ────────────────────────────────────────────────

    /**
     * All categories (active + inactive) for a restaurant.
     * For the owner's management dashboard.
     */
    @Transactional(readOnly = true)
    public List<MenuCategoryResponse> getCategoriesForOwner(Long restaurantId, Long ownerId) {
        getRestaurantOwnedBy(restaurantId, ownerId);
        return categoryRepository
                .findByRestaurantIdOrderByDisplayOrderAsc(restaurantId)
                .stream()
                .map(menuMapper::toCategoryResponse)
                .toList();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ITEM OPERATIONS
    // ══════════════════════════════════════════════════════════════════════════

    // ── Create Item ───────────────────────────────────────────────────────────

    /**
     * Adds a new item to a restaurant's menu under a specific category.
     *
     * @throws BusinessException if discountedPrice >= price
     * @throws BusinessException if item name already exists in the category
     */
    @CacheEvict(value = "menus", key = "#restaurantId")
    @Transactional
    public MenuItemResponse createItem(
            CreateMenuItemRequest request,
            Long restaurantId,
            Long ownerId
    ) {
        Restaurant restaurant = getRestaurantOwnedBy(restaurantId, ownerId);
        MenuCategory category = getCategoryBelongingToRestaurant(request.categoryId(), restaurantId);

        // Guard: discountedPrice must be less than price
        if (request.discountedPrice() != null
                && request.discountedPrice().compareTo(request.price()) >= 0) {
            throw new BusinessException(
                    "Discounted price must be less than the regular price.");
        }

        // Guard: no duplicate item names in the same category
        if (itemRepository.existsByCategoryIdAndNameIgnoreCase(
                request.categoryId(), request.name())) {
            throw new BusinessException(
                    "An item named '" + request.name() + "' already exists in this category.");
        }

        MenuItem item = MenuItem.builder()
                .name(request.name().trim())
                .description(request.description())
                .price(request.price())
                .discountedPrice(request.discountedPrice())
                .foodType(request.foodType())
                .displayOrder(request.displayOrder() != null ? request.displayOrder() : 0)
                .category(category)
                .restaurant(restaurant)
                .isActive(true)
                .isAvailable(true)
                .build();

        MenuItem saved = itemRepository.save(item);
        log.info("MenuItem created: id={}, name='{}', restaurantId={}", saved.getId(), saved.getName(), restaurantId);
        return menuMapper.toItemResponse(saved);
    }

    // ── Update Item ───────────────────────────────────────────────────────────

    /**
     * Partially updates a menu item. Only non-null fields are applied.
     *
     * Special case — discount removal:
     *   Setting request.removeDiscount()=true clears discountedPrice,
     *   because a null discountedPrice in the request is ambiguous
     *   ("no change" vs "remove the discount").
     */
    @CacheEvict(value = "menus", key = "#restaurantId")
    @Transactional
    public MenuItemResponse updateItem(
            Long itemId,
            UpdateMenuItemRequest request,
            Long restaurantId,
            Long ownerId
    ) {
        getRestaurantOwnedBy(restaurantId, ownerId);
        MenuItem item = getItemBelongingToRestaurant(itemId, restaurantId);

        if (StringUtils.hasText(request.name()))        item.setName(request.name().trim());
        if (StringUtils.hasText(request.description())) item.setDescription(request.description());
        if (request.foodType()    != null) item.setFoodType(request.foodType());
        if (request.displayOrder()!= null) item.setDisplayOrder(request.displayOrder());
        if (request.isAvailable() != null) item.setIsAvailable(request.isAvailable());
        if (request.isActive()    != null) item.setIsActive(request.isActive());

        // Move to a different category if requested
        if (request.categoryId() != null) {
            MenuCategory newCategory = getCategoryBelongingToRestaurant(
                    request.categoryId(), restaurantId);
            item.setCategory(newCategory);
        }

        // Handle price update
        if (request.price() != null) item.setPrice(request.price());

        // Handle discount
        if (Boolean.TRUE.equals(request.removeDiscount())) {
            item.setDiscountedPrice(null);   // explicit removal
        } else if (request.discountedPrice() != null) {
            if (request.discountedPrice().compareTo(item.getPrice()) >= 0) {
                throw new BusinessException("Discounted price must be less than the regular price.");
            }
            item.setDiscountedPrice(request.discountedPrice());
        }

        MenuItem updated = itemRepository.save(item);
        return menuMapper.toItemResponse(updated);
    }

    // ── Toggle Availability ───────────────────────────────────────────────────

    /**
     * Flips the isAvailable flag (owner only).
     * Quick toggle for "temporarily out of stock" situations.
     */
    @CacheEvict(value = "menus", key = "#restaurantId")
    @Transactional
    public MenuItemResponse toggleItemAvailability(Long itemId, Long restaurantId, Long ownerId) {
        getRestaurantOwnedBy(restaurantId, ownerId);
        MenuItem item = getItemBelongingToRestaurant(itemId, restaurantId);
        item.setIsAvailable(!item.getIsAvailable());
        MenuItem updated = itemRepository.save(item);
        log.info("MenuItem id={} isAvailable toggled to {}", itemId, updated.getIsAvailable());
        return menuMapper.toItemResponse(updated);
    }

    // ── Delete Item (soft) ────────────────────────────────────────────────────

    @CacheEvict(value = "menus", key = "#restaurantId")
    @Transactional
    public void deleteItem(Long itemId, Long restaurantId, Long ownerId) {
        getRestaurantOwnedBy(restaurantId, ownerId);
        MenuItem item = getItemBelongingToRestaurant(itemId, restaurantId);
        item.setIsActive(false);
        item.setIsAvailable(false);
        itemRepository.save(item);
        log.info("MenuItem soft-deleted: id={}, restaurantId={}", itemId, restaurantId);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PUBLIC (CUSTOMER-FACING) READ OPERATIONS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Returns the full structured menu for a restaurant — cached in Redis.
     *
     * Each category contains only active + available items.
     * Categories with zero visible items are still included
     * (owner may want empty sections for structure).
     *
     * @param restaurantId the restaurant
     * @return ordered list of categories, each with their nested items
     */
    @Cacheable(value = "menus", key = "#restaurantId")
    @Transactional(readOnly = true)
    public List<MenuCategoryWithItemsResponse> getFullMenu(Long restaurantId) {
        if (!restaurantRepository.existsById(restaurantId)) {
            throw new ResourceNotFoundException("Restaurant", "id", restaurantId);
        }

        List<MenuCategory> categories =
                categoryRepository.findByRestaurantIdAndIsActiveTrueOrderByDisplayOrderAsc(restaurantId);

        // Load items per category — avoids @OneToMany lazy loading issues
        return categories.stream()
                .map(cat -> {
                    List<MenuItem> items =
                            itemRepository.findByCategoryIdAndIsActiveTrueAndIsAvailableTrueOrderByDisplayOrderAsc(
                                    cat.getId()
                            );
                    return menuMapper.toCategoryWithItemsResponse(cat, items);
                })
                .toList();
    }

    /**
     * All active + available items filtered by food type (e.g., VEG only).
     * Used for the "Veg" toggle on the restaurant menu page.
     */
    @Transactional(readOnly = true)
    public List<MenuItemResponse> getItemsByFoodType(Long restaurantId, FoodType foodType) {
        return menuMapper.toItemResponseList(
                itemRepository.findByRestaurantIdAndFoodTypeAndIsActiveTrueAndIsAvailableTrueOrderByDisplayOrderAsc(
                        restaurantId, foodType
                )
        );
    }

    /**
     * Keyword search across item name and description within a restaurant.
     */
    @Transactional(readOnly = true)
    public List<MenuItemResponse> searchItems(Long restaurantId, String keyword) {
        return menuMapper.toItemResponseList(
                itemRepository.searchByKeywordInRestaurant(restaurantId, keyword)
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // INTERNAL HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Loads a restaurant and verifies the caller owns it.
     *
     * @throws ResourceNotFoundException if restaurant not found
     * @throws BusinessException         if caller doesn't own it
     */
    private Restaurant getRestaurantOwnedBy(Long restaurantId, Long ownerId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));
        if (!restaurant.getOwner().getId().equals(ownerId)) {
            throw new BusinessException("You do not have permission to manage this restaurant's menu.");
        }
        return restaurant;
    }

    /**
     * Loads a MenuCategory and verifies it belongs to the given restaurant.
     */
    private MenuCategory getCategoryBelongingToRestaurant(Long categoryId, Long restaurantId) {
        return categoryRepository.findByIdAndRestaurantId(categoryId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MenuCategory", "id", categoryId));
    }

    /**
     * Loads a MenuItem and verifies it belongs to the given restaurant.
     */
    private MenuItem getItemBelongingToRestaurant(Long itemId, Long restaurantId) {
        return itemRepository.findByIdAndRestaurantId(itemId, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "MenuItem", "id", itemId));
    }
}
