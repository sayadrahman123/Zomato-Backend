package com.zomato.backend.dto.response;

/**
 * Snapshot of the delivery address embedded in an order response.
 */
public record DeliveryAddressResponse(
        String street,
        String area,
        String city,
        String state,
        String pincode,
        Double latitude,
        Double longitude
) {}
