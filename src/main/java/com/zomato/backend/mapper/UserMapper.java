package com.zomato.backend.mapper;

import com.zomato.backend.dto.response.UserResponse;
import com.zomato.backend.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Manual mapper between {@link User} entity and its DTOs.
 *
 * Why manual instead of MapStruct?
 * - Zero extra dependency for a project this size
 * - Explicit control — easy to see exactly what fields are mapped
 * - No annotation processing surprises
 *
 * If this grows complex, swap to MapStruct later with no API changes.
 */
@Component
public class UserMapper {

    /**
     * Converts a User entity to a safe {@link UserResponse}.
     * passwordHash is intentionally NOT included.
     *
     * @param user the entity from the database
     * @return public-safe DTO
     */
    public UserResponse toUserResponse(User user) {
        if (user == null) return null;

        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getIsActive(),
                user.getCreatedAt()
        );
    }

    /**
     * Converts a list of User entities to a list of {@link UserResponse} DTOs.
     * Useful for admin list endpoints.
     *
     * @param users list of entities
     * @return list of public-safe DTOs
     */
    public List<UserResponse> toUserResponseList(List<User> users) {
        return users.stream()
                .map(this::toUserResponse)
                .toList();
    }
}
