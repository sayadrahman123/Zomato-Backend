package com.zomato.backend.dto.response;

import java.time.LocalDateTime;

/**
 * Customer-facing view of a saved delivery address.
 */
public record UserAddressResponse(
        Long          id,
        String        label,
        String        street,
        String        area,
        String        city,
        String        state,
        String        pincode,
        Double        latitude,
        Double        longitude,
        Boolean       isDefault,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
