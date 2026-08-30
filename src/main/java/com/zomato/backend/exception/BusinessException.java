package com.zomato.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a business rule is violated.
 *
 * Examples:
 *  - Changing password with wrong currentPassword
 *  - Placing an order from an empty cart
 *  - Trying to review an order that isn't DELIVERED yet
 *
 * Maps to HTTP 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
