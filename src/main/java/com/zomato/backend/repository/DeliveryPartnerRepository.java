package com.zomato.backend.repository;

import com.zomato.backend.entity.DeliveryPartner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeliveryPartnerRepository extends JpaRepository<DeliveryPartner, Long> {

    Optional<DeliveryPartner> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /** All verified + available partners — for manual admin assignment. */
    List<DeliveryPartner> findByIsVerifiedTrueAndIsAvailableTrueAndIsActiveTrue();

    /**
     * Finds verified + available partners whose IDs are in the given list.
     * Used after Redis GEORADIUS returns nearby IDs — we intersect with DB
     * to confirm they are still verified and available.
     */
    @Query("""
            SELECT dp FROM DeliveryPartner dp
            WHERE dp.id IN :ids
              AND dp.isVerified  = true
              AND dp.isAvailable = true
              AND dp.isActive    = true
            ORDER BY dp.averageRating DESC NULLS LAST
            """)
    List<DeliveryPartner> findAvailableByIds(@Param("ids") List<Long> ids);

    Page<DeliveryPartner> findByIsVerifiedFalseAndIsActiveTrue(Pageable pageable);

    long countByIsVerifiedFalseAndIsActiveTrue();
}
