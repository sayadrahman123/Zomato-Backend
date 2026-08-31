package com.zomato.backend.service;

import com.zomato.backend.dto.request.RegisterPartnerRequest;
import com.zomato.backend.dto.request.UpdateLocationRequest;
import com.zomato.backend.entity.Delivery;
import com.zomato.backend.entity.DeliveryPartner;
import com.zomato.backend.entity.Order;
import com.zomato.backend.entity.User;
import com.zomato.backend.entity.enums.DeliveryStatus;
import com.zomato.backend.entity.enums.OrderStatus;
import com.zomato.backend.exception.BusinessException;
import com.zomato.backend.exception.ResourceNotFoundException;
import com.zomato.backend.model.PartnerLocation;
import com.zomato.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Business logic for the Delivery Partner module.
 *
 * Responsibilities:
 *  - Partner registration and profile management
 *  - Online / offline toggling (synced with Redis GEO)
 *  - Real-time location updates
 *  - Order assignment (auto nearest-partner or manual admin)
 *  - Delivery status progression (PICKED_UP → DELIVERED)
 *
 * Location data lives in Redis (LocationTrackingService).
 * Assignment history and status live in MySQL (DeliveryRepository).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private static final double DEFAULT_SEARCH_RADIUS_KM = 10.0;
    private static final int    NEARBY_PARTNER_LIMIT      = 20;

    private final DeliveryRepository        deliveryRepository;
    private final DeliveryPartnerRepository partnerRepository;
    private final OrderRepository           orderRepository;
    private final UserRepository            userRepository;
    private final LocationTrackingService   locationTrackingService;

    // ── Partner Registration ──────────────────────────────────────────────────

    /**
     * Creates a DeliveryPartner profile for an existing DELIVERY_PARTNER user.
     * isVerified defaults to false — admin must approve before going live.
     *
     * @param userId  JWT-extracted user ID (must have DELIVERY_PARTNER role)
     * @param request vehicleType + vehicleNumber
     */
    @Transactional
    public DeliveryPartner registerPartner(Long userId, RegisterPartnerRequest request) {
        if (partnerRepository.existsByUserId(userId)) {
            throw new BusinessException("A delivery partner profile already exists for this account.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        DeliveryPartner partner = DeliveryPartner.builder()
                .user(user)
                .vehicleType(request.vehicleType())
                .vehicleNumber(request.vehicleNumber().toUpperCase())
                .isVerified(false)
                .isAvailable(false)
                .isActive(true)
                .build();

        DeliveryPartner saved = partnerRepository.save(partner);
        log.info("Partner registered: partnerId={}, userId={}", saved.getId(), userId);
        return saved;
    }

    // ── Online / Offline ──────────────────────────────────────────────────────

    /**
     * Marks the partner as available and adds them to Redis GEO tracking.
     * Only verified, active partners can go online.
     *
     * @param partnerId the authenticated partner's ID
     * @param lat       current GPS latitude
     * @param lng       current GPS longitude
     */
    @Transactional
    public void goOnline(Long partnerId, Double lat, Double lng) {
        DeliveryPartner partner = getPartner(partnerId);

        if (!partner.getIsActive()) {
            throw new BusinessException("Your account has been suspended. Contact support.");
        }
        if (!partner.getIsVerified()) {
            throw new BusinessException("Your profile is pending admin verification.");
        }

        partner.setIsAvailable(true);
        partnerRepository.save(partner);

        // Register in Redis GEO
        locationTrackingService.updateLocation(partnerId, lat, lng);
        log.info("Partner online: partnerId={}", partnerId);
    }

    /**
     * Marks the partner as unavailable and removes them from Redis GEO.
     * Cannot go offline while a delivery is in progress (PICKED_UP status).
     */
    @Transactional
    public void goOffline(Long partnerId) {
        DeliveryPartner partner = getPartner(partnerId);

        // Guard: cannot go offline mid-delivery
        boolean hasActiveDelivery = deliveryRepository
                .findByPartnerIdAndStatus(partnerId, DeliveryStatus.PICKED_UP)
                .isPresent();
        if (hasActiveDelivery) {
            throw new BusinessException(
                    "You have an active delivery in progress. Complete it before going offline.");
        }

        partner.setIsAvailable(false);
        partnerRepository.save(partner);
        locationTrackingService.removePartner(partnerId);
        log.info("Partner offline: partnerId={}", partnerId);
    }

    // ── Location Update ───────────────────────────────────────────────────────

    /**
     * Updates the partner's real-time GPS location in Redis.
     * Also updates the DeliveryPartner entity columns for last known position.
     *
     * @param partnerId the authenticated partner's ID
     * @param request   { latitude, longitude }
     */
    @Transactional
    public void updateLocation(Long partnerId, UpdateLocationRequest request) {
        DeliveryPartner partner = getPartner(partnerId);

        if (!partner.getIsAvailable()) {
            throw new BusinessException("Go online before updating your location.");
        }

        // Persist to MySQL (last known position)
        partner.setCurrentLatitude(request.latitude());
        partner.setCurrentLongitude(request.longitude());
        partnerRepository.save(partner);

        // Update Redis GEO + metadata Hash
        locationTrackingService.updateLocation(partnerId, request.latitude(), request.longitude());
    }

    // ── Order Assignment ──────────────────────────────────────────────────────

    /**
     * Automatically assigns the nearest verified + available partner to an order.
     *
     * Flow:
     *  1. Load order and check it's in CONFIRMED status (restaurant accepted)
     *  2. Ensure no delivery already exists for this order
     *  3. Query Redis GEORADIUS near the restaurant's location
     *  4. Intersect with DB to get truly available partners
     *  5. Pick the top candidate (highest rating among nearest)
     *  6. Create a Delivery record and update Order status to OUT_FOR_DELIVERY
     *
     * @param orderId the order to assign
     * @return created Delivery entity
     */
    @Transactional
    public Delivery autoAssignDelivery(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PREPARING) {
            throw new BusinessException(
                    "Order must be CONFIRMED or PREPARING before assigning a delivery partner.");
        }

        if (deliveryRepository.existsByOrderId(orderId)) {
            throw new BusinessException("A delivery partner is already assigned to this order.");
        }

        // Delivery location = customer's delivery address
        Double lat = order.getDeliveryAddress().getLatitude();
        Double lng = order.getDeliveryAddress().getLongitude();

        if (lat == null || lng == null) {
            throw new BusinessException(
                    "Delivery address has no GPS coordinates — cannot auto-assign.");
        }

        // Find nearby partners via Redis GEO
        List<Long> nearbyIds = locationTrackingService.findNearbyPartnerIds(
                lat, lng, DEFAULT_SEARCH_RADIUS_KM, NEARBY_PARTNER_LIMIT
        );

        if (nearbyIds.isEmpty()) {
            throw new BusinessException(
                    "No delivery partners available nearby. Try again in a few minutes.");
        }

        // Intersect with DB: verified + available
        List<DeliveryPartner> candidates = partnerRepository.findAvailableByIds(nearbyIds);
        if (candidates.isEmpty()) {
            throw new BusinessException(
                    "No verified delivery partners available nearby at this time.");
        }

        // Pick the best candidate (already sorted by rating DESC in repo query)
        DeliveryPartner chosen = candidates.get(0);

        return createDeliveryAssignment(order, chosen);
    }

    /**
     * Admin manually assigns a specific partner to an order.
     *
     * @param orderId   the order to assign
     * @param partnerId the partner to assign
     */
    @Transactional
    public Delivery manualAssignDelivery(Long orderId, Long partnerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        if (deliveryRepository.existsByOrderId(orderId)) {
            throw new BusinessException("A delivery partner is already assigned to this order.");
        }

        DeliveryPartner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryPartner", "id", partnerId));

        if (!partner.getIsVerified() || !partner.getIsActive()) {
            throw new BusinessException("This partner is not eligible for delivery assignments.");
        }

        return createDeliveryAssignment(order, partner);
    }

    // ── Delivery Status Updates (Partner) ─────────────────────────────────────

    /**
     * Partner marks the order as PICKED_UP (collected from restaurant).
     * Records the pickup timestamp and partner's GPS coordinates at pickup.
     */
    @Transactional
    public Delivery markPickedUp(Long deliveryId, Long partnerId) {
        Delivery delivery = getDeliveryOwnedByPartner(deliveryId, partnerId);

        if (delivery.getStatus() != DeliveryStatus.ASSIGNED) {
            throw new BusinessException("Order must be ASSIGNED before marking as picked up.");
        }

        delivery.setStatus(DeliveryStatus.PICKED_UP);
        delivery.setPickedUpAt(LocalDateTime.now());

        // Snapshot partner's location at pickup time
        Optional<PartnerLocation> loc = locationTrackingService.getPartnerLocation(partnerId);
        loc.ifPresent(l -> {
            delivery.setPickupLatitude(l.getLatitude());
            delivery.setPickupLongitude(l.getLongitude());
        });

        // Link active order to location metadata
        locationTrackingService.setActiveOrder(partnerId, delivery.getOrder().getId());

        Delivery saved = deliveryRepository.save(delivery);
        log.info("Delivery picked up: deliveryId={}, partnerId={}", deliveryId, partnerId);
        return saved;
    }

    /**
     * Partner marks the order as DELIVERED.
     * Records delivered timestamp and increments partner's totalDeliveries.
     * Advances the linked Order status to DELIVERED.
     */
    @Transactional
    public Delivery markDelivered(Long deliveryId, Long partnerId) {
        Delivery delivery = getDeliveryOwnedByPartner(deliveryId, partnerId);

        if (delivery.getStatus() != DeliveryStatus.PICKED_UP) {
            throw new BusinessException("Order must be PICKED_UP before marking as delivered.");
        }

        delivery.setStatus(DeliveryStatus.DELIVERED);
        delivery.setDeliveredAt(LocalDateTime.now());

        // Advance the Order status
        Order order = delivery.getOrder();
        order.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);

        // Increment partner's delivery count
        DeliveryPartner partner = delivery.getPartner();
        partner.setTotalDeliveries(partner.getTotalDeliveries() + 1);
        partner.setIsAvailable(true);      // auto-available for next delivery
        partnerRepository.save(partner);

        // Clear active order from Redis
        locationTrackingService.setActiveOrder(partnerId, null);

        Delivery saved = deliveryRepository.save(delivery);
        log.info("Delivery completed: deliveryId={}, partnerId={}, orderId={}",
                deliveryId, partnerId, order.getId());
        return saved;
    }

    /**
     * Partner marks a delivery as FAILED (could not deliver).
     */
    @Transactional
    public Delivery markFailed(Long deliveryId, Long partnerId, String notes) {
        Delivery delivery = getDeliveryOwnedByPartner(deliveryId, partnerId);

        if (delivery.getStatus() == DeliveryStatus.DELIVERED
                || delivery.getStatus() == DeliveryStatus.FAILED) {
            throw new BusinessException("Cannot update a terminal delivery status.");
        }

        delivery.setStatus(DeliveryStatus.FAILED);
        delivery.setPartnerNotes(notes);
        locationTrackingService.setActiveOrder(partnerId, null);

        return deliveryRepository.save(delivery);
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Partner's delivery history — paginated, newest first.
     */
    @Transactional(readOnly = true)
    public Page<Delivery> getMyDeliveries(Long partnerId, int page, int size) {
        size = Math.min(size, 20);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return deliveryRepository.findByPartnerIdOrderByCreatedAtDesc(partnerId, pageable);
    }

    /**
     * Fetch delivery assignment for a specific order.
     */
    @Transactional(readOnly = true)
    public Optional<Delivery> getDeliveryForOrder(Long orderId) {
        return deliveryRepository.findByOrderId(orderId);
    }

    // ── Internal Helpers ──────────────────────────────────────────────────────

    /** Creates the Delivery row and updates the partner's availability. */
    private Delivery createDeliveryAssignment(Order order, DeliveryPartner partner) {
        Delivery delivery = Delivery.builder()
                .order(order)
                .partner(partner)
                .status(DeliveryStatus.ASSIGNED)
                .assignedAt(LocalDateTime.now())
                .build();

        // Partner is now occupied
        partner.setIsAvailable(false);
        partnerRepository.save(partner);

        Delivery saved = deliveryRepository.save(delivery);
        log.info("Delivery assigned: orderId={}, partnerId={}, deliveryId={}",
                order.getId(), partner.getId(), saved.getId());
        return saved;
    }

    private DeliveryPartner getPartner(Long partnerId) {
        return partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryPartner", "id", partnerId));
    }

    private Delivery getDeliveryOwnedByPartner(Long deliveryId, Long partnerId) {
        Delivery delivery = deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", "id", deliveryId));
        if (!delivery.getPartner().getId().equals(partnerId)) {
            throw new BusinessException("This delivery does not belong to you.");
        }
        return delivery;
    }
}
