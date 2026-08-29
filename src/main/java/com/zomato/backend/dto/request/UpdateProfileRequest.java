package com.zomato.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for PUT /api/users/me
 *
 * All fields are optional — only non-null values will be applied.
 * Using a class (not record) here so fields can be null by default,
 * allowing partial updates.
 */
public record UpdateProfileRequest(

        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @Pattern(
            regexp = "^[6-9]\\d{9}$",
            message = "Please provide a valid 10-digit Indian mobile number"
        )
        String phone
) {}
