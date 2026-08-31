package com.zomato.backend.dto.request;

import com.zomato.backend.entity.enums.VehicleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for POST /api/delivery/register
 *
 * A DELIVERY_PARTNER user submits this to create their partner profile.
 * Admin must approve (isVerified = true) before they can accept deliveries.
 */
public record RegisterPartnerRequest(

        @NotNull(message = "Vehicle type is required")
        VehicleType vehicleType,

        @NotBlank(message = "Vehicle number is required")
        @Size(min = 4, max = 20, message = "Vehicle number must be between 4 and 20 characters")
        @Pattern(
            regexp  = "^[A-Z0-9]+$",
            message = "Vehicle number must contain only uppercase letters and digits"
        )
        String vehicleNumber
) {}
