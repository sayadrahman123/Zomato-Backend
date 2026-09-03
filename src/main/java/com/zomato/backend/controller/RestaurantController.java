package com.zomato.backend.controller;

import com.zomato.backend.dto.request.CreateRestaurantRequest;
import com.zomato.backend.dto.request.UpdateRestaurantRequest;
import com.zomato.backend.dto.response.ApiResponse;
import com.zomato.backend.dto.response.PagedResponse;
import com.zomato.backend.dto.response.RestaurantResponse;
import com.zomato.backend.dto.response.RestaurantSummaryResponse;
import com.zomato.backend.entity.enums.CuisineType;
import com.zomato.backend.service.RestaurantService;
import com.zomato.backend.util.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Handles all restaurant operations.
 * <p>
 * Access rules:
 *  - GET endpoints → public (no token required for browse/search)
 *  - POST / PUT / DELETE / PATCH → RESTAURANT_OWNER only
 * <p>
 * Base path: /api/restaurants
 */
@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
@Tag(name = "Restaurants", description = "Restaurant CRUD, listing, search, and status management")
public class RestaurantController {

    private final RestaurantService restaurantService;
    private final AuthUtils         authUtils;

    // ── POST /api/restaurants ─────────────────────────────────────────────────

    @Operation(
        summary     = "Register a new restaurant",
        description = "Creates a new restaurant under the authenticated owner. " +
                      "Starts as inactive (pending admin approval)."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @PostMapping
    public ResponseEntity<ApiResponse<RestaurantResponse>> createRestaurant(
            @Valid @RequestBody CreateRestaurantRequest request,
            HttpServletRequest httpRequest
    ) {
        Long ownerId = authUtils.getCurrentUserId(httpRequest);
        RestaurantResponse response = restaurantService.createRestaurant(request, ownerId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Restaurant registered successfully. Pending admin approval.", response));
    }

    // ── GET /api/restaurants/{id} ─────────────────────────────────────────────

    @Operation(
        summary     = "Get restaurant by ID",
        description = "Returns full restaurant details. Result is cached in Redis for 10 minutes."
    )
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<RestaurantResponse>> getRestaurantById(
            @PathVariable Long id
    ) {
        RestaurantResponse response = restaurantService.getRestaurantById(id);
        return ResponseEntity.ok(ApiResponse.success("Restaurant fetched successfully", response));
    }

    // ── GET /api/restaurants ──────────────────────────────────────────────────

    @Operation(
        summary     = "List restaurants",
        description = "Paginated list of active restaurants. " +
                      "Filter by city, cuisine type, and open-now status. " +
                      "Sorted by rating (highest first). Max 20 per page."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<RestaurantSummaryResponse>>> getRestaurants(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) CuisineType cuisineType,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "false") boolean onlyOpen
    ) {
        Page<RestaurantSummaryResponse> results;

        if (cuisineType != null && city != null) {
            results = restaurantService.getRestaurantsByCuisine(city, cuisineType, page, size);
        } else {
            results = restaurantService.getRestaurants(city, page, size, onlyOpen);
        }

        return ResponseEntity.ok(ApiResponse.paged("Restaurants fetched successfully", results));
    }

    // ── GET /api/restaurants/search ───────────────────────────────────────────
    // Step 2.8 — Search endpoint

    @Operation(
        summary     = "Search restaurants",
        description = "Keyword search across restaurant name and description. " +
                      "Optionally filter by city. Results sorted by rating."
    )
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<RestaurantSummaryResponse>>> searchRestaurants(
            @RequestParam String q,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<RestaurantSummaryResponse> results =
                restaurantService.searchRestaurants(q, city, page, size);
        return ResponseEntity.ok(ApiResponse.paged("Search results", results));
    }

    // ── GET /api/restaurants/my ───────────────────────────────────────────────

    @Operation(
        summary     = "Get my restaurants",
        description = "Returns all restaurants owned by the authenticated owner."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PagedResponse<RestaurantResponse>>> getMyRestaurants(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest
    ) {
        Long ownerId = authUtils.getCurrentUserId(httpRequest);
        Page<RestaurantResponse> results =
                restaurantService.getMyRestaurants(ownerId, page, size);
        return ResponseEntity.ok(ApiResponse.paged("Your restaurants", results));
    }

    // ── PUT /api/restaurants/{id} ─────────────────────────────────────────────

    @Operation(
        summary     = "Update a restaurant",
        description = "Partial update — only provided (non-null) fields are applied. " +
                      "Evicts the Redis cache for this restaurant."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RestaurantResponse>> updateRestaurant(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRestaurantRequest request,
            HttpServletRequest httpRequest
    ) {
        Long ownerId = authUtils.getCurrentUserId(httpRequest);
        RestaurantResponse response = restaurantService.updateRestaurant(id, request, ownerId);
        return ResponseEntity.ok(ApiResponse.success("Restaurant updated successfully", response));
    }

    // ── PATCH /api/restaurants/{id}/toggle-open ───────────────────────────────

    @Operation(
        summary     = "Toggle open/closed status",
        description = "Flips the isOpen flag. Only works on admin-approved restaurants. " +
                      "Evicts the Redis cache for this restaurant."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @PatchMapping("/{id}/toggle-open")
    public ResponseEntity<ApiResponse<RestaurantResponse>> toggleOpen(
            @PathVariable Long id,
            HttpServletRequest httpRequest
    ) {
        Long ownerId = authUtils.getCurrentUserId(httpRequest);
        RestaurantResponse response = restaurantService.toggleOpen(id, ownerId);
        return ResponseEntity.ok(ApiResponse.success(
                "Restaurant is now " + (response.isOpen() ? "OPEN" : "CLOSED"), response
        ));
    }

    // ── DELETE /api/restaurants/{id} ──────────────────────────────────────────

    @Operation(
        summary     = "Delete (deactivate) a restaurant",
        description = "Soft-deletes the restaurant by setting isActive=false. " +
                      "Historical orders and reviews are preserved. " +
                      "Evicts the Redis cache."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRestaurant(
            @PathVariable Long id,
            HttpServletRequest httpRequest
    ) {
        Long ownerId = authUtils.getCurrentUserId(httpRequest);
        restaurantService.deleteRestaurant(id, ownerId);
        return ResponseEntity.ok(ApiResponse.success("Restaurant deactivated successfully"));
    }
    // ── Admin Endpoints ───────────────────────────────────────────────────────

    @Operation(
        summary     = "Admin: list all restaurants",
        description = "Returns all restaurants with optional isActive filter. " +
                      "isActive=false → pending approval queue."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin")
    public ResponseEntity<ApiResponse<PagedResponse<RestaurantResponse>>> getRestaurantsForAdmin(
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<RestaurantResponse> results =
                restaurantService.getRestaurantsForAdmin(isActive, page, size);
        return ResponseEntity.ok(ApiResponse.paged("Admin: restaurants fetched", results));
    }

    @Operation(
        summary     = "Admin: approve a restaurant",
        description = "Sets isActive=true so the restaurant appears in public listings."
    )
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<RestaurantResponse>> approveRestaurant(
            @PathVariable Long id
    ) {
        RestaurantResponse response = restaurantService.approveRestaurant(id);
        return ResponseEntity.ok(ApiResponse.success("Restaurant approved and now live", response));
    }
}

