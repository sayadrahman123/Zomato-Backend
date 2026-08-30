package com.zomato.backend.security;

import com.zomato.backend.config.SecurityConfig;
import com.zomato.backend.entity.User;
import com.zomato.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Spring Security hook — called by the authentication framework
 * whenever it needs to load a user by their email address.
 * <p>
 * Used in two places:
 *  1. {@link JwtAuthFilter} — to validate the JWT against the real DB record
 *  2. {@link SecurityConfig} — wired into the AuthenticationProvider
 * <p>
 * Why findByEmailAndIsActiveTrue?
 *   Banned users are rejected here at the DB level, before any
 *   password or token check. They receive UsernameNotFoundException
 *   which Spring Security converts to a 401 — same error as "not found",
 *   so attackers can't distinguish banned from non-existent accounts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads an active user by email and wraps them in Spring Security's
     * {@link UserDetails} object.
     *
     * The role is mapped to a GrantedAuthority with the "ROLE_" prefix
     * (e.g. CUSTOMER → ROLE_CUSTOMER) — required by Spring Security's
     * hasRole() and @PreAuthorize("hasRole('CUSTOMER')") checks.
     *
     * @param email the user's email (used as username in this app)
     * @return populated UserDetails
     * @throws UsernameNotFoundException if no active user exists with this email
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailAndIsActiveTrue(email)
                .orElseThrow(() -> {
                    log.debug("No active user found for email: {}", email);
                    return new UsernameNotFoundException(
                            "No active user found with email: " + email
                    );
                });

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .accountExpired(false)
                .accountLocked(!user.getIsActive())
                .credentialsExpired(false)
                .disabled(!user.getIsActive())
                .build();
    }
}
