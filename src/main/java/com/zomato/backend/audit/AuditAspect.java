package com.zomato.backend.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.stream.Collectors;

/**
 * AOP Aspect intercepting methods annotated with {@link AuditAction}.
 * <p>
 * Automatically extracts:
 * <ul>
 *   <li>The current authenticated actor (email, roles).</li>
 *   <li>The target resource ID from method parameters.</li>
 *   <li>Execution duration and outcome (SUCCESS / FAILURE).</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    @Around("@annotation(auditAction)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, AuditAction auditAction) throws Throwable {
        long start = System.currentTimeMillis();
        String action       = auditAction.action();
        String resourceType = auditAction.resourceType();

        // 1. Resolve Actor from Spring Security context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String actorEmail = (auth != null && auth.isAuthenticated()) ? auth.getName() : "ANONYMOUS";
        String actorRole = (auth != null)
                ? auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(","))
                : "NONE";

        // 2. Resolve Resource ID from method parameters
        String resourceId = resolveResourceId(joinPoint);

        Object result;
        try {
            result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - start;
            auditService.recordSuccess(
                    action,
                    null,
                    actorEmail,
                    actorRole,
                    resourceType,
                    resourceId,
                    "Execution time: " + executionTime + "ms"
            );
            return result;

        } catch (Throwable ex) {
            long executionTime = System.currentTimeMillis() - start;
            auditService.recordFailure(
                    action,
                    null,
                    actorEmail,
                    actorRole,
                    resourceType,
                    resourceId,
                    "Failed after " + executionTime + "ms",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    /**
     * Extracts the target resource ID by inspecting method parameter names and types.
     */
    private String resolveResourceId(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            String name = parameters[i].getName().toLowerCase();
            if (name.contains("id") && args[i] != null) {
                return String.valueOf(args[i]);
            }
        }

        // Fallback: first primitive or ID argument
        if (args[0] instanceof Long || args[0] instanceof Integer || args[0] instanceof String) {
            return String.valueOf(args[0]);
        }

        return null;
    }
}
