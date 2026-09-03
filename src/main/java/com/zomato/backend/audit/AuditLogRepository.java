package com.zomato.backend.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for immutable {@link AuditLog} records.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByActorEmailOrderByCreatedAtDesc(String actorEmail, Pageable pageable);

    Page<AuditLog> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

    Page<AuditLog> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(
            String resourceType, String resourceId, Pageable pageable);
}
