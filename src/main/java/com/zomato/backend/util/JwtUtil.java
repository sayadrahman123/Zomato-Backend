package com.zomato.backend.util;

import com.zomato.backend.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Utility for creating, parsing, and validating JWT tokens.
 *
 * Uses jjwt 0.12.x API (parser().verifyWith().build()).
 *
 * Token structure (payload claims):
 * <pre>
 *   sub  : user email (subject)
 *   role : user role string (e.g. "CUSTOMER")
 *   uid  : user database ID (Long)
 *   iat  : issued at
 *   exp  : expiration
 * </pre>
 */
@Slf4j
@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtUtil(AppProperties appProperties) {
        // Derive a HMAC-SHA256 key from the configured secret string
        this.signingKey = Keys.hmacShaKeyFor(
                appProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8)
        );
        this.expirationMs = appProperties.getJwt().getExpirationMs();
    }

    // ── Token Generation ──────────────────────────────────────────────────────

    /**
     * Generates a signed JWT token for the given user.
     *
     * @param email  used as the token subject (standard "sub" claim)
     * @param userId embedded as a custom "uid" claim for fast lookup
     * @param role   embedded as a custom "role" claim for authorization
     * @return signed JWT string (ready to send in Authorization header)
     */
    public String generateToken(String email, Long userId, String role) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("uid", userId);
        extraClaims.put("role", role);

        return Jwts.builder()
                .claims(extraClaims)
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    // ── Token Parsing ─────────────────────────────────────────────────────────

    /**
     * Extracts the email (subject) from a token.
     * Throws JwtException if token is invalid or expired.
     */
    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts the user ID from the custom "uid" claim.
     */
    public Long extractUserId(String token) {
        return extractClaim(token, claims -> claims.get("uid", Long.class));
    }

    /**
     * Extracts the role string from the custom "role" claim.
     */
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /**
     * Returns the expiration date of the token.
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Returns the configured token lifetime in milliseconds.
     * Used by AuthResponse so the client knows when to refresh.
     */
    public long getExpirationMs() {
        return expirationMs;
    }

    // ── Token Validation ──────────────────────────────────────────────────────

    /**
     * Validates the token:
     * 1. Verifies the signature using our signing key
     * 2. Checks the subject (email) matches the given email
     * 3. Checks the token is not expired
     *
     * @param token the JWT string from the Authorization header
     * @param email the email of the user making the request
     * @return true if the token is valid for this user
     */
    public boolean isTokenValid(String token, String email) {
        try {
            String extractedEmail = extractEmail(token);
            return extractedEmail.equals(email) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Returns true if the token's expiration date is in the past.
     */
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ── Internal Helpers ──────────────────────────────────────────────────────

    /**
     * Generic claim extractor — parses the token and applies the
     * provided function to extract a specific field from the claims.
     *
     * @param token          the JWT string
     * @param claimsResolver function that picks a field from Claims
     * @param <T>            the type of the field
     * @return the extracted value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Parses and verifies the full JWT, returning all claims.
     * Throws {@link ExpiredJwtException} if expired,
     * {@link JwtException} if signature is invalid.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
