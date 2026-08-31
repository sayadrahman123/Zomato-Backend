package com.zomato.backend.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Payload for PATCH /api/delivery/location
 *
 * Sent by the partner's mobile app every N seconds while they are online.
 * Coordinates must be valid WGS-84 values (standard GPS format).
 */
public record UpdateLocationRequest(

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90.0",  message = "Latitude must be >= -90")
        @DecimalMax(value = "90.0",   message = "Latitude must be <= 90")
        Double latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180.0", message = "Longitude must be >= -180")
        @DecimalMax(value = "180.0",  message = "Longitude must be <= 180")
        Double longitude
) {}
