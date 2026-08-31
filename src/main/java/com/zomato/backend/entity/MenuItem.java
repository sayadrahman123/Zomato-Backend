package com.zomato.backend.entity;

import com.zomato.backend.entity.enums.FoodType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Represents a single dish or drink on a restaurant's menu.
 *
 * Hierarchy:
 *   Restaurant → MenuCategory → MenuItem
 *
 * Why restaurant_id is denormalized on MenuItem?
 *   Most menu queries filter by restaurant — having restaurant_id directly
 *   on menu items avoids a join through menu_categories on every query.
 *   It also makes it fast to count/list all items for a restaurant.
 *
 * Pricing:
 *   - {@code price} is the base/regular price.
 *   - {@code discountedPrice} is optional — when set, customers pay this
 *     amount and the UI shows the original price struck through.
 *
 * Audit timestamps (createdAt, updatedAt) inherited from {@link BaseEntity}.
 */
@Entity
@Table(
    name = "menu_items",
    indexes = {
        @Index(name = "idx_menu_item_category",   columnList = "category_id"),
        @Index(name = "idx_menu_item_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_menu_item_food_type",  columnList = "restaurant_id, food_type"),
        @Index(name = "idx_menu_item_available",  columnList = "restaurant_id, is_available")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Item Details ──────────────────────────────────────────────────────────

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // ── Pricing ───────────────────────────────────────────────────────────────

    /**
     * Regular/base price. precision=10, scale=2 supports prices up to ₹99,999,999.99.
     * Using BigDecimal — never use Double/Float for monetary values.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /**
     * Optional discounted/sale price.
     * When non-null, this is the price customers actually pay.
     * Must be less than {@code price} (validated at service level).
     */
    @Column(name = "discounted_price", precision = 10, scale = 2)
    private BigDecimal discountedPrice;

    // ── Classification ────────────────────────────────────────────────────────

    /**
     * VEG / NON_VEG / EGG / VEGAN — shown as colored dot on the menu.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "food_type", nullable = false, length = 10)
    private FoodType foodType;

    // ── Display ───────────────────────────────────────────────────────────────

    /**
     * Controls display order within the category.
     * Lower values appear first. Default 0.
     */
    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    // ── Status ────────────────────────────────────────────────────────────────

    /**
     * When false, the item is hidden from the menu entirely.
     * Owner uses this to permanently remove an item without losing history.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * When false, item is shown but marked "Currently Unavailable".
     * Owners toggle this for temporary out-of-stock situations.
     * Different from isActive — isAvailable is reversible; isActive=false is a soft-delete.
     */
    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private Boolean isAvailable = true;

    // ── Relationships ──────────────────────────────────────────────────────────

    /**
     * The category this item belongs to (e.g., "Starters").
     * LAZY — avoid loading the full category on every item query.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "category_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_menu_item_category")
    )
    private MenuCategory category;

    /**
     * Denormalized FK to restaurant — allows fast filtering of all items
     * for a restaurant without joining through menu_categories.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "restaurant_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_menu_item_restaurant")
    )
    private Restaurant restaurant;
}
