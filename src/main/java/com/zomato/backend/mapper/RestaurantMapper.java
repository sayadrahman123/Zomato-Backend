package com.zomato.backend.mapper;

import com.zomato.backend.dto.request.AddressRequest;
import com.zomato.backend.dto.response.AddressResponse;
import com.zomato.backend.dto.response.RestaurantResponse;
import com.zomato.backend.dto.response.RestaurantSummaryResponse;
import com.zomato.backend.entity.Address;
import com.zomato.backend.entity.Restaurant;
import org.springframework.stereotype.Component;

/**
 * Manual mapper between {@link Restaurant} / {@link Address} entities
 * and their response/request DTOs.
 */
@Component
public class RestaurantMapper {

    // ── Entity → Response ─────────────────────────────────────────────────────

    /**
     * Full restaurant detail DTO.
     * Includes address, owner info, and all status flags.
     */
    public RestaurantResponse toRestaurantResponse(Restaurant r) {
        if (r == null) return null;

        return new RestaurantResponse(
                r.getId(),
                r.getName(),
                r.getDescription(),
                r.getPhone(),
                r.getEmail(),
                r.getCuisineType(),
                r.getCity(),
                r.getAvgRating(),
                r.getTotalRatings(),
                r.getIsActive(),
                r.getIsOpen(),
                toAddressResponse(r.getAddress()),
                r.getOwner() != null ? r.getOwner().getId()   : null,
                r.getOwner() != null ? r.getOwner().getName() : null,
                r.getCreatedAt()
        );
    }

    /**
     * Compact card DTO for listing / search results.
     * No address, no owner info — just what a customer needs.
     */
    public RestaurantSummaryResponse toRestaurantSummaryResponse(Restaurant r) {
        if (r == null) return null;

        return new RestaurantSummaryResponse(
                r.getId(),
                r.getName(),
                r.getCuisineType(),
                r.getCity(),
                r.getAvgRating(),
                r.getTotalRatings(),
                r.getIsOpen()
        );
    }

    // ── Address Mappers ───────────────────────────────────────────────────────

    /**
     * Maps an Address entity to AddressResponse DTO.
     * Returns null if address is null (restaurant without address).
     */
    public AddressResponse toAddressResponse(Address address) {
        if (address == null) return null;

        return new AddressResponse(
                address.getId(),
                address.getStreet(),
                address.getArea(),
                address.getCity(),
                address.getState(),
                address.getPincode(),
                address.getLatitude(),
                address.getLongitude()
        );
    }

    /**
     * Maps an AddressRequest DTO to a new Address entity.
     * Used during restaurant creation and address update.
     */
    public Address toAddressEntity(AddressRequest request) {
        if (request == null) return null;

        return Address.builder()
                .street(request.street())
                .area(request.area())
                .city(request.city())
                .state(request.state())
                .pincode(request.pincode())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build();
    }

    /**
     * Updates an existing Address entity in-place from an AddressRequest.
     * Used during restaurant update — avoids creating a new Address row.
     *
     * Only non-null fields in the request are applied.
     *
     * @param existing the Address entity to update
     * @param request  the new values (null fields = keep existing)
     */
    public void updateAddressFromRequest(Address existing, AddressRequest request) {
        if (request == null) return;

        if (request.street()   != null) existing.setStreet(request.street());
        if (request.area()     != null) existing.setArea(request.area());
        if (request.city()     != null) existing.setCity(request.city());
        if (request.state()    != null) existing.setState(request.state());
        if (request.pincode()  != null) existing.setPincode(request.pincode());
        if (request.latitude() != null) existing.setLatitude(request.latitude());
        if (request.longitude()!= null) existing.setLongitude(request.longitude());
    }
}
