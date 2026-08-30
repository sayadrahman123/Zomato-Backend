package com.zomato.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown during registration when the provided phone number is already
 * associated with an existing account.
 *
 * Maps to HTTP 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicatePhoneException extends RuntimeException {

    public DuplicatePhoneException(String phone) {
        super(String.format("An account with phone number '%s' already exists", phone));
    }
}
