package com.zomato.backend.service;

import com.zomato.backend.dto.request.CreateRestaurantRequest;
import com.zomato.backend.dto.request.UpdateRestaurantRequest;
import com.zomato.backend.dto.response.RestaurantResponse;
import com.zomato.backend.dto.response.RestaurantSummaryResponse;
import com.zomato.backend.entity.Address;
import com.zomato.backend.entity.Restaurant;
import com.zomato.backend.entity.User;
import com.zomato.backend.entity.enums.CuisineType;
import com.zomato.backend.exception.BusinessException;
import com.zomato.backend.exception.ResourceNotFoundException;
import com.zomato.backend.mapper.RestaurantMapper;
import com.zomato.backend.repository.RestaurantRepository;
import com.zomato.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Business logic for the Restaurant module.
 * <p>
 * Caching is handled by {@link RestaurantCacheService} (stampede + penetration
 * + avalanche + safe invalidation). Only {@link #getRestaurantById(Long)} is
 * cached — paginated list/search results are not cached because the key space
 * is unbounded and data freshness matters more than read performance there.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestaurantService {

    private final RestaurantRepository   restaurantRepository;
    private final UserRepository         userRepository;
    private final RestaurantMapper       restaurantMapper;
    private final RestaurantCacheService restaurantCacheService;
    // ── Create ────────────────────────────────────────────────────────────────

    /**
     * Registers a new restaurant under the authenticated owner.
     * <p>
     * New restaurants start with isActive=false (pending admin approval)
     * and isOpen=false. Ratings default to 0.
     *
     * @param request  validated creation payload
     * @param ownerId  JWT-extracted user ID (must have RESTAURANT_OWNER role)
     * @return full RestaurantResponse of the saved restaurant
     */
    @Transactional
    public RestaurantResponse createRestaurant(CreateRestaurantRequest request, Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", ownerId));

        Address address = restaurantMapper.toAddressEntity(request.address());

        Restaurant restaurant = Restaurant.builder()
                .name(request.name().trim())
                .description(request.description())
                .phone(request.phone())
                .email(request.email())
                .cuisineType(request.cuisineType())
                .city(request.address().city().trim())
                .address(address)
                .owner(owner)
                // isActive = false, isOpen = false, avgRating = 0, totalRatings = 0 (defaults)
                .build();

        Restaurant saved = restaurantRepository.save(restaurant);
        log.info("Restaurant created: id={}, name='{}', ownerId={}", saved.getId(), saved.getName(), ownerId);
        return restaurantMapper.toRestaurantResponse(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Fetches a single restaurant by ID.
     * Visible to everyone (customers, owner, admin).
     * <p>
     * Cached in Redis under the key "restaurants::{id}" for 10 minutes.
     * Cache is evicted automatically on update, toggle, or delete.
     *
     * @param id the restaurant ID
     * @return full RestaurantResponse
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public RestaurantResponse getRestaurantById(Long id) {
        // All 4 cache protections active: stampede, penetration, avalanche, safe-release
        return restaurantCacheService.getOrLoad(
                id,
                () -> restaurantRepository.findById(id).map(restaurantMapper::toRestaurantResponse)
        );
    }

    /**
     * Paginated list of active restaurants, optionally filtered by city.
     * <p>
     * Sorted by avgRating DESC by default (highest-rated first).
     *
     * @param city   city name filter (null = all cities)
     * @param page   zero-based page number
     * @param size   page size (max 20 enforced internally)
     * @param onlyOpen if true, only returns currently open restaurants
     */
    @Transactional(readOnly = true)
    public Page<RestaurantSummaryResponse> getRestaurants(
            String city, int page, int size, boolean onlyOpen
    ) {
        size = Math.min(size, 20);  // hard cap to prevent abuse
        Pageable pageable = PageRequest.of(page, size, Sort.by("avgRating").descending());

        Page<Restaurant> results;

        if (StringUtils.hasText(city) && onlyOpen) {
            results = restaurantRepository
                    .findByCityIgnoreCaseAndIsActiveTrueAndIsOpenTrue(city, pageable);
        } else if (StringUtils.hasText(city)) {
            results = restaurantRepository
                    .findByCityIgnoreCaseAndIsActiveTrue(city, pageable);
        } else {
            // No city filter — return all active restaurants
            results = restaurantRepository.findByIsActive(true, pageable);
        }

        return results.map(restaurantMapper::toRestaurantSummaryResponse);
    }

    /**
     * Paginated list filtered by city AND cuisine type.
     */
    @Transactional(readOnly = true)
    public Page<RestaurantSummaryResponse> getRestaurantsByCuisine(
            String city, CuisineType cuisineType, int page, int size
    ) {
        size = Math.min(size, 20);
        Pageable pageable = PageRequest.of(page, size, Sort.by("avgRating").descending());

        Page<Restaurant> results = restaurantRepository
                .findByCityIgnoreCaseAndCuisineTypeAndIsActiveTrue(city, cuisineType, pageable);

        return results.map(restaurantMapper::toRestaurantSummaryResponse);
    }

    /**
     * Full-text search across restaurant name and description.
     *
     * @param q    search keyword
     * @param city city filter (null = global search)
     */
    @Transactional(readOnly = true)
    public Page<RestaurantSummaryResponse> searchRestaurants(
            String q, String city, int page, int size
    ) {
        size = Math.min(size, 20);
        Pageable pageable = PageRequest.of(page, size, Sort.by("avgRating").descending());

        Page<Restaurant> results = StringUtils.hasText(city)
                ? restaurantRepository.searchByCityAndKeyword(city, q, pageable)
                : restaurantRepository.searchByKeyword(q, pageable);

        return results.map(restaurantMapper::toRestaurantSummaryResponse);
    }

    /**
     * All restaurants owned by the authenticated owner.
     */
    @Transactional(readOnly = true)
    public Page<RestaurantResponse> getMyRestaurants(Long ownerId, int page, int size) {
        size = Math.min(size, 20);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        return restaurantRepository.findByOwnerId(ownerId, pageable)
                .map(restaurantMapper::toRestaurantResponse);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * Updates a restaurant's details.
     * Only the owner of this specific restaurant can call this.
     * <p>
     * Applies only non-null fields (partial update pattern).
     *
     * @param id      restaurant ID to update
     * @param request partial update payload
     * @param ownerId JWT-extracted user ID
     * @return updated RestaurantResponse
     * @throws BusinessException         if the caller does not own this restaurant
     * @throws ResourceNotFoundException if restaurant not found
     */
    @Transactional
    public RestaurantResponse updateRestaurant(
            Long id, UpdateRestaurantRequest request, Long ownerId
    ) {
        Restaurant restaurant = getRestaurantOwnedBy(id, ownerId);

        // Apply only non-null fields
        if (StringUtils.hasText(request.name()))        restaurant.setName(request.name().trim());
        if (StringUtils.hasText(request.description())) restaurant.setDescription(request.description());
        if (StringUtils.hasText(request.phone()))       restaurant.setPhone(request.phone());
        if (StringUtils.hasText(request.email()))       restaurant.setEmail(request.email());
        if (request.cuisineType() != null)              restaurant.setCuisineType(request.cuisineType());

        // Update address in-place (avoids creating a new address row)
        if (request.address() != null) {
            if (restaurant.getAddress() == null) {
                restaurant.setAddress(restaurantMapper.toAddressEntity(request.address()));
            } else {
                restaurantMapper.updateAddressFromRequest(restaurant.getAddress(), request.address());
                // Also update city on the restaurant root if address city changed
                if (StringUtils.hasText(request.address().city())) {
                    restaurant.setCity(request.address().city().trim());
                }
            }
        }

        Restaurant updated = restaurantRepository.save(restaurant);
        restaurantCacheService.evict(id);
        log.info("Restaurant updated: id={}, ownerId={}", id, ownerId);
        return restaurantMapper.toRestaurantResponse(updated);
    }

    // ── Toggle Open/Close ─────────────────────────────────────────────────────

    /**
     * Toggles the isOpen flag for a restaurant (owner only).
     * A restaurant can only be opened if it has been approved (isActive=true).
     *
     * @param id      restaurant ID
     * @param ownerId JWT-extracted owner ID
     * @return updated RestaurantResponse
     * @throws BusinessException if the restaurant is not yet approved
     */
    @Transactional
    public RestaurantResponse toggleOpen(Long id, Long ownerId) {
        Restaurant restaurant = getRestaurantOwnedBy(id, ownerId);

        if (!restaurant.getIsActive()) {
            throw new BusinessException(
                    "Restaurant is not yet approved by admin. Cannot change open status."
            );
        }

        restaurant.setIsOpen(!restaurant.getIsOpen());
        Restaurant updated = restaurantRepository.save(restaurant);
        restaurantCacheService.evict(id);
        log.info("Restaurant id={} isOpen toggled to {}", id, updated.getIsOpen());
        return restaurantMapper.toRestaurantResponse(updated);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /**
     * Soft-deletes a restaurant by setting isActive=false (owner only).
     * <p>
     * Why soft-delete instead of hard-delete?
     * Orders, reviews, and other records reference this restaurant.
     * Hard-deleting would violate FK constraints or cascade-delete
     * historical order data, which is unacceptable.
     *
     * @param id      restaurant ID
     * @param ownerId JWT-extracted owner ID
     */
    @Transactional
    public void deleteRestaurant(Long id, Long ownerId) {
        Restaurant restaurant = getRestaurantOwnedBy(id, ownerId);
        restaurant.setIsActive(false);
        restaurant.setIsOpen(false);
        restaurantRepository.save(restaurant);
        restaurantCacheService.evict(id);
        log.info("Restaurant soft-deleted: id={}, ownerId={}", id, ownerId);
    }

    // ── Internal Helpers ──────────────────────────────────────────────────────

    /**
     * Loads a restaurant and verifies ownership in one step.
     *
     * @throws ResourceNotFoundException if the restaurant doesn't exist
     * @throws BusinessException         if ownerId doesn't match
     */
    private Restaurant getRestaurantOwnedBy(Long restaurantId, Long ownerId) {
        Restaurant restaurant = findRestaurantById(restaurantId);
        if (!restaurant.getOwner().getId().equals(ownerId)) {
            throw new BusinessException(
                    "You do not have permission to modify this restaurant."
            );
        }
        return restaurant;
    }

    /**
     * Fetches a restaurant by ID or throws ResourceNotFoundException.
     */
    private Restaurant findRestaurantById(Long id) {
        return restaurantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", id));
    }

    // ── Admin Operations ──────────────────────────────────────────────────────

    /**
     * Approves a restaurant — sets isActive=true so it appears in public listings.
     * Admin-only. Also evicts any stale cache entry.
     *
     * @param id restaurant ID to approve
     * @return updated RestaurantResponse
     */
    @Transactional
    public RestaurantResponse approveRestaurant(Long id) {
        Restaurant restaurant = findRestaurantById(id);
        restaurant.setIsActive(true);
        Restaurant saved = restaurantRepository.save(restaurant);
        restaurantCacheService.evict(id);
        log.info("Restaurant approved by admin: id={}", id);
        return restaurantMapper.toRestaurantResponse(saved);
    }

    /**
     * Returns all restaurants for admin review — active and inactive.
     *
     * @param isActive filter: true=live, false=pending approval, null=all
     */
    @Transactional(readOnly = true)
    public Page<RestaurantResponse> getRestaurantsForAdmin(Boolean isActive, int page, int size) {
        size = Math.min(size, 50);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Restaurant> results = (isActive != null)
                ? restaurantRepository.findByIsActive(isActive, pageable)
                : restaurantRepository.findAll(pageable);
        return results.map(restaurantMapper::toRestaurantResponse);
    }


    private String restaurantKey(Long restaurantId) {
        return "restaurant:" + restaurantId;
    }
}

