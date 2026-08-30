package com.zomato.backend.controller;

import com.zomato.backend.dto.request.ChangePasswordRequest;
import com.zomato.backend.dto.request.UpdateProfileRequest;
import com.zomato.backend.dto.response.ApiResponse;
import com.zomato.backend.dto.response.UserResponse;
import com.zomato.backend.service.UserService;
import com.zomato.backend.util.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Manages the authenticated user's own profile.
 * <p>
 * All endpoints require a valid JWT — enforced by SecurityConfig.
 * The userId is extracted from the token, never trusted from the request body.
 * <p>
 * Base path: /api/users
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "Authenticated user profile management")
@SecurityRequirement(name = "bearerAuth")   // shows the lock icon on every endpoint in Swagger UI
public class UserController {

    private final UserService userService;
    private final AuthUtils   authUtils;

    // ── GET /api/users/me ─────────────────────────────────────────────────────

    @Operation(
        summary     = "Get my profile",
        description = "Returns the authenticated user's profile. UserId is extracted from the JWT."
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(
            HttpServletRequest request
    ) {
        Long userId = authUtils.getCurrentUserId(request);
        UserResponse profile = userService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.success("Profile fetched successfully", profile));
    }

    // ── PUT /api/users/me ─────────────────────────────────────────────────────

    @Operation(
        summary     = "Update my profile",
        description = "Updates name and/or phone. Only provided (non-null) fields are updated."
    )
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = authUtils.getCurrentUserId(httpRequest);
        UserResponse updated = userService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated successfully", updated));
    }

    // ── PUT /api/users/me/password ────────────────────────────────────────────

    @Operation(
        summary     = "Change my password",
        description = "Requires currentPassword for verification. " +
                      "newPassword and confirmPassword must match."
    )
    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = authUtils.getCurrentUserId(httpRequest);
        userService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
    }
}
