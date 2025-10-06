package ch.batbern.shared.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods that require audit logging
 *
 * Usage:
 * <pre>
 * &#64;AuditLog(action = "USER_DATA_EXPORT")
 * public ResponseEntity<Map<String, Object>> exportUserData(...) {
 *     // method implementation
 * }
 * </pre>
 *
 * The AuditLoggingAspect will intercept methods annotated with @AuditLog
 * and log the action with user context to CloudWatch.
 *
 * Implements AC10: Audit Logging from Story 1.11
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AuditLog {
    /**
     * The audit action to log (e.g., "USER_DATA_EXPORT", "USER_DATA_DELETION")
     */
    String action();

    /**
     * Optional description of the audit action
     */
    String description() default "";

    /**
     * Severity level of the audit event
     */
    AuditSeverity severity() default AuditSeverity.INFO;

    public enum AuditSeverity {
        INFO,
        WARNING,
        CRITICAL
    }
}
