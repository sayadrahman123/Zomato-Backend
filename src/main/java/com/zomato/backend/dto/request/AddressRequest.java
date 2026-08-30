package com.zomato.backend.dto.request;

import jakarta.validation.constraints.*;

/**
 * Address payload embedded inside restaurant create/update requests.
 */
public record AddressRequest(

        @NotBlank(message = "Street is required")
        @Size(max = 255, message = "Street must be under 255 characters")
        String street,

        @Size(max = 100, message = "Area must be under 100 characters")
        String area,                        // optional — locality / neighbourhood

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must be under 100 characters")
        String city,

        @NotBlank(message = "State is required")
        @Size(max = 100, message = "State must be under 100 characters")
        String state,

        @NotBlank(message = "Pincode is required")
        @Pattern(
            regexp = "^[1-9][0-9]{5}$",
            message = "Please provide a valid 6-digit Indian PIN code"
        )
        String pincode,

        @DecimalMin(value = "-90.0",  message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0",   message = "Latitude must be between -90 and 90")
        Double latitude,                    // optional

        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0",  message = "Longitude must be between -180 and 180")
        Double longitude                    // optional
) {}
