package com.zomato.backend.security;

import com.zomato.backend.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT Authentication Filter — runs once per HTTP request.
 *
 * Flow:
 * 1. Extract the "Authorization: Bearer <token>" header
 * 2. Parse and validate the JWT using {@link JwtUtil}
 * 3. Load the user from DB via {@link UserDetailsService}
 * 4. Set the authenticated principal into {@link SecurityContextHolder}
 *
 * If any step fails (missing header, bad token, expired token),
 * the filter simply continues without setting authentication —
 * Spring Security's downstream filters will then return 401.
 *
 * Why {@link OncePerRequestFilter}?
 * Guarantees this filter executes exactly once per request,
 * even with async dispatching or servlet forwards.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        // ── Step 1: Extract token from header ─────────────────────────────────
        String token = extractTokenFromRequest(request);

        if (token == null) {
            // No token — let the request continue; secured endpoints will 401
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 2: Extract email from token ──────────────────────────────────
        String email;
        try {
            email = jwtUtil.extractEmail(token);
        } catch (Exception e) {
            log.debug("Could not extract email from JWT: {}", e.getMessage());
            filterChain.doFilter(request, response);
            return;
        }

        // ── Step 3: Authenticate only if not already authenticated ────────────
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            // ── Step 4: Validate token against the loaded user ─────────────────
            if (jwtUtil.isTokenValid(token, userDetails.getUsername())) {

                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,                          // credentials (not needed post-auth)
                                userDetails.getAuthorities()   // roles/permissions
                        );

                // Attach request metadata (IP, session) to the auth token
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // ── Step 5: Set authentication in SecurityContext ──────────────
                SecurityContextHolder.getContext().setAuthentication(authToken);
                log.debug("Authenticated user: {} for URI: {}", email, request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the raw JWT string from the Authorization header.
     *
     * Returns null if:
     * - Header is missing
     * - Header doesn't start with "Bearer "
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }

        return null;
    }
}
