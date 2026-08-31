package com.zomato.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * A single line item within an order — snapshot of the menu item at purchase time.
 *
 * Why snapshot itemName and price?
 *   If the restaurant later changes the item's name or price,
 *   the order history must reflect what the customer actually paid.
 *   Storing a live FK to MenuItem alone would cause order history to
 *   "drift" whenever the restaurant edits their menu.
 *
 * MenuItem FK is kept for analytics (e.g., "most ordered items")
 * but it's nullable to handle the case where the item is hard-deleted.
 */
@Entity
@Table(
    name = "order_items",
    indexes = {
        @Index(name = "idx_order_item_order",    columnList = "order_id"),
        @Index(name = "idx_order_item_menu_item", columnList = "menu_item_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Relationship ──────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "order_id",
        nullable = false,
        foreignKey = @ForeignKey(name = "fk_order_item_order")
    )
    private Order order;

    /**
     * Nullable — item may be soft-deleted from the menu after the order was placed.
     * Analytics can still use this FK as long as the item exists.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "menu_item_id",
        foreignKey = @ForeignKey(name = "fk_order_item_menu_item")
    )
    private MenuItem menuItem;

    // ── Snapshots (captured at order-placement time) ───────────────────────────

    @Column(name = "item_name", nullable = false, length = 150)
    private String itemName;

    /**
     * Price the customer actually paid (= discountedPrice if applicable).
     * Precision: 10 digits, 2 decimal places.
     */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer quantity;

    /**
     * price × quantity — pre-computed and stored to avoid re-computation
     * in reports and receipts (price could change after order).
     */
    @Column(name = "line_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal lineTotal;
}
