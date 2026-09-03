package com.zomato.backend.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for capturing and persisting platform audit records.
 * <p>
 * Ensures audit logging is non-blocking, isolated from caller transactions
 * (using {@code REQUIRES_NEW}), and resilient to failures so business operations
 * are never broken by auditing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Records an audit event synchronously in an isolated transaction.
     * <p>
     * Runs with {@code Propagation.REQUIRES_NEW} so that even if the outer
     * business transaction rolls back, or if saving the audit log encounters an issue,
     * the two operations do not interfere.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String action,
            Long actorId,
            String actorEmail,
            String actorRole,
            String resourceType,
            String resourceId,
            String details,
            String status,
            String errorMessage
    ) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID);
        String clientIp      = MDC.get(CorrelationIdFilter.MDC_CLIENT_IP);

        try {
            AuditLog auditLog = AuditLog.builder()
                    .action(action)
                    .actorId(actorId)
                    .actorEmail(actorEmail)
                    .actorRole(actorRole)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .details(details)
                    .ipAddress(clientIp)
                    .status(status != null ? status : "SUCCESS")
                    .errorMessage(errorMessage)
                    .correlationId(correlationId)
                    .build();

            auditLogRepository.save(auditLog);

            log.info("AUDIT [{}] actor={} role={} target={}:{} status={} [corrId={}]",
                    action, actorEmail, actorRole, resourceType, resourceId, status, correlationId);

        } catch (Exception e) {
            // Non-fatal: log error so auditing failure does not kill caller
            log.error("Failed to persist audit log for action={}: {}", action, e.getMessage());
        }
    }

    /**
     * Convenience method for recording successful actions.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(
            String action,
            Long actorId,
            String actorEmail,
            String actorRole,
            String resourceType,
            String resourceId,
            String details
    ) {
        record(action, actorId, actorEmail, actorRole, resourceType, resourceId, details, "SUCCESS", null);
    }

    /**
     * Convenience method for recording failed actions.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(
            String action,
            Long actorId,
            String actorEmail,
            String actorRole,
            String resourceType,
            String resourceId,
            String details,
            String errorMessage
    ) {
        record(action, actorId, actorEmail, actorRole, resourceType, resourceId, details, "FAILURE", errorMessage);
    }
}
