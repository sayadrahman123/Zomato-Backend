package com.zomato.backend.controller;

import com.zomato.backend.dto.request.PlaceOrderRequest;
import com.zomato.backend.dto.request.UpdateOrderStatusRequest;
import com.zomato.backend.dto.response.ApiResponse;
import com.zomato.backend.dto.response.OrderResponse;
import com.zomato.backend.dto.response.OrderSummaryResponse;
import com.zomato.backend.entity.enums.OrderStatus;
import com.zomato.backend.service.OrderService;
import com.zomato.backend.util.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Order lifecycle endpoints.
 *
 * Base path: /api/orders
 *
 * Access rules:
 *   CUSTOMER         : place, view own orders, cancel own order
 *   RESTAURANT_OWNER : view restaurant orders, advance order status
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Orders", description = "Order placement, history, status updates, and cancellations")
public class OrderController {

    private final OrderService orderService;
    private final AuthUtils    authUtils;

    // ══════════════════════════════════════════════════════════════════════════
    // CUSTOMER ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "Place an order",
        description = "Converts the customer's Redis cart into a persisted order. " +
                      "Requires a non-empty cart. Cart is cleared on success."
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request,
            HttpServletRequest httpRequest
    ) {
        Long customerId = authUtils.getCurrentUserId(httpRequest);
        OrderResponse order = orderService.placeOrder(customerId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed successfully", order));
    }

    @Operation(
        summary     = "Get my order history",
        description = "Returns a paginated list of the authenticated customer's past orders, " +
                      "newest first."
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<OrderSummaryResponse>>> getMyOrders(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest
    ) {
        Long customerId = authUtils.getCurrentUserId(httpRequest);
        Page<OrderSummaryResponse> orders = orderService.getMyOrders(customerId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Orders fetched successfully", orders));
    }

    @Operation(
        summary     = "Get a specific order",
        description = "Returns full order details. Customers can only view their own orders."
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getMyOrder(
            @PathVariable Long orderId,
            HttpServletRequest httpRequest
    ) {
        Long customerId = authUtils.getCurrentUserId(httpRequest);
        OrderResponse order = orderService.getMyOrder(orderId, customerId);
        return ResponseEntity.ok(ApiResponse.success("Order fetched successfully", order));
    }

    @Operation(
        summary     = "Cancel an order",
        description = "Customer cancels their own order. " +
                      "Only allowed while the order is PENDING or CONFIRMED."
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    @PatchMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(
            @PathVariable Long orderId,
            HttpServletRequest httpRequest
    ) {
        Long customerId = authUtils.getCurrentUserId(httpRequest);
        OrderResponse order = orderService.cancelOrder(orderId, customerId);
        return ResponseEntity.ok(ApiResponse.success("Order cancelled successfully", order));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RESTAURANT OWNER ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "Get orders for a restaurant",
        description = "Returns paginated orders for the authenticated owner's restaurant. " +
                      "Optional 'status' filter (e.g. status=PENDING for the incoming order queue)."
    )
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getRestaurantOrders(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest
    ) {
        Long ownerId = authUtils.getCurrentUserId(httpRequest);
        Page<OrderResponse> orders =
                orderService.getRestaurantOrders(restaurantId, status, ownerId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Restaurant orders fetched", orders));
    }

    @Operation(
        summary     = "Update order status",
        description = "Restaurant owner advances the order through its lifecycle. " +
                      "Valid transitions: PENDING→CONFIRMED, CONFIRMED→PREPARING, " +
                      "PREPARING→OUT_FOR_DELIVERY, OUT_FOR_DELIVERY→DELIVERED. " +
                      "PENDING or CONFIRMED orders can be CANCELLED."
    )
    @PreAuthorize("hasRole('RESTAURANT_OWNER')")
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            HttpServletRequest httpRequest
    ) {
        Long ownerId = authUtils.getCurrentUserId(httpRequest);
        OrderResponse order = orderService.updateOrderStatus(orderId, request, ownerId);
        return ResponseEntity.ok(ApiResponse.success(
                "Order status updated to " + order.status().name(), order));
    }
}
