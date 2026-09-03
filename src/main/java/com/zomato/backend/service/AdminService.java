package com.zomato.backend.service;

import com.zomato.backend.audit.AuditAction;
import com.zomato.backend.dto.response.UserResponse;
import com.zomato.backend.entity.DeliveryPartner;
import com.zomato.backend.entity.Restaurant;
import com.zomato.backend.entity.User;
import com.zomato.backend.entity.enums.UserRole;
import com.zomato.backend.exception.BusinessException;
import com.zomato.backend.exception.ResourceNotFoundException;
import com.zomato.backend.mapper.UserMapper;
import com.zomato.backend.repository.DeliveryPartnerRepository;
import com.zomato.backend.repository.RestaurantRepository;
import com.zomato.backend.repository.UserRepository;
import com.zomato.backend.util.PaginationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-only business logic.
 *
 * Three responsibility areas:
 *  1. User management   — list, ban, unban, change role
 *  2. Restaurant moderation — list pending, approve, reject
 *  3. Delivery partner  — list unverified, verify, reject
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository            userRepository;
    private final RestaurantRepository      restaurantRepository;
    private final DeliveryPartnerRepository partnerRepository;
    private final UserMapper                userMapper;
    private final UserProfileCacheService   profileCacheService;

    // ══════════════════════════════════════════════════════════════════════════
    // USER MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════════

    /** Paginated list of all platform users, newest first. */
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(int page, int size) {
        Pageable pageable = PaginationUtils.createPageable(page, size, 50, Sort.by("createdAt").descending());
        return userRepository.findAll(pageable).map(userMapper::toUserResponse);
    }

    /** Fetch a single user by ID. */
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        return userMapper.toUserResponse(user);
    }

    /**
     * Bans a user — sets isActive=false.
     * Banned users cannot authenticate (checked in UserDetailsServiceImpl).
     * Admins cannot ban other admins.
     */
    @AuditAction(action = "USER_BANNED", resourceType = "USER")
    @Transactional
    public UserResponse banUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (user.getRole() == UserRole.ADMIN) {
            throw new BusinessException("Admin accounts cannot be banned.");
        }
        if (!user.getIsActive()) {
            throw new BusinessException("User is already banned.");
        }

        user.setIsActive(false);
        User saved = userRepository.save(user);
        profileCacheService.evict(userId);
        log.info("User banned by admin: userId={}", userId);
        return userMapper.toUserResponse(saved);
    }

    /** Restores a previously banned user's account. */
    @AuditAction(action = "USER_UNBANNED", resourceType = "USER")
    @Transactional
    public UserResponse unbanUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (user.getIsActive()) {
            throw new BusinessException("User is not banned.");
        }

        user.setIsActive(true);
        User saved = userRepository.save(user);
        profileCacheService.evict(userId);
        log.info("User unbanned by admin: userId={}", userId);
        return userMapper.toUserResponse(saved);
    }

    /**
     * Changes a user's platform role.
     * Prevents changing an ADMIN's own role.
     */
    @AuditAction(action = "USER_ROLE_CHANGED", resourceType = "USER")
    @Transactional
    public UserResponse changeUserRole(Long userId, UserRole newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        if (user.getRole() == UserRole.ADMIN && newRole != UserRole.ADMIN) {
            throw new BusinessException("Cannot demote an admin account via this endpoint.");
        }
        if (user.getRole() == newRole) {
            throw new BusinessException("User already has the role: " + newRole.name());
        }

        user.setRole(newRole);
        User saved = userRepository.save(user);
        profileCacheService.evict(userId);
        log.info("User role changed: userId={}, newRole={}", userId, newRole);
        return userMapper.toUserResponse(saved);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RESTAURANT MODERATION
    // ══════════════════════════════════════════════════════════════════════════

    /** Restaurants awaiting admin approval (isActive=false), newest first. */
    @Transactional(readOnly = true)
    public Page<Restaurant> getPendingRestaurants(int page, int size) {
        Pageable pageable = PaginationUtils.createPageable(page, size, 20, Sort.by("createdAt").descending());
        return restaurantRepository.findByIsActive(false, pageable);
    }

    /**
     * Approves a restaurant — sets isActive=true.
     * The restaurant becomes visible to customers in search.
     */
    @AuditAction(action = "RESTAURANT_APPROVED", resourceType = "RESTAURANT")
    @Transactional
    public Restaurant approveRestaurant(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        if (restaurant.getIsActive()) {
            throw new BusinessException("Restaurant is already approved.");
        }

        restaurant.setIsActive(true);
        Restaurant saved = restaurantRepository.save(restaurant);
        log.info("Restaurant approved: restaurantId={}", restaurantId);
        return saved;
    }

    /**
     * Rejects / suspends a restaurant — sets isActive=false.
     * Used for new restaurant rejection or post-approval suspension.
     */
    @AuditAction(action = "RESTAURANT_REJECTED", resourceType = "RESTAURANT")
    @Transactional
    public Restaurant rejectRestaurant(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        restaurant.setIsActive(false);
        restaurant.setIsOpen(false);    // close it immediately
        Restaurant saved = restaurantRepository.save(restaurant);
        log.info("Restaurant rejected/suspended: restaurantId={}", restaurantId);
        return saved;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DELIVERY PARTNER VERIFICATION
    // ══════════════════════════════════════════════════════════════════════════

    /** Delivery partners awaiting document verification (isVerified=false), newest first. */
    @Transactional(readOnly = true)
    public Page<DeliveryPartner> getPendingPartners(int page, int size) {
        Pageable pageable = PaginationUtils.createPageable(page, size, 20, Sort.by("createdAt").descending());
        return partnerRepository.findByIsVerifiedFalseAndIsActiveTrue(pageable);
    }

    /**
     * Verifies a delivery partner — sets isVerified=true.
     * The partner can now go online and receive delivery assignments.
     */
    @AuditAction(action = "PARTNER_VERIFIED", resourceType = "PARTNER")
    @Transactional
    public DeliveryPartner verifyPartner(Long partnerId) {
        DeliveryPartner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryPartner", "id", partnerId));

        if (partner.getIsVerified()) {
            throw new BusinessException("Delivery partner is already verified.");
        }

        partner.setIsVerified(true);
        DeliveryPartner saved = partnerRepository.save(partner);
        log.info("Delivery partner verified: partnerId={}", partnerId);
        return saved;
    }

    /**
     * Suspends a delivery partner — sets isActive=false.
     * Suspended partners cannot go online or accept deliveries.
     */
    @AuditAction(action = "PARTNER_SUSPENDED", resourceType = "PARTNER")
    @Transactional
    public DeliveryPartner suspendPartner(Long partnerId) {
        DeliveryPartner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryPartner", "id", partnerId));

        if (!partner.getIsActive()) {
            throw new BusinessException("Delivery partner is already suspended.");
        }

        partner.setIsActive(false);
        partner.setIsAvailable(false);  // take them offline immediately
        DeliveryPartner saved = partnerRepository.save(partner);
        log.info("Delivery partner suspended: partnerId={}", partnerId);
        return saved;
    }

    /** Reinstates a suspended delivery partner. */
    @AuditAction(action = "PARTNER_REINSTATED", resourceType = "PARTNER")
    @Transactional
    public DeliveryPartner reinstatePartner(Long partnerId) {
        DeliveryPartner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("DeliveryPartner", "id", partnerId));

        if (partner.getIsActive()) {
            throw new BusinessException("Delivery partner is not suspended.");
        }

        partner.setIsActive(true);
        DeliveryPartner saved = partnerRepository.save(partner);
        log.info("Delivery partner reinstated: partnerId={}", partnerId);
        return saved;
    }
}
