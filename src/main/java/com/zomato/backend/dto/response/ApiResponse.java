package com.zomato.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Standard API response envelope used by every controller in this project.
 *
 * All endpoints return this shape:
 * <pre>
 * {
 *   "success": true,
 *   "message": "User registered successfully",
 *   "data": { ... },       // null on error responses
 *   "timestamp": "..."
 * }
 * </pre>
 *
 * {@code @JsonInclude(NON_NULL)} hides the "data" field entirely
 * when it is null (e.g. on plain success messages or errors).
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;

    @Builder.Default
    private final LocalDateTime timestamp = LocalDateTime.now();

    // ── Static factory helpers ────────────────────────────────────────────────

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}
