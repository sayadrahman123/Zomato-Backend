package com.zomato.backend.controller;

import com.zomato.backend.dto.request.ChangeRoleRequest;
import com.zomato.backend.dto.response.ApiResponse;
import com.zomato.backend.dto.response.UserResponse;
import com.zomato.backend.entity.DeliveryPartner;
import com.zomato.backend.entity.Restaurant;
import com.zomato.backend.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only management endpoints.
 *
 * Base path: /api/admin
 * Access: ADMIN role only (enforced at class level + SecurityConfig).
 *
 * Three sections:
 *   /users/**        — user management (ban, unban, change role)
 *   /restaurants/**  — restaurant approval and suspension
 *   /partners/**     — delivery partner verification and suspension
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin", description = "Platform administration — users, restaurants, and delivery partners")
public class AdminController {

    private final AdminService adminService;

    // ══════════════════════════════════════════════════════════════════════════
    // USER MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(summary = "List all users", description = "Paginated list of all platform users, newest first.")
    @GetMapping("/users")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success("Users fetched", adminService.getAllUsers(page, size)));
    }

    @Operation(summary = "Get user by ID")
    @GetMapping("/users/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success("User fetched", adminService.getUserById(userId)));
    }

    @Operation(
        summary     = "Ban a user",
        description = "Sets isActive=false. Banned users cannot log in. Admins cannot ban other admins."
    )
    @PatchMapping("/users/{userId}/ban")
    public ResponseEntity<ApiResponse<UserResponse>> banUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success("User banned successfully", adminService.banUser(userId)));
    }

    @Operation(summary = "Unban a user", description = "Restores a previously banned user's access.")
    @PatchMapping("/users/{userId}/unban")
    public ResponseEntity<ApiResponse<UserResponse>> unbanUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success("User unbanned successfully", adminService.unbanUser(userId)));
    }

    @Operation(
        summary     = "Change a user's role",
        description = "Changes the platform role. Cannot demote an ADMIN account."
    )
    @PatchMapping("/users/{userId}/role")
    public ResponseEntity<ApiResponse<UserResponse>> changeRole(
            @PathVariable Long userId,
            @Valid @RequestBody ChangeRoleRequest request
    ) {
        UserResponse user = adminService.changeUserRole(userId, request.role());
        return ResponseEntity.ok(ApiResponse.success(
                "Role changed to " + request.role().name(), user));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RESTAURANT MODERATION
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "List pending restaurants",
        description = "Restaurants with isActive=false awaiting admin approval."
    )
    @GetMapping("/restaurants/pending")
    public ResponseEntity<ApiResponse<Page<Restaurant>>> getPendingRestaurants(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Pending restaurants fetched", adminService.getPendingRestaurants(page, size)));
    }

    @Operation(
        summary     = "Approve a restaurant",
        description = "Sets isActive=true — restaurant becomes visible to customers."
    )
    @PatchMapping("/restaurants/{restaurantId}/approve")
    public ResponseEntity<ApiResponse<Restaurant>> approveRestaurant(
            @PathVariable Long restaurantId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Restaurant approved", adminService.approveRestaurant(restaurantId)));
    }

    @Operation(
        summary     = "Reject / suspend a restaurant",
        description = "Sets isActive=false and isOpen=false. Used for new rejections or post-approval violations."
    )
    @PatchMapping("/restaurants/{restaurantId}/reject")
    public ResponseEntity<ApiResponse<Restaurant>> rejectRestaurant(
            @PathVariable Long restaurantId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Restaurant rejected/suspended", adminService.rejectRestaurant(restaurantId)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DELIVERY PARTNER MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "List pending delivery partners",
        description = "Partners with isVerified=false awaiting document verification."
    )
    @GetMapping("/partners/pending")
    public ResponseEntity<ApiResponse<Page<DeliveryPartner>>> getPendingPartners(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Pending partners fetched", adminService.getPendingPartners(page, size)));
    }

    @Operation(
        summary     = "Verify a delivery partner",
        description = "Sets isVerified=true — partner can now go online and accept deliveries."
    )
    @PatchMapping("/partners/{partnerId}/verify")
    public ResponseEntity<ApiResponse<DeliveryPartner>> verifyPartner(
            @PathVariable Long partnerId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Partner verified", adminService.verifyPartner(partnerId)));
    }

    @Operation(
        summary     = "Suspend a delivery partner",
        description = "Sets isActive=false and isAvailable=false — partner is immediately taken offline."
    )
    @PatchMapping("/partners/{partnerId}/suspend")
    public ResponseEntity<ApiResponse<DeliveryPartner>> suspendPartner(
            @PathVariable Long partnerId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Partner suspended", adminService.suspendPartner(partnerId)));
    }

    @Operation(
        summary     = "Reinstate a suspended delivery partner",
        description = "Sets isActive=true — partner can go online again after reinstatement."
    )
    @PatchMapping("/partners/{partnerId}/reinstate")
    public ResponseEntity<ApiResponse<DeliveryPartner>> reinstatePartner(
            @PathVariable Long partnerId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Partner reinstated", adminService.reinstatePartner(partnerId)));
    }
}
