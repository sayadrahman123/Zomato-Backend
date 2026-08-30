package com.zomato.backend.controller;

import com.zomato.backend.dto.request.LoginRequest;
import com.zomato.backend.dto.request.RegisterRequest;
import com.zomato.backend.dto.response.ApiResponse;
import com.zomato.backend.dto.response.AuthResponse;
import com.zomato.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handles user registration and login.
 *
 * Both endpoints are PUBLIC (no JWT required) —
 * configured in {@link com.zomato.backend.config.SecurityConfig}.
 *
 * Base path: /api/auth
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Register and login endpoints")
public class AuthController {

    private final AuthService authService;

    // ── POST /api/auth/register ───────────────────────────────────────────────

    @Operation(
        summary     = "Register a new user",
        description = "Creates a new CUSTOMER account. Returns a JWT token on success."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description  = "User registered successfully",
            content      = @Content(schema = @Schema(implementation = AuthResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description  = "Email or phone already registered"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description  = "Validation error (invalid email, phone format, etc.)"
        )
    })
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        AuthResponse authResponse = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("User registered successfully", authResponse));
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @Operation(
        summary     = "Login with email and password",
        description = "Authenticates an existing user. Returns a fresh JWT token on success."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description  = "Login successful",
            content      = @Content(schema = @Schema(implementation = AuthResponse.class))
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "401",
            description  = "Invalid credentials or account banned"
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description  = "Validation error"
        )
    })
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request
    ) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Login successful", authResponse));
    }
}
