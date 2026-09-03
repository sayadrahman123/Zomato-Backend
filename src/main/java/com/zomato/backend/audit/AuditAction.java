package com.zomato.backend.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to declaratively audit sensitive service methods.
 * <p>
 * Handled automatically by {@link AuditAspect}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditAction {

    /**
     * The business action identifier (e.g. "USER_BANNED", "ORDER_CANCELLED").
     */
    String action();

    /**
     * Type of resource being modified (e.g. "USER", "RESTAURANT", "ORDER").
     */
    String resourceType() default "";
}
