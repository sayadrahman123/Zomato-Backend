package com.zomato.backend.service;

import com.zomato.backend.audit.AuditAction;
import com.zomato.backend.dto.request.PlaceOrderRequest;
import com.zomato.backend.dto.request.UpdateOrderStatusRequest;
import com.zomato.backend.dto.response.OrderResponse;
import com.zomato.backend.dto.response.OrderSummaryResponse;
import com.zomato.backend.entity.*;
import com.zomato.backend.entity.enums.OrderStatus;
import com.zomato.backend.entity.enums.PaymentStatus;
import com.zomato.backend.exception.BusinessException;
import com.zomato.backend.exception.ResourceNotFoundException;
import com.zomato.backend.mapper.OrderMapper;
import com.zomato.backend.model.CartItem;
import com.zomato.backend.repository.*;
import com.zomato.backend.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Business logic for the Order module.
 *
 * placeOrder flow:
 *  1. Load & validate the customer's Redis cart
 *  2. Validate the restaurant (active + open)
 *  3. Build Order + OrderItems from CartItems (price snapshots)
 *  4. Generate a unique human-readable order number
 *  5. Persist to MySQL
 *  6. Clear the Redis cart
 *  7. Return OrderResponse
 *
 * State machine:
 *  PENDING → CONFIRMED → PREPARING → OUT_FOR_DELIVERY → DELIVERED
 *  PENDING / CONFIRMED → CANCELLED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final BigDecimal DELIVERY_FEE    = new BigDecimal("30.00");
    private static final int        MAX_ORDER_NUM_RETRIES = 5;

    private final OrderRepository      orderRepository;
    private final UserRepository       userRepository;
    private final RestaurantRepository restaurantRepository;
    private final CartService          cartService;
    private final OrderMapper          orderMapper;

    // ── Place Order ───────────────────────────────────────────────────────────

    /**
     * Converts the customer's Redis cart into a persisted Order.
     *
     * @param customerId JWT-extracted customer ID
     * @param request    delivery address + payment method + notes
     * @return saved OrderResponse (confirmation page)
     * @throws BusinessException if cart is empty, restaurant is closed, etc.
     */
    @Transactional
    public OrderResponse placeOrder(Long customerId, PlaceOrderRequest request) {

        // ── 1. Load cart ──────────────────────────────────────────────────────
        var cart = cartService.getCart(customerId);
        if (cart.items() == null || cart.items().isEmpty()) {
            throw new BusinessException("Your cart is empty. Add items before placing an order.");
        }

        // ── 2. Load and validate entities ─────────────────────────────────────
        User user = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", customerId));

        Long restaurantId = cart.restaurantId();
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        if (!restaurant.getIsActive()) {
            throw new BusinessException("This restaurant is not currently accepting orders.");
        }
        if (!restaurant.getIsOpen()) {
            throw new BusinessException("'" + restaurant.getName() + "' is currently closed.");
        }

        // ── 3. Build DeliveryAddress snapshot ─────────────────────────────────
        var addr = request.deliveryAddress();
        DeliveryAddress deliveryAddress = DeliveryAddress.builder()
                .street(addr.street())
                .area(addr.area())
                .city(addr.city())
                .state(addr.state())
                .pincode(addr.pincode())
                .latitude(addr.latitude())
                .longitude(addr.longitude())
                .build();

        // ── 4. Build Order ────────────────────────────────────────────────────
        Order order = Order.builder()
                .orderNumber(generateUniqueOrderNumber())
                .customer(user)
                .restaurant(restaurant)
                .status(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(request.paymentMethod().toUpperCase())
                .deliveryAddress(deliveryAddress)
                .deliveryFee(DELIVERY_FEE)
                .specialInstructions(request.specialInstructions())
                .build();

        // ── 5. Build OrderItems from CartItems ────────────────────────────────
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem cartItem : cart.items()) {
            BigDecimal lineTotal = cartItem.getEffectivePrice()
                    .multiply(BigDecimal.valueOf(cartItem.getQuantity()));

            OrderItem orderItem = OrderItem.builder()
                    .itemName(cartItem.getItemName())
                    .price(cartItem.getEffectivePrice())     // snapshot
                    .quantity(cartItem.getQuantity())
                    .lineTotal(lineTotal)
                    .build();

            // Try to link the MenuItem entity for analytics (best-effort)
            try {
                orderItem.setMenuItem(
                    new MenuItem() {{ setId(cartItem.getItemId()); }}
                );
            } catch (Exception ignored) { /* leave null if lookup fails */ }

            order.addOrderItem(orderItem);
            subtotal = subtotal.add(lineTotal);
        }

        order.setSubtotal(subtotal);
        order.setTotalAmount(subtotal.add(order.getDeliveryFee()));

        // ── 6. Persist ────────────────────────────────────────────────────────
        Order saved = orderRepository.save(order);

        // ── 7. Clear cart ──────────────────────────────────────────────────────
        cartService.clearCart(customerId);

        log.info("Order placed: orderNumber={}, customerId={}, restaurantId={}, total={}",
                saved.getOrderNumber(), customerId, restaurantId, saved.getTotalAmount());

        return orderMapper.toOrderResponse(saved);
    }

    // ── Get Order (Customer) ──────────────────────────────────────────────────

    /**
     * Returns a specific order — only if it belongs to the customer.
     */
    @Transactional(readOnly = true)
    public OrderResponse getMyOrder(Long orderId, Long customerId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));
        return orderMapper.toOrderResponse(order);
    }

    /**
     * Paginated order history for the authenticated customer.
     */
    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getMyOrders(Long customerId, int page, int size) {
        Pageable pageable = PaginationUtils.createPageable(page, size, 20, Sort.by("createdAt").descending());
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(orderMapper::toOrderSummaryResponse);
    }

    // ── Get Orders (Restaurant) ───────────────────────────────────────────────

    /**
     * Paginated orders for a restaurant, filtered by status.
     * Only the restaurant owner can call this.
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> getRestaurantOrders(
            Long restaurantId, OrderStatus status, Long ownerId, int page, int size
    ) {
        verifyRestaurantOwnership(restaurantId, ownerId);
        Pageable pageable = PaginationUtils.createPageable(page, size, 20, Sort.by("createdAt").descending());

        Page<Order> orders = (status != null)
                ? orderRepository.findByRestaurantIdAndStatusOrderByCreatedAtDesc(
                        restaurantId, status, pageable)
                : orderRepository.findByRestaurantIdOrderByCreatedAtDesc(
                        restaurantId, pageable);

        return orders.map(orderMapper::toOrderResponse);
    }

    // ── Update Order Status (Restaurant State Machine) ────────────────────────

    /**
     * Advances an order through its lifecycle.
     * Only the owning restaurant can update status.
     *
     * Valid transitions (Step 5.6 state machine):
     *  PENDING           → CONFIRMED / CANCELLED
     *  CONFIRMED         → PREPARING / CANCELLED
     *  PREPARING         → OUT_FOR_DELIVERY
     *  OUT_FOR_DELIVERY  → DELIVERED
     *  DELIVERED         → (terminal — no further transitions)
     *  CANCELLED         → (terminal — no further transitions)
     *
     * @param orderId   the order to update
     * @param request   { status: NEW_STATUS }
     * @param ownerId   JWT-extracted owner ID
     */
    @AuditAction(action = "ORDER_STATUS_UPDATED", resourceType = "ORDER")
    @Transactional
    public OrderResponse updateOrderStatus(
            Long orderId, UpdateOrderStatusRequest request, Long ownerId
    ) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        // Ownership: this order must belong to the owner's restaurant
        verifyRestaurantOwnership(order.getRestaurant().getId(), ownerId);

        // State machine validation
        validateStatusTransition(order.getStatus(), request.status());

        order.setStatus(request.status());

        // If delivered with COD → mark payment as PAID
        if (request.status() == OrderStatus.DELIVERED
                && "COD".equalsIgnoreCase(order.getPaymentMethod())) {
            order.setPaymentStatus(PaymentStatus.PAID);
        }

        Order updated = orderRepository.save(order);
        log.info("Order {} status updated: {} → {}", order.getOrderNumber(),
                order.getStatus(), request.status());

        return orderMapper.toOrderResponse(updated);
    }

    // ── Cancel Order (Customer) ───────────────────────────────────────────────

    /**
     * Customer cancels their own order.
     * Only allowed while the order is still PENDING or CONFIRMED.
     */
    @AuditAction(action = "ORDER_CANCELLED", resourceType = "ORDER")
    @Transactional
    public OrderResponse cancelOrder(Long orderId, Long customerId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        validateStatusTransition(order.getStatus(), OrderStatus.CANCELLED);
        order.setStatus(OrderStatus.CANCELLED);
        Order updated = orderRepository.save(order);

        log.info("Order {} cancelled by customer {}", order.getOrderNumber(), customerId);
        return orderMapper.toOrderResponse(updated);
    }

    // ── Internal Helpers ──────────────────────────────────────────────────────

    /**
     * State machine — throws BusinessException on invalid transition.
     */
    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        if (current == OrderStatus.DELIVERED || current == OrderStatus.CANCELLED) {
            throw new BusinessException(
                    "Order is already " + current.name().toLowerCase() +
                    " and cannot be changed.");
        }

        boolean valid = switch (current) {
            case PENDING   -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.PREPARING || next == OrderStatus.CANCELLED;
            case PREPARING -> next == OrderStatus.OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> next == OrderStatus.DELIVERED;
            default -> false;
        };

        if (!valid) {
            throw new BusinessException(
                    "Cannot transition order from " + current.name() +
                    " to " + next.name() + ".");
        }
    }

    /**
     * Verifies the authenticated user owns the restaurant.
     * Throws BusinessException if not.
     */
    private void verifyRestaurantOwnership(Long restaurantId, Long ownerId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));
        if (!restaurant.getOwner().getId().equals(ownerId)) {
            throw new BusinessException("You do not have permission to manage this restaurant's orders.");
        }
    }

    /**
     * Generates a unique, human-readable order number.
     *
     * Format: "ORD-{YYYYMMDD}-{6-char-uppercase-alphanumeric}"
     * Example: "ORD-20240831-AB12CD"
     *
     * Retries up to MAX_ORDER_NUM_RETRIES times on collision (extremely rare).
     */
    private String generateUniqueOrderNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        for (int i = 0; i < MAX_ORDER_NUM_RETRIES; i++) {
            String suffix = UUID.randomUUID().toString()
                    .replace("-", "")
                    .substring(0, 6)
                    .toUpperCase();
            String candidate = "ORD-" + date + "-" + suffix;
            if (!orderRepository.existsByOrderNumber(candidate)) {
                return candidate;
            }
        }
        // Fallback: full UUID (won't collide)
        return "ORD-" + date + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }
}
