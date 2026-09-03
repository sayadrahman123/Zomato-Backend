package com.zomato.backend.controller;

import com.zomato.backend.dto.request.RegisterPartnerRequest;
import com.zomato.backend.dto.request.UpdateLocationRequest;
import com.zomato.backend.dto.response.ApiResponse;
import com.zomato.backend.dto.response.PagedResponse;
import com.zomato.backend.entity.Delivery;
import com.zomato.backend.entity.DeliveryPartner;
import com.zomato.backend.model.PartnerLocation;
import com.zomato.backend.service.DeliveryService;
import com.zomato.backend.service.LocationTrackingService;
import com.zomato.backend.util.AuthUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Delivery partner endpoints.
 *
 * Base path: /api/delivery
 *
 * Access rules:
 *   DELIVERY_PARTNER : register, go online/offline, update location,
 *                      view own deliveries, mark picked-up/delivered/failed
 *   ADMIN            : manual assignment, auto-assign trigger,
 *                      view pending partner verifications
 *   CUSTOMER         : view delivery status for their order
 */
@RestController
@RequestMapping("/api/delivery")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Delivery", description = "Delivery partner registration, location tracking, and delivery lifecycle")
public class DeliveryController {

    private final DeliveryService          deliveryService;
    private final LocationTrackingService  locationTrackingService;
    private final AuthUtils                authUtils;

    // ══════════════════════════════════════════════════════════════════════════
    // PARTNER REGISTRATION
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "Register as a delivery partner",
        description = "Creates a delivery partner profile for the authenticated DELIVERY_PARTNER user. " +
                      "Admin must approve (verify) the profile before the partner can go online."
    )
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<DeliveryPartner>> registerPartner(
            @Valid @RequestBody RegisterPartnerRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = authUtils.getCurrentUserId(httpRequest);
        DeliveryPartner partner = deliveryService.registerPartner(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Partner profile created. Awaiting admin verification.", partner));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ONLINE / OFFLINE TOGGLE
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "Go online",
        description = "Marks the partner as available and registers them in Redis GEO for assignment. " +
                      "Requires isVerified=true (admin approved)."
    )
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @PatchMapping("/online")
    public ResponseEntity<ApiResponse<Void>> goOnline(
            @Valid @RequestBody UpdateLocationRequest request,
            HttpServletRequest httpRequest
    ) {
        Long partnerId = authUtils.getCurrentUserId(httpRequest);
        deliveryService.goOnline(partnerId, request.latitude(), request.longitude());
        return ResponseEntity.ok(ApiResponse.success("You are now online and available for deliveries"));
    }

    @Operation(
        summary     = "Go offline",
        description = "Marks the partner as unavailable and removes them from Redis GEO. " +
                      "Cannot go offline while a delivery is in progress."
    )
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @PatchMapping("/offline")
    public ResponseEntity<ApiResponse<Void>> goOffline(HttpServletRequest httpRequest) {
        Long partnerId = authUtils.getCurrentUserId(httpRequest);
        deliveryService.goOffline(partnerId);
        return ResponseEntity.ok(ApiResponse.success("You are now offline"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOCATION UPDATE
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "Update real-time location",
        description = "Called by the partner's app every N seconds while online. " +
                      "Updates both Redis GEO and the MySQL last-known-position columns."
    )
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @PatchMapping("/location")
    public ResponseEntity<ApiResponse<Void>> updateLocation(
            @Valid @RequestBody UpdateLocationRequest request,
            HttpServletRequest httpRequest
    ) {
        Long partnerId = authUtils.getCurrentUserId(httpRequest);
        deliveryService.updateLocation(partnerId, request);
        return ResponseEntity.ok(ApiResponse.success("Location updated"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DELIVERY STATUS UPDATES (PARTNER)
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "Mark order as picked up",
        description = "Partner confirms collection from the restaurant. " +
                      "Records pickup timestamp and GPS coordinates."
    )
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @PatchMapping("/{deliveryId}/picked-up")
    public ResponseEntity<ApiResponse<Delivery>> markPickedUp(
            @PathVariable Long deliveryId,
            HttpServletRequest httpRequest
    ) {
        Long partnerId = authUtils.getCurrentUserId(httpRequest);
        Delivery delivery = deliveryService.markPickedUp(deliveryId, partnerId);
        return ResponseEntity.ok(ApiResponse.success("Order marked as picked up", delivery));
    }

    @Operation(
        summary     = "Mark order as delivered",
        description = "Partner confirms delivery to the customer. " +
                      "Order status advances to DELIVERED. Partner's delivery count increments."
    )
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @PatchMapping("/{deliveryId}/delivered")
    public ResponseEntity<ApiResponse<Delivery>> markDelivered(
            @PathVariable Long deliveryId,
            HttpServletRequest httpRequest
    ) {
        Long partnerId = authUtils.getCurrentUserId(httpRequest);
        Delivery delivery = deliveryService.markDelivered(deliveryId, partnerId);
        return ResponseEntity.ok(ApiResponse.success("Order delivered successfully", delivery));
    }

    @Operation(
        summary     = "Mark delivery as failed",
        description = "Partner reports a failed delivery (customer unreachable, wrong address, etc.). " +
                      "Notes are required for customer support investigation."
    )
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @PatchMapping("/{deliveryId}/failed")
    public ResponseEntity<ApiResponse<Delivery>> markFailed(
            @PathVariable Long deliveryId,
            @RequestParam @NotBlank(message = "Failure notes are required") String notes,
            HttpServletRequest httpRequest
    ) {
        Long partnerId = authUtils.getCurrentUserId(httpRequest);
        Delivery delivery = deliveryService.markFailed(deliveryId, partnerId, notes);
        return ResponseEntity.ok(ApiResponse.success("Delivery marked as failed", delivery));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // PARTNER HISTORY (PARTNER)
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "My delivery history",
        description = "Returns the authenticated partner's paginated delivery history, newest first."
    )
    @PreAuthorize("hasRole('DELIVERY_PARTNER')")
    @GetMapping("/my-deliveries")
    public ResponseEntity<ApiResponse<PagedResponse<Delivery>>> getMyDeliveries(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest httpRequest
    ) {
        Long partnerId = authUtils.getCurrentUserId(httpRequest);
        Page<Delivery> deliveries = deliveryService.getMyDeliveries(partnerId, page, size);
        return ResponseEntity.ok(ApiResponse.paged("Delivery history fetched", deliveries));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CUSTOMER — VIEW DELIVERY TRACKING
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "Track delivery for my order",
        description = "Returns the delivery assignment and partner's live location for a customer's order."
    )
    @PreAuthorize("hasRole('CUSTOMER')")
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PartnerLocation>> trackDelivery(
            @PathVariable Long orderId
    ) {
        Optional<Delivery> delivery = deliveryService.getDeliveryForOrder(orderId);
        if (delivery.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success("No delivery partner assigned yet", null));
        }

        Long partnerId = delivery.get().getPartner().getId();
        Optional<PartnerLocation> location = locationTrackingService.getPartnerLocation(partnerId);
        return ResponseEntity.ok(ApiResponse.success(
                "Partner location fetched",
                location.orElse(null)
        ));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ADMIN ENDPOINTS
    // ══════════════════════════════════════════════════════════════════════════

    @Operation(
        summary     = "Auto-assign a delivery partner",
        description = "Admin triggers automatic assignment of the nearest available partner to an order."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/assign/{orderId}/auto")
    public ResponseEntity<ApiResponse<Delivery>> autoAssign(
            @PathVariable Long orderId
    ) {
        Delivery delivery = deliveryService.autoAssignDelivery(orderId);
        return ResponseEntity.ok(ApiResponse.success("Partner auto-assigned successfully", delivery));
    }

    @Operation(
        summary     = "Manually assign a delivery partner",
        description = "Admin assigns a specific verified partner to an order."
    )
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/assign/{orderId}/partner/{partnerId}")
    public ResponseEntity<ApiResponse<Delivery>> manualAssign(
            @PathVariable Long orderId,
            @PathVariable Long partnerId
    ) {
        Delivery delivery = deliveryService.manualAssignDelivery(orderId, partnerId);
        return ResponseEntity.ok(ApiResponse.success("Partner assigned successfully", delivery));
    }
}
