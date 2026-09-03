package com.zomato.backend.service;

import com.zomato.backend.dto.request.ChangePasswordRequest;
import com.zomato.backend.dto.request.UpdateProfileRequest;
import com.zomato.backend.dto.response.UserResponse;
import com.zomato.backend.entity.User;
import com.zomato.backend.exception.BusinessException;
import com.zomato.backend.exception.DuplicatePhoneException;
import com.zomato.backend.exception.ResourceNotFoundException;
import com.zomato.backend.mapper.UserMapper;
import com.zomato.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Handles authenticated user profile operations.
 * <p>
 * All methods receive the userId extracted from the JWT
 * by the controller — no userId is trusted from the request body.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository         userRepository;
    private final PasswordEncoder        passwordEncoder;
    private final UserMapper             userMapper;
    private final UserProfileCacheService profileCacheService;

    // ── Get Profile ───────────────────────────────────────────────────────────

    /**
     * Returns the profile of the authenticated user.
     *
     * @param userId JWT-extracted user ID
     * @return safe UserResponse (no passwordHash)
     * @throws ResourceNotFoundException if the user isn't found (edge case)
     */
    @Transactional(readOnly = true)
    public UserResponse getProfile(Long userId) {
        return profileCacheService.getOrLoad(
                userId,
                () -> userRepository.findById(userId).map(userMapper::toUserResponse)
        );
    }

    // ── Update Profile ────────────────────────────────────────────────────────

    /**
     * Updates name and/or phone of the authenticated user.
     * <p>
     * Only non-null, non-blank fields in the request are applied —
     * so a client can send just { "name": "new name" } without
     * touching the phone, and vice versa.
     *
     * @param userId  JWT-extracted user ID
     * @param request partial update payload
     * @return updated UserResponse
     * @throws DuplicatePhoneException if another user already takes the new phone
     */
    @Transactional
    public UserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = findUserById(userId);
        boolean updated = false;

        // ── Apply name change ──────────────────────────────────────────────────
        if (StringUtils.hasText(request.name())) {
            user.setName(request.name().trim());
            updated = true;
        }

        // ── Apply phone change ─────────────────────────────────────────────────
        if (StringUtils.hasText(request.phone())) {
            String newPhone = request.phone().trim();

            // Only check uniqueness if the phone is actually changing
            if (!newPhone.equals(user.getPhone())) {
                if (userRepository.existsByPhone(newPhone)) {
                    throw new DuplicatePhoneException(newPhone);
                }
                user.setPhone(newPhone);
                updated = true;
            }
        }

        if (updated) {
            user = userRepository.save(user);
            profileCacheService.evict(userId);
            log.info("Profile updated for userId={}", userId);
        }

        return userMapper.toUserResponse(user);
    }

    // ── Change Password ───────────────────────────────────────────────────────

    /**
     * Changes the password of the authenticated user.
     * <p>
     * Validations (in order):
     *  1. The currentPassword must match the stored BCrypt hash
     *  2. The newPassword must not be the same as the currentPassword
     *  3. newPassword and confirmPassword must match
     *
     * @param userId  JWT-extracted user ID
     * @param request contains the currentPassword, newPassword, confirmPassword
     * @throws BusinessException if any validation fails
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = findUserById(userId);

        // ── 1. Verify current password ─────────────────────────────────────────
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect");
        }

        // ── 2. New password must differ from current ───────────────────────────
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessException("New password must be different from the current password");
        }

        // ── 3. New password and confirm password must match ────────────────────
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException("New password and confirm password do not match");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        profileCacheService.evict(userId);   // cached profile had stale data (no pw, but safe housekeeping)
        log.info("Password changed successfully for userId={}", userId);
    }

    // ── Internal Helpers ──────────────────────────────────────────────────────

    /**
     * Fetches a User entity by ID or throws ResourceNotFoundException.
     * Private — only used within this service.
     */
    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }
}
