package com.zomato.backend.dto.request;

import com.zomato.backend.entity.enums.CuisineType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/**
 * Payload for PUT /api/restaurants/{id}
 *
 * All fields are optional — only non-null values are applied,
 * enabling true partial updates (PATCH-style via PUT).
 */
public record UpdateRestaurantRequest(

        @Size(min = 2, max = 150, message = "Name must be between 2 and 150 characters")
        String name,

        @Size(max = 2000, message = "Description must be under 2000 characters")
        String description,

        @Pattern(
            regexp = "^[6-9]\\d{9}$",
            message = "Please provide a valid 10-digit Indian mobile number"
        )
        String phone,

        @Email(message = "Please provide a valid email address")
        String email,

        CuisineType cuisineType,

        @Valid                              // triggers nested validation if address is provided
        AddressRequest address             // null = don't update address
) {}
