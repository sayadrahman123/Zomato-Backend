package com.zomato.backend.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zomato.backend.dto.response.ApiResponse;
import com.zomato.backend.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that applies per-endpoint-group rate limiting using Redis.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * Endpoint groups and their limits:
 *
 *   AUTH   → /api/auth/**        10 req / 60s   per IP
 *            Tight limit to prevent brute-force and credential stuffing.
 *
 *   PUBLIC → public GET paths    100 req / 60s  per IP
 *            Generous limit for browsing; still blocks scrapers.
 *
 *   USER   → all other paths     200 req / 60s  per userId
 *            Uses userId (not IP) → fair for mobile apps behind carrier NAT.
 *
 *   ADMIN  → /api/admin/**       skip
 *            Admins are trusted actors. Rate limiting adds no value here.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * Response headers on every allowed request:
 *
 *   X-RateLimit-Limit     : the limit for this group
 *   X-RateLimit-Remaining : requests remaining in the current window
 *   X-RateLimit-Window    : window duration in seconds
 *
 * On rejection (HTTP 429):
 *   Retry-After           : seconds until the window resets (= window size)
 *   Body                  : standard ApiResponse error envelope
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    // ── Group limits ──────────────────────────────────────────────────────────

    private static final int  AUTH_LIMIT     = 10;
    private static final long AUTH_WINDOW_MS = 60_000L;   // 1 minute

    private static final int  PUBLIC_LIMIT      = 100;
    private static final long PUBLIC_WINDOW_MS  = 60_000L;

    private static final int  USER_LIMIT      = 200;
    private static final long USER_WINDOW_MS  = 60_000L;

    private final RateLimiterService rateLimiterService;
    private final JwtUtil            jwtUtil;
    private final ObjectMapper       objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest  request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain         filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // ── Skip rate limiting for admin endpoints ────────────────────────────
        if (path.startsWith("/api/admin/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── Skip rate limiting for actuator and swagger ───────────────────────
        if (path.startsWith("/actuator/") || path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        // ── Determine group + identifier ──────────────────────────────────────
        String group;
        String identifier;
        int    limit;
        long   windowMs;

        if (path.startsWith("/api/auth/")) {
            // AUTH group: always keyed by IP (pre-auth, no userId available)
            group      = "AUTH";
            identifier = extractClientIp(request);
            limit      = AUTH_LIMIT;
            windowMs   = AUTH_WINDOW_MS;

        } else if (isPublicGetPath(path, request.getMethod())) {
            // PUBLIC group: keyed by IP
            group      = "PUBLIC";
            identifier = extractClientIp(request);
            limit      = PUBLIC_LIMIT;
            windowMs   = PUBLIC_WINDOW_MS;

        } else {
            // USER group: keyed by userId from JWT (fairer than IP for mobile)
            group      = "USER";
            identifier = extractUserIdentifier(request);
            limit      = USER_LIMIT;
            windowMs   = USER_WINDOW_MS;
        }

        // ── Check rate limit ──────────────────────────────────────────────────
        RateLimiterService.RateLimitResult result =
                rateLimiterService.check(group, identifier, limit, windowMs);

        long windowSeconds = windowMs / 1000;

        // Always set informational headers on allowed requests
        response.setHeader("X-RateLimit-Limit",     String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Window",    windowSeconds + "s");

        if (!result.allowed()) {
            // 429 Too Many Requests
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Retry-After", String.valueOf(windowSeconds));

            ApiResponse<Void> errorBody = ApiResponse.error(
                    "Too many requests. You have exceeded the " + limit +
                    " requests per " + windowSeconds + "s limit. " +
                    "Retry after " + windowSeconds + " seconds."
            );
            objectMapper.writeValue(response.getWriter(), errorBody);
            return;
        }

        filterChain.doFilter(request, response);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns true for GET paths that are publicly accessible without a JWT.
     * Mirrors the PUBLIC_GET_URLS array in SecurityConfig.
     */
    private boolean isPublicGetPath(String path, String method) {
        if (!"GET".equalsIgnoreCase(method)) return false;
        return path.startsWith("/api/restaurants")
            || path.startsWith("/api/reviews/restaurant/");
    }

    /**
     * Extracts the real client IP, respecting X-Forwarded-For from reverse proxies.
     * Falls back to remoteAddr if no forwarding header is present.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For: client, proxy1, proxy2 — first entry is the real client
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Extracts userId from the JWT for the USER group identifier.
     * Falls back to IP if the token is missing or invalid
     * (the JwtAuthFilter will reject it on the next step anyway).
     */
    private String extractUserIdentifier(HttpServletRequest request) {
        try {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                String email = jwtUtil.extractEmail(token);
                if (email != null && !email.isBlank()) {
                    return "user:" + email;
                }
            }
        } catch (Exception e) {
            log.debug("Could not extract user from JWT for rate limiting: {}", e.getMessage());
        }
        return extractClientIp(request);
    }
}
