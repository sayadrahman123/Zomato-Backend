package com.zomato.backend.exception;

import com.zomato.backend.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized exception handler for the entire application.
 *
 * Every exception that bubbles up from any controller or service
 * is caught here and converted into a consistent {@link ApiResponse}
 * shape — so the client always gets the same JSON structure
 * regardless of what went wrong.
 *
 * Handler priority (top = most specific, bottom = most general):
 *   1. Validation errors     (400)  ← MethodArgumentNotValidException
 *   2. Business exceptions   (400)  ← BusinessException
 *   3. Conflict exceptions   (409)  ← DuplicateEmailException, DuplicatePhoneException
 *   4. Not found             (404)  ← ResourceNotFoundException
 *   5. Auth exceptions       (401)  ← BadCredentialsException, AuthenticationException
 *   6. Access denied         (403)  ← AccessDeniedException
 *   7. Method not allowed    (405)  ← HttpRequestMethodNotSupportedException
 *   8. No resource found     (404)  ← NoResourceFoundException (wrong URL)
 *   9. Catch-all             (500)  ← Exception
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 1. Validation Errors (Bean Validation) ────────────────────────────────

    /**
     * Handles @Valid failures on @RequestBody parameters.
     *
     * Returns a map of { fieldName: errorMessage } in the data field
     * so the client knows exactly which fields failed and why.
     *
     * Example response:
     * <pre>
     * {
     *   "success": false,
     *   "message": "Validation failed. Please check the highlighted fields.",
     *   "data": {
     *     "email": "Please provide a valid email address",
     *     "phone": "Please provide a valid 10-digit Indian mobile number"
     *   }
     * }
     * </pre>
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException ex
    ) {
        // Collect all field errors into an ordered map
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            // If the same field has multiple errors, keep the first one
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.debug("Validation failed: {}", fieldErrors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Validation failed. Please check the highlighted fields.")
                        .data(fieldErrors)
                        .build());
    }

    // ── 2. Business Rule Violations (400) ─────────────────────────────────────

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.debug("Business exception: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ── 3. Conflict Exceptions (409) ──────────────────────────────────────────

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateEmail(DuplicateEmailException ex) {
        log.debug("Duplicate email: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(DuplicatePhoneException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicatePhone(DuplicatePhoneException ex) {
        log.debug("Duplicate phone: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ── 4. Not Found (404) ────────────────────────────────────────────────────

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(
            ResourceNotFoundException ex
    ) {
        log.debug("Resource not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // ── 5. Authentication Failures (401) ──────────────────────────────────────

    /**
     * Handles wrong credentials, disabled/locked accounts.
     * Using specific messages per exception type for better UX.
     */
    @ExceptionHandler({
        BadCredentialsException.class,
        DisabledException.class,
        LockedException.class,
        AuthenticationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex
    ) {
        String message;
        if (ex instanceof DisabledException || ex instanceof LockedException) {
            message = "Your account has been suspended. Please contact support.";
        } else if (ex instanceof BadCredentialsException) {
            message = "Invalid email or password.";
        } else {
            message = "Authentication failed. Please login again.";
        }

        log.debug("Authentication exception [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(message));
    }

    // ── 6. Access Denied (403) ────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.debug("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("You do not have permission to perform this action."));
    }

    // ── 7. Method Not Allowed (405) ───────────────────────────────────────────

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex
    ) {
        String message = String.format(
                "HTTP method '%s' is not supported for this endpoint.", ex.getMethod()
        );
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(message));
    }

    // ── 8. Wrong URL / No Handler Found (404) ─────────────────────────────────

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException ex
    ) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("The requested endpoint does not exist."));
    }

    // ── 9. Catch-all (500) ────────────────────────────────────────────────────

    /**
     * Last-resort handler for any unexpected exception.
     *
     * Logs the full stack trace internally but returns a generic message
     * to the client — never expose internal details (stack traces, DB errors)
     * to end users.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(
                        "An unexpected error occurred. Please try again later."
                ));
    }
}
