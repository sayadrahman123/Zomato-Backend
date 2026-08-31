package com.zomato.backend.repository;

import com.zomato.backend.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for {@link OrderItem} entity.
 *
 * Kept minimal because order items are almost always accessed
 * via their parent Order (CascadeType.ALL + eager/lazy collection).
 *
 * Direct item queries are only needed for:
 *  - Analytics (most ordered items)
 *  - Cases where we need items without loading the full Order graph
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * All items for a given order.
     * Alternative to loading items through Order.getOrderItems()
     * when the Order entity is not already loaded.
     */
    List<OrderItem> findByOrderId(Long orderId);

    /**
     * All order items referencing a specific menu item.
     * Used for analytics: "how many times was item X ordered?"
     */
    List<OrderItem> findByMenuItemId(Long menuItemId);

    /**
     * Count of times a menu item has been ordered across all orders.
     */
    long countByMenuItemId(Long menuItemId);
}
