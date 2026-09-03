package com.zomato.backend.audit;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Immutable audit log entity recording sensitive actions and platform mutations.
 * <p>
 * Stored in {@code audit_logs} table for compliance, security investigation,
 * and operational traceability.
 */
@Entity
@Table(
    name = "audit_logs",
    indexes = {
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_actor", columnList = "actor_email"),
        @Index(name = "idx_audit_resource", columnList = "resource_type, resource_id"),
        @Index(name = "idx_audit_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Action name, e.g. "USER_BANNED", "RESTAURANT_APPROVED", "ORDER_STATUS_UPDATED".
     */
    @Column(nullable = false, length = 100)
    private String action;

    /**
     * ID of the user who performed the action (null if unauthenticated).
     */
    @Column(name = "actor_id")
    private Long actorId;

    /**
     * Email / username of the actor.
     */
    @Column(name = "actor_email", length = 150)
    private String actorEmail;

    /**
     * Role of the actor (ADMIN, CUSTOMER, etc.).
     */
    @Column(name = "actor_role", length = 50)
    private String actorRole;

    /**
     * Type of resource affected (e.g. "USER", "RESTAURANT", "ORDER", "PARTNER").
     */
    @Column(name = "resource_type", length = 50)
    private String resourceType;

    /**
     * ID of the affected resource (as string for flexibility).
     */
    @Column(name = "resource_id", length = 100)
    private String resourceId;

    /**
     * Operational details or state diff summary.
     */
    @Column(columnDefinition = "TEXT")
    private String details;

    /**
     * Client IP address where the request originated.
     */
    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    /**
     * Execution outcome: "SUCCESS" or "FAILURE".
     */
    @Column(nullable = false, length = 20)
    private String status;

    /**
     * Error message if the operation failed.
     */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /**
     * Correlation ID (X-Correlation-ID) tracing this event to specific HTTP logs.
     */
    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    /**
     * Timestamp when the audit event occurred.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
