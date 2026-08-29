package com.zomato.backend.repository;

import com.zomato.backend.entity.User;
import com.zomato.backend.entity.enums.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link User} entity.
 *
 * Spring Data JPA auto-implements all methods at runtime —
 * no SQL needed, the method names are the query.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // ── Lookup ────────────────────────────────────────────────────────────────

    /**
     * Used by Spring Security's UserDetailsService to load a user during login.
     */
    Optional<User> findByEmail(String email);

    /**
     * Used to find a user by their phone number.
     */
    Optional<User> findByPhone(String phone);

    /**
     * Find user only if the account is active (not banned).
     * Used in UserDetailsServiceImpl so banned users cannot log in.
     */
    Optional<User> findByEmailAndIsActiveTrue(String email);

    // ── Existence Checks (used during registration to prevent duplicates) ─────

    /**
     * Check if an email is already registered.
     * More efficient than findByEmail() — does SELECT 1 instead of SELECT *.
     */
    boolean existsByEmail(String email);

    /**
     * Check if a phone number is already registered.
     */
    boolean existsByPhone(String phone);

    // ── Admin Queries ─────────────────────────────────────────────────────────

    /**
     * Fetch all users with a specific role — used by Admin module.
     * Returns a page to avoid loading thousands of records at once.
     */
    Page<User> findByRole(UserRole role, Pageable pageable);

    /**
     * Fetch all active or inactive users — used by Admin module.
     */
    Page<User> findByIsActive(Boolean isActive, Pageable pageable);
}
