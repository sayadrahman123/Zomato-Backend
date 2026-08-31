package com.zomato.backend.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents a category within a restaurant's menu.
 *
 * Examples: "Starters", "Main Course", "Biryani", "Desserts", "Beverages"
 *
 * Each restaurant defines its own set of categories.
 * Categories are ordered by {@code displayOrder} so owners can control
 * the sequence in which they appear on the menu page.
 *
 * Relationship:
 *   Restaurant (1) ──→ (many) MenuCategory
 *   MenuCategory (1) ──→ (many) MenuItem   [added in Step 3.2]
 *
 * Audit timestamps (createdAt, updatedAt) inherited from {@link BaseEntity}.
 */
@Entity
@Table(
    name = "menu_categories",
    indexes = {
        @Index(name = "idx_menu_category_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_menu_category_order",      columnList = "restaurant_id, display_order")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuCategory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Category Details ──────────────────────────────────────────────────────

    /**
     * Display name of the category — e.g. "Starters", "Main Course".
     * Unique per restaurant (enforced at service level, not DB, for clarity).
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Optional description shown below the category heading.
     * Example: "Freshly baked every morning"
     */
    @Column(length = 255)
    private String description;

    /**
     * Controls display order on the menu page.
     * Lower values appear first. Owner can reorder categories.
     * Default 0 — newly added categories appear at the top unless specified.
     */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    // ── Status ────────────────────────────────────────────────────────────────

    /**
     * When false, the category and all its items are hidden from customers.
     * Useful for seasonal menus (e.g., hide "Festive Specials" after the season).
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    // ── Relationships ──────────────────────────────────────────────────────────

    /**
     * The restaurant this category belongs to.
     * LAZY — don't load the full restaurant on every category query.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "restaurant_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_menu_category_restaurant")
    )
    private Restaurant restaurant;

    // MenuItem collection added in Step 3.2
}
