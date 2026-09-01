package com.zomato.backend.service;

import com.zomato.backend.dto.request.SaveAddressRequest;
import com.zomato.backend.dto.response.UserAddressResponse;
import com.zomato.backend.entity.User;
import com.zomato.backend.entity.UserAddress;
import com.zomato.backend.exception.BusinessException;
import com.zomato.backend.exception.ResourceNotFoundException;
import com.zomato.backend.repository.UserAddressRepository;
import com.zomato.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD for a customer's saved delivery addresses.
 *
 * Single-default invariant:
 *   At most one address per user can have isDefault=true.
 *   Enforced by clearDefaultForUser() (bulk UPDATE) before setting a new default.
 *
 * Address cap:
 *   A user can save up to MAX_ADDRESSES addresses.
 *   Prevents unbounded growth; typical users need 2-3.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAddressService {

    private static final int MAX_ADDRESSES = 10;

    private final UserAddressRepository addressRepository;
    private final UserRepository        userRepository;

    // ── Add Address ───────────────────────────────────────────────────────────

    /**
     * Saves a new delivery address for the customer.
     * If isDefault=true (or it's the first address), sets it as the default.
     */
    @Transactional
    public UserAddressResponse addAddress(Long userId, SaveAddressRequest request) {
        long count = addressRepository.countByUserId(userId);
        if (count >= MAX_ADDRESSES) {
            throw new BusinessException(
                    "You can save up to " + MAX_ADDRESSES + " addresses. Delete one to add a new one.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // First address or explicitly requested → make it the default
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault()) || count == 0;

        if (makeDefault) {
            addressRepository.clearDefaultForUser(userId);
        }

        UserAddress address = UserAddress.builder()
                .user(user)
                .label(request.label() != null ? request.label().trim() : "Home")
                .street(request.street())
                .area(request.area())
                .city(request.city())
                .state(request.state())
                .pincode(request.pincode())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .isDefault(makeDefault)
                .build();

        UserAddress saved = addressRepository.save(address);
        log.info("Address added: addressId={}, userId={}, default={}", saved.getId(), userId, makeDefault);
        return toResponse(saved);
    }

    // ── Update Address ────────────────────────────────────────────────────────

    /**
     * Full update of an existing address.
     * If isDefault=true, clears other defaults first.
     */
    @Transactional
    public UserAddressResponse updateAddress(Long addressId, Long userId, SaveAddressRequest request) {
        UserAddress address = getOwnedAddress(addressId, userId);

        boolean makeDefault = Boolean.TRUE.equals(request.isDefault());
        if (makeDefault && !address.getIsDefault()) {
            addressRepository.clearDefaultForUser(userId);
            address.setIsDefault(true);
        }

        address.setLabel(request.label() != null ? request.label().trim() : address.getLabel());
        address.setStreet(request.street());
        address.setArea(request.area());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPincode(request.pincode());
        address.setLatitude(request.latitude());
        address.setLongitude(request.longitude());

        UserAddress saved = addressRepository.save(address);
        return toResponse(saved);
    }

    // ── Set Default ───────────────────────────────────────────────────────────

    /**
     * Marks the given address as the customer's default.
     * Clears the default flag on all other addresses first.
     */
    @Transactional
    public UserAddressResponse setDefault(Long addressId, Long userId) {
        UserAddress address = getOwnedAddress(addressId, userId);

        if (address.getIsDefault()) {
            return toResponse(address); // already default — no-op
        }

        addressRepository.clearDefaultForUser(userId);
        address.setIsDefault(true);
        return toResponse(addressRepository.save(address));
    }

    // ── Delete Address ────────────────────────────────────────────────────────

    /**
     * Deletes an address permanently.
     * If the deleted address was the default, assigns default to the
     * most recently created remaining address (if any).
     */
    @Transactional
    public void deleteAddress(Long addressId, Long userId) {
        UserAddress address = getOwnedAddress(addressId, userId);
        boolean wasDefault = address.getIsDefault();

        addressRepository.delete(address);
        log.info("Address deleted: addressId={}, userId={}", addressId, userId);

        // Re-assign default if needed
        if (wasDefault) {
            List<UserAddress> remaining =
                    addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId);
            if (!remaining.isEmpty()) {
                UserAddress newDefault = remaining.get(0);
                newDefault.setIsDefault(true);
                addressRepository.save(newDefault);
            }
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * All saved addresses for the customer — default first, then newest first.
     */
    @Transactional(readOnly = true)
    public List<UserAddressResponse> getMyAddresses(Long userId) {
        return addressRepository
                .findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * The customer's current default address.
     * Returns null if no default is set (user has no addresses).
     */
    @Transactional(readOnly = true)
    public UserAddressResponse getDefaultAddress(Long userId) {
        return addressRepository
                .findByUserIdAndIsDefaultTrue(userId)
                .map(this::toResponse)
                .orElse(null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UserAddress getOwnedAddress(Long addressId, Long userId) {
        return addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Address", "id", addressId));
    }

    private UserAddressResponse toResponse(UserAddress a) {
        return new UserAddressResponse(
                a.getId(),
                a.getLabel(),
                a.getStreet(),
                a.getArea(),
                a.getCity(),
                a.getState(),
                a.getPincode(),
                a.getLatitude(),
                a.getLongitude(),
                a.getIsDefault(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}
