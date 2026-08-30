package com.zomato.backend.dto.response;

/**
 * Address details in API responses.
 */
public record AddressResponse(
        Long   id,
        String street,
        String area,
        String city,
        String state,
        String pincode,
        Double latitude,
        Double longitude
) {}
