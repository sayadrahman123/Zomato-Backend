package com.zomato.backend.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for POST /api/auth/register
 *
 * Java record — immutable, auto-generates constructor, getters,
 * equals, hashCode, and toString. Perfect for DTOs.
 *
 * Validations run automatically when @Valid is placed on the
 * controller method parameter.
 */
public record RegisterRequest(

        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Please provide a valid email address")
        String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(
            regexp = "^[6-9]\\d{9}$",
            message = "Please provide a valid 10-digit Indian mobile number"
        )
        String phone,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
        String password
) {}
