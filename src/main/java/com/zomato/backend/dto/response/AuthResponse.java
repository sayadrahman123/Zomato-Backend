package com.zomato.backend.dto.response;

import com.zomato.backend.entity.enums.UserRole;

/**
 * Response body for POST /api/auth/register and POST /api/auth/login.
 *
 * Contains the JWT token and basic user info so the client doesn't
 * need to make a second GET /api/users/me call immediately after login.
 */
public record AuthResponse(
        String token,
        String tokenType,
        Long expiresInMs,
        Long userId,
        String name,
        String email,
        UserRole role
) {
    /**
     * Convenience factory — callers just pass the token + user info,
     * tokenType is always "Bearer".
     */
    public static AuthResponse of(String token, Long expiresInMs,
                                   Long userId, String name,
                                   String email, UserRole role) {
        return new AuthResponse(token, "Bearer", expiresInMs, userId, name, email, role);
    }
}
