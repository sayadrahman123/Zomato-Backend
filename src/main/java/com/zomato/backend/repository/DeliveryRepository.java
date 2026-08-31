package com.zomato.backend.repository;

import com.zomato.backend.entity.Delivery;
import com.zomato.backend.entity.enums.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByOrderId(Long orderId);

    /** All deliveries for a partner — for their delivery history. */
    Page<Delivery> findByPartnerIdOrderByCreatedAtDesc(Long partnerId, Pageable pageable);

    /** Active delivery for a partner (should be at most one at a time). */
    Optional<Delivery> findByPartnerIdAndStatus(Long partnerId, DeliveryStatus status);

    boolean existsByOrderId(Long orderId);

    long countByPartnerIdAndStatus(Long partnerId, DeliveryStatus status);
}
