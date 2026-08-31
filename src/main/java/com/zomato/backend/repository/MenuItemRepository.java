package com.zomato.backend.repository;

import com.zomato.backend.entity.MenuItem;
import com.zomato.backend.entity.enums.FoodType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link MenuItem} entity.
 *
 * Uses the denormalized restaurant_id column for fast restaurant-wide queries,
 * avoiding joins through menu_categories.
 */
@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    // ── Customer queries ──────────────────────────────────────────────────────

    /**
     * All active, available items in a category — sorted by displayOrder.
     * Core query for rendering a single category section on the menu page.
     */
    List<MenuItem> findByCategoryIdAndIsActiveTrueAndIsAvailableTrueOrderByDisplayOrderAsc(
            Long categoryId
    );

    /**
     * All active items in a restaurant filtered by food type.
     * Used for the "VEG only" toggle common in food delivery apps.
     */
    List<MenuItem> findByRestaurantIdAndFoodTypeAndIsActiveTrueAndIsAvailableTrueOrderByDisplayOrderAsc(
            Long restaurantId, FoodType foodType
    );

    // ── Owner queries ─────────────────────────────────────────────────────────

    /**
     * All items (including inactive/unavailable) for a category.
     * Used on the owner's menu management dashboard.
     */
    List<MenuItem> findByCategoryIdOrderByDisplayOrderAsc(Long categoryId);

    /**
     * All items (active + inactive) across the entire restaurant.
     * Used for owner's full item inventory view.
     */
    List<MenuItem> findByRestaurantIdOrderByDisplayOrderAsc(Long restaurantId);

    /**
     * Find a specific menu item that belongs to a given restaurant.
     * Guards update/delete — owner can't modify another restaurant's items.
     */
    Optional<MenuItem> findByIdAndRestaurantId(Long id, Long restaurantId);

    /**
     * Fast ownership check — SELECT 1 instead of loading the full entity.
     */
    boolean existsByIdAndRestaurantId(Long id, Long restaurantId);

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Keyword search across item name and description within a restaurant.
     * Only returns active + available items (customer-facing).
     *
     * @param restaurantId the restaurant to search within
     * @param keyword      partial match against name or description
     */
    @Query("""
            SELECT m FROM MenuItem m
            WHERE m.restaurant.id = :restaurantId
              AND m.isActive = true
              AND m.isAvailable = true
              AND (
                    LOWER(m.name)        LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(m.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
              )
            ORDER BY m.displayOrder ASC
            """)
    List<MenuItem> searchByKeywordInRestaurant(
            @Param("restaurantId") Long restaurantId,
            @Param("keyword")      String keyword
    );

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Checks if an item name already exists in a category.
     * Prevents duplicate item names within the same category.
     */
    boolean existsByCategoryIdAndNameIgnoreCase(Long categoryId, String name);

    /**
     * Count of active items in a category.
     * Used to decide if a category can be hidden/deleted.
     */
    long countByCategoryIdAndIsActiveTrue(Long categoryId);

    /**
     * Count of all items belonging to a restaurant.
     * Used in restaurant analytics / admin views.
     */
    long countByRestaurantId(Long restaurantId);
}
