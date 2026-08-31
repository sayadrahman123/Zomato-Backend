package com.zomato.backend.repository;

import com.zomato.backend.entity.Order;
import com.zomato.backend.entity.enums.OrderStatus;
import com.zomato.backend.entity.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link Order} entity.
 *
 * Three actor perspectives:
 *  - Customer  : their own order history + single order detail
 *  - Restaurant: incoming orders filtered by status
 *  - Admin     : platform-wide order visibility
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // ── Customer queries ──────────────────────────────────────────────────────

    /**
     * Paginated order history for a customer, newest first.
     * Used on the "My Orders" page.
     */
    Page<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    /**
     * A specific order that belongs to the given customer.
     * Guards the order detail endpoint — customers can only view their own orders.
     */
    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);

    /**
     * Load order by human-readable order number for a specific customer.
     * Used in customer support lookups.
     */
    Optional<Order> findByOrderNumberAndCustomerId(String orderNumber, Long customerId);

    // ── Restaurant queries ─────────────────────────────────────────────────────

    /**
     * All orders for a restaurant, newest first.
     * Used on the restaurant's order management dashboard.
     */
    Page<Order> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);

    /**
     * Orders for a restaurant filtered by status.
     * Example: status=PENDING → incoming orders requiring confirmation.
     */
    Page<Order> findByRestaurantIdAndStatusOrderByCreatedAtDesc(
            Long restaurantId, OrderStatus status, Pageable pageable
    );

    /**
     * A specific order that belongs to the given restaurant.
     * Guards the order status update endpoint.
     */
    Optional<Order> findByIdAndRestaurantId(Long id, Long restaurantId);

    /**
     * Count of active (non-terminal) orders for a restaurant.
     * Used for the restaurant's live order badge/counter.
     */
    @Query("""
            SELECT COUNT(o) FROM Order o
            WHERE o.restaurant.id = :restaurantId
              AND o.status NOT IN ('DELIVERED', 'CANCELLED')
            """)
    long countActiveOrdersByRestaurant(@Param("restaurantId") Long restaurantId);

    /**
     * Count of orders in a specific status for a restaurant.
     * Used for dashboard stats (e.g., "3 orders pending confirmation").
     */
    long countByRestaurantIdAndStatus(Long restaurantId, OrderStatus status);

    // ── General / Admin ───────────────────────────────────────────────────────

    /**
     * Find an order by its human-readable order number.
     * Used by admin support for cross-customer lookups.
     */
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * Checks if an order number already exists.
     * Used during order number generation to guarantee uniqueness.
     */
    boolean existsByOrderNumber(String orderNumber);

    /**
     * All orders filtered by payment status (admin use).
     * Example: paymentStatus=FAILED → payments that need investigation.
     */
    Page<Order> findByPaymentStatus(PaymentStatus paymentStatus, Pageable pageable);

    /**
     * All orders filtered by order status (admin use).
     */
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);
}
