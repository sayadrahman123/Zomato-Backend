package com.zomato.backend.service;

import com.zomato.backend.dto.request.LoginRequest;
import com.zomato.backend.dto.request.RegisterRequest;
import com.zomato.backend.dto.response.AuthResponse;
import com.zomato.backend.entity.User;
import com.zomato.backend.entity.enums.UserRole;
import com.zomato.backend.exception.DuplicateEmailException;
import com.zomato.backend.exception.DuplicatePhoneException;
import com.zomato.backend.repository.UserRepository;
import com.zomato.backend.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles user registration and authentication.
 *
 * register() flow:
 *   1. Check email uniqueness          → 409 if taken
 *   2. Check phone uniqueness          → 409 if taken
 *   3. Hash the password (BCrypt 12)
 *   4. Persist the User entity
 *   5. Generate JWT
 *   6. Return AuthResponse (token + user info)
 *
 * login() flow:
 *   1. Delegate to AuthenticationManager.authenticate()
 *      → internally calls UserDetailsServiceImpl.loadUserByUsername()
 *      → verifies BCrypt password hash
 *      → throws BadCredentialsException / DisabledException on failure
 *   2. Load the full User entity for the JWT claims
 *   3. Generate JWT
 *   4. Return AuthResponse
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository       userRepository;
    private final PasswordEncoder      passwordEncoder;
    private final JwtUtil              jwtUtil;
    private final AuthenticationManager authenticationManager;

    // ── Register ──────────────────────────────────────────────────────────────

    /**
     * Registers a new user with the CUSTOMER role by default.
     *
     * @param request validated registration payload
     * @return JWT token + user details
     * @throws DuplicateEmailException if email is already registered
     * @throws DuplicatePhoneException if phone is already registered
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.email());

        // ── 1. Uniqueness guards ───────────────────────────────────────────────
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        if (userRepository.existsByPhone(request.phone())) {
            throw new DuplicatePhoneException(request.phone());
        }

        // ── 2. Build and persist user ─────────────────────────────────────────
        User user = User.builder()
                .name(request.name())
                .email(request.email().toLowerCase().trim())
                .phone(request.phone())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(UserRole.CUSTOMER)   // all self-registrations are CUSTOMER
                .isActive(true)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User registered successfully with id: {}", savedUser.getId());

        // ── 3. Generate token and return ──────────────────────────────────────
        String token = jwtUtil.generateToken(
                savedUser.getEmail(),
                savedUser.getId(),
                savedUser.getRole().name()
        );

        return AuthResponse.of(
                token,
                jwtUtil.getExpirationMs(),
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * Authenticates a user and returns a fresh JWT.
     *
     * Delegates the heavy lifting (password check, account status) to
     * Spring Security's AuthenticationManager. If authentication fails,
     * Spring Security throws BadCredentialsException or
     * DisabledException automatically — no manual password comparison needed.
     *
     * @param request validated login payload
     * @return fresh JWT token + user details
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.email());

        // ── 1. Authenticate via Spring Security ───────────────────────────────
        // This internally calls:
        //   UserDetailsServiceImpl.loadUserByUsername(email)
        //   BCryptPasswordEncoder.matches(rawPassword, storedHash)
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email().toLowerCase().trim(),
                        request.password()
                )
        );

        // ── 2. Load full entity for JWT claims ────────────────────────────────
        // authentication.getName() returns the email (set as username in UserDetails)
        User user = userRepository.findByEmailAndIsActiveTrue(authentication.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found — this should never happen"));

        log.info("User logged in successfully: id={}, role={}", user.getId(), user.getRole());

        // ── 3. Generate fresh token and return ────────────────────────────────
        String token = jwtUtil.generateToken(
                user.getEmail(),
                user.getId(),
                user.getRole().name()
        );

        return AuthResponse.of(
                token,
                jwtUtil.getExpirationMs(),
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
