package com.zomato.backend.repository;

import com.zomato.backend.entity.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    List<UserAddress> findByUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);

    Optional<UserAddress> findByIdAndUserId(Long id, Long userId);

    Optional<UserAddress> findByUserIdAndIsDefaultTrue(Long userId);

    long countByUserId(Long userId);

    /**
     * Clears the isDefault flag on ALL addresses for a user in a single UPDATE.
     * Called before setting a new default to maintain the single-default invariant.
     */
    @Modifying
    @Query("UPDATE UserAddress ua SET ua.isDefault = false WHERE ua.user.id = :userId")
    void clearDefaultForUser(@Param("userId") Long userId);
}
