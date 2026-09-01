package com.zomato.backend.dto.request;

import com.zomato.backend.entity.enums.UserRole;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for PATCH /api/admin/users/{userId}/role
 */
public record ChangeRoleRequest(
        @NotNull(message = "New role is required")
        UserRole role
) {}
