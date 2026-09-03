package com.zomato.backend.config;

import com.zomato.backend.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Central Spring Security configuration.
 * <p>
 * Key design choices:
 * - STATELESS session: no HttpSession created (JWT is the session)
 * - CSRF disabled: safe for stateless REST APIs using JWT
 * - JwtAuthFilter runs BEFORE UsernamePasswordAuthenticationFilter
 * - @EnableMethodSecurity: enables @PreAuthorize on controllers/services
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // enables @PreAuthorize("hasRole('ADMIN')") etc.
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final com.zomato.backend.ratelimit.RateLimitFilter rateLimitFilter;
    private final UserDetailsService userDetailsService;

    // ── Publicly accessible endpoints ─────────────────────────────────────────

    private static final String[] PUBLIC_POST_URLS = {
            "/api/auth/register",
            "/api/auth/login"
    };

    private static final String[] PUBLIC_GET_URLS = {
            // Swagger / OpenAPI docs — accessible without auth
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/api-docs",
            "/api-docs/**",
            // Actuator health — useful for Docker healthchecks
            "/actuator/health",
            "/actuator/info",
            // Public browse endpoints (no login required to see restaurants/menus)
            "/api/restaurants",
            "/api/restaurants/**",
            "/api/restaurants/*/menu",
            // Public review listing (read-only, no auth needed)
            "/api/reviews/restaurant/**"
    };

    // ── Security Filter Chain ─────────────────────────────────────────────────

    /**
     * Configures the HTTP security filter chain.
     *
     * @param http the HttpSecurity builder
     * @return the built SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — not needed for stateless JWT-based REST APIs
            .csrf(AbstractHttpConfigurer::disable)

            // Disable form-based login — we use JWT
            .formLogin(AbstractHttpConfigurer::disable)

            // Disable HTTP Basic auth header — we use Bearer token
            .httpBasic(AbstractHttpConfigurer::disable)

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                    // Public POST endpoints (register, login)
                    .requestMatchers(HttpMethod.POST, PUBLIC_POST_URLS).permitAll()
                    // Public GET endpoints (swagger, health, restaurants)
                    .requestMatchers(HttpMethod.GET, PUBLIC_GET_URLS).permitAll()
                    // Admin-only endpoints
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    // Everything else requires authentication
                    .anyRequest().authenticated()
            )

            // STATELESS — no HttpSession; JWT carries all auth state
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Wire up our DaoAuthenticationProvider
            .authenticationProvider(authenticationProvider())

            // Add Rate Limit filter before the JWT auth filter
            .addFilterBefore(rateLimitFilter, JwtAuthFilter.class)

            // Add JWT filter before the default username/password filter
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ── Authentication Beans ──────────────────────────────────────────────────

    /**
     * BCrypt password encoder — strength 12 (default is 10, 12 is
     * slightly stronger while still being fast enough for a web app).
     */
    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder(12);
    }

    /**
     * DaoAuthenticationProvider — connects a Spring Security's authentication
     * mechanism to our UserDetailsService and PasswordEncoder.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Exposes the AuthenticationManager bean so AuthService can call
     * authenticationManager.authenticate(...) during login.
     *
     * @param config provided by Spring Security auto-config
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }
}
