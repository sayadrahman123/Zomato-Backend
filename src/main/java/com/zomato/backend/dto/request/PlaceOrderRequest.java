package com.zomato.backend.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload for POST /api/orders
 *
 * The items come from the customer's Redis cart — not from this request.
 * This request only provides: where to deliver, how to pay, and any notes.
 */
public record PlaceOrderRequest(

        @NotNull(message = "Delivery address is required")
        @Valid
        AddressRequest deliveryAddress,

        /**
         * "RAZORPAY" or "COD" (Cash on Delivery).
         */
        @NotBlank(message = "Payment method is required")
        @Pattern(
            regexp = "^(RAZORPAY|COD)$",
            message = "Payment method must be 'RAZORPAY' or 'COD'"
        )
        String paymentMethod,

        @Size(max = 500, message = "Special instructions must be under 500 characters")
        String specialInstructions
) {}
