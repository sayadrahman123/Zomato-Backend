package com.zomato.backend.dto.response;

import com.zomato.backend.entity.enums.UserRole;

import java.time.LocalDateTime;

/**
 * Safe user representation returned by the API.
 *
 * NEVER includes passwordHash. All sensitive internal fields
 * are deliberately excluded from this record.
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        String phone,
        UserRole role,
        Boolean isActive,
        LocalDateTime createdAt
) {}
