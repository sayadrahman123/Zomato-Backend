package com.zomato.backend.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Utility for extracting authenticated user info from the current request.
 * <p>
 * Why not @AuthenticationPrincipal UserDetails?
 *   UserDetails only gives us the email (username). To get the userId
 *   we'd need an extra DB call on every request. Since we embedded
 *   "uid" in the JWT claims, we can read it directly from the token
 *   with zero DB overhead.
 * <p>
 * Usage in any controller method:
 * <pre>
 *   Long userId = authUtils.getCurrentUserId(request);
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class AuthUtils {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX        = "Bearer ";

    private final JwtUtil jwtUtil;

    /**
     * Extracts the authenticated user's ID from the JWT in the
     * Authorization header of the current request.
     *
     * @param request the current HTTP request
     * @return the userId stored in the "uid" JWT claim
     */
    public Long getCurrentUserId(HttpServletRequest request) {
        String token = extractToken(request);
        return jwtUtil.extractUserId(token);
    }

    /**
     * Extracts the authenticated user's email from the JWT.
     *
     * @param request the current HTTP request
     * @return email (subject claim)
     */
    public String getCurrentUserEmail(HttpServletRequest request) {
        String token = extractToken(request);
        return jwtUtil.extractEmail(token);
    }

    /**
     * Extracts the raw JWT string from the Authorization header.
     */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        throw new IllegalStateException(
                "No JWT token found in request — this should not reach a secured endpoint"
        );
    }
}
