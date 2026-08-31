package com.zomato.backend.repository;

import com.zomato.backend.entity.MenuCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link MenuCategory} entity.
 *
 * Categories are always fetched in displayOrder ASC so the menu renders
 * in the order the owner configured.
 */
@Repository
public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {

    // ── Customer queries ──────────────────────────────────────────────────────

    /**
     * All active categories for a restaurant, sorted by displayOrder.
     * Used to render the public menu page.
     */
    List<MenuCategory> findByRestaurantIdAndIsActiveTrueOrderByDisplayOrderAsc(Long restaurantId);

    // ── Owner queries ─────────────────────────────────────────────────────────

    /**
     * All categories (active + inactive) for a restaurant, sorted by displayOrder.
     * Used on the owner's menu management dashboard.
     */
    List<MenuCategory> findByRestaurantIdOrderByDisplayOrderAsc(Long restaurantId);

    /**
     * Find a specific category that belongs to a given restaurant.
     * Guards update/delete endpoints — prevents owners modifying another
     * restaurant's categories.
     */
    Optional<MenuCategory> findByIdAndRestaurantId(Long id, Long restaurantId);

    /**
     * Fast ownership check — SELECT 1 instead of loading the full entity.
     */
    boolean existsByIdAndRestaurantId(Long id, Long restaurantId);

    /**
     * Checks if a category name already exists in a restaurant.
     * Used to prevent duplicate category names per restaurant.
     */
    boolean existsByRestaurantIdAndNameIgnoreCase(Long restaurantId, String name);

    /**
     * Count of active categories in a restaurant.
     * Useful for validation (e.g., must have at least one category).
     */
    long countByRestaurantIdAndIsActiveTrue(Long restaurantId);
}
