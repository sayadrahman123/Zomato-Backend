package com.zomato.backend.dto.request;

import com.zomato.backend.entity.enums.CuisineType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

/**
 * Payload for POST /api/restaurants
 *
 * The owner is taken from the JWT — not from this request body.
 */
public record CreateRestaurantRequest(

        @NotBlank(message = "Restaurant name is required")
        @Size(min = 2, max = 150, message = "Name must be between 2 and 150 characters")
        String name,

        @Size(max = 2000, message = "Description must be under 2000 characters")
        String description,

        @NotBlank(message = "Phone number is required")
        @Pattern(
            regexp = "^[6-9]\\d{9}$",
            message = "Please provide a valid 10-digit Indian mobile number"
        )
        String phone,

        @Email(message = "Please provide a valid email address")
        String email,                       // optional

        @NotNull(message = "Cuisine type is required")
        CuisineType cuisineType,

        @NotNull(message = "Address is required")
        @Valid                              // triggers nested validation on AddressRequest
        AddressRequest address
) {}
