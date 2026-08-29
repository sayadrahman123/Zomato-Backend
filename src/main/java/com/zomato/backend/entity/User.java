package com.zomato.backend.entity;

import com.zomato.backend.entity.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a user account in the Zomato platform.
 *
 * A single User can hold one of four roles (see {@link UserRole}).
 * Passwords are stored as BCrypt hashes — never in plain text.
 *
 * Audit timestamps (createdAt, updatedAt) are inherited from {@link BaseEntity}.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email"),
        @UniqueConstraint(name = "uk_users_phone", columnNames = "phone")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Personal Info ──────────────────────────────────────────────────────────

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 15)
    private String phone;

    /**
     * BCrypt-hashed password. Never expose this in any DTO or API response.
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // ── Role ──────────────────────────────────────────────────────────────────

    /**
     * Stored as a VARCHAR string so adding new roles later doesn't require
     * a DB migration that changes column type.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    // ── Account Status ────────────────────────────────────────────────────────

    /**
     * False when an admin has banned the account.
     * Checked in UserDetailsServiceImpl — banned users cannot authenticate.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}
