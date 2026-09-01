package com.zomato.backend.dto.request;

import jakarta.validation.constraints.*;

/**
 * Payload for POST /api/addresses and PUT /api/addresses/{id}
 *
 * Extends the address fields in AddressRequest with a label and isDefault flag
 * that are specific to saved customer delivery addresses.
 */
public record SaveAddressRequest(

        @Size(max = 50, message = "Label must be under 50 characters")
        String label,           // e.g. "Home", "Office" — defaults to "Home" if null

        @NotBlank(message = "Street is required")
        @Size(max = 255)
        String street,

        @Size(max = 100)
        String area,

        @NotBlank(message = "City is required")
        @Size(max = 100)
        String city,

        @NotBlank(message = "State is required")
        @Size(max = 100)
        String state,

        @NotBlank(message = "Pincode is required")
        @Pattern(regexp = "^[1-9][0-9]{5}$", message = "Please provide a valid 6-digit PIN code")
        String pincode,

        @DecimalMin(value = "-90.0",  message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0",   message = "Latitude must be between -90 and 90")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0",  message = "Longitude must be between -180 and 180")
        Double longitude,

        Boolean isDefault       // if true, this address becomes the default
) {}
