package com.zomato.backend.repository;

import com.zomato.backend.entity.Restaurant;
import com.zomato.backend.entity.enums.CuisineType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link Restaurant} entity.
 *
 * Query strategy:
 *  - Simple filters     → Spring Data method names (no SQL)
 *  - Complex search     → @Query JPQL (database-agnostic)
 *  - Ownership checks   → existsBy... (SELECT 1, fastest)
 */
@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    // ── Owner queries ─────────────────────────────────────────────────────────

    /**
     * All restaurants belonging to a specific owner.
     * Used on the restaurant owner's dashboard.
     */
    Page<Restaurant> findByOwnerId(Long ownerId, Pageable pageable);

    /**
     * Find a specific restaurant only if it belongs to the given owner.
     * Used to guard update/delete endpoints — prevents owners from
     * modifying other owners' restaurants.
     */
    Optional<Restaurant> findByIdAndOwnerId(Long id, Long ownerId);

    /**
     * Quick ownership check without loading the full entity.
     * More efficient than findByIdAndOwnerId() when we only need true/false.
     */
    boolean existsByIdAndOwnerId(Long id, Long ownerId);

    // ── Customer listing queries ───────────────────────────────────────────────

    /**
     * All active restaurants in a city — main listing page.
     * Only returns admin-approved (isActive=true) restaurants.
     */
    Page<Restaurant> findByCityIgnoreCaseAndIsActiveTrue(String city, Pageable pageable);

    /**
     * All active AND currently open restaurants in a city.
     * Used for "open now" filter on the listing page.
     */
    Page<Restaurant> findByCityIgnoreCaseAndIsActiveTrueAndIsOpenTrue(
            String city, Pageable pageable
    );

    /**
     * All active restaurants of a specific cuisine in a city.
     * Used for cuisine-type filter (e.g., "Show only Biryani restaurants in Bangalore").
     */
    Page<Restaurant> findByCityIgnoreCaseAndCuisineTypeAndIsActiveTrue(
            String city, CuisineType cuisineType, Pageable pageable
    );

    // ── Search ────────────────────────────────────────────────────────────────

    /**
     * Full-text search across restaurant name and city.
     *
     * JPQL LIKE search — case-insensitive, matches partial words.
     * Only returns active (admin-approved) restaurants.
     *
     * Example: q="pizza", city="Mumbai"
     * → returns all restaurants in Mumbai whose name contains "pizza"
     *
     * @param q    the search keyword (partial match on name)
     * @param city the city to restrict results to
     */
    @Query("""
            SELECT r FROM Restaurant r
            WHERE r.isActive = true
              AND LOWER(r.city) = LOWER(:city)
              AND (
                    LOWER(r.name) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(r.description) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<Restaurant> searchByCityAndKeyword(
            @Param("city") String city,
            @Param("q") String q,
            Pageable pageable
    );

    /**
     * Global search across all cities — used when no city filter is applied.
     *
     * @param q the search keyword
     */
    @Query("""
            SELECT r FROM Restaurant r
            WHERE r.isActive = true
              AND (
                    LOWER(r.name) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(r.description) LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<Restaurant> searchByKeyword(@Param("q") String q, Pageable pageable);

    // ── Admin queries ─────────────────────────────────────────────────────────

    /**
     * All restaurants filtered by active status — used on the admin dashboard.
     * isActive=false → pending approval; isActive=true → live restaurants.
     */
    Page<Restaurant> findByIsActive(Boolean isActive, Pageable pageable);

    /**
     * Count of restaurants owned by a user.
     * Used to enforce any per-owner restaurant limits (future feature).
     */
    long countByOwnerId(Long ownerId);
}
