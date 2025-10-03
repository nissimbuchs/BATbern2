package ch.batbern.shared.security;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Aspect for Audit Logging
 *
 * Intercepts methods annotated with @AuditLog and logs audit events to CloudWatch.
 *
 * Audit Log Format:
 * {
 *   "timestamp": "2025-10-03T10:00:00Z",
 *   "userId": "auth0|123",
 *   "correlationId": "abc-123",
 *   "action": "USER_DATA_EXPORT",
 *   "method": "exportUserData",
 *   "className": "UserDataExportController",
 *   "status": "SUCCESS",
 *   "duration": 123
 * }
 *
 * Implements AC10: Audit Logging from Story 1.11
 */
@Aspect
@Component
@Slf4j
public class AuditLoggingAspect {

    /**
     * Intercept methods annotated with @AuditLog
     */
    @Around("@annotation(auditLog)")
    public Object logAuditEvent(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {

        // Extract user context
        String userId = extractUserId();
        String correlationId = MDC.get("correlationId");
        String action = auditLog.action();
        String description = auditLog.description();
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        // Build audit data
        Map<String, Object> auditData = new HashMap<>();
        auditData.put("timestamp", Instant.now());
        auditData.put("userId", userId);
        auditData.put("correlationId", correlationId);
        auditData.put("action", action);
        auditData.put("description", description);
        auditData.put("method", methodName);
        auditData.put("className", className);
        auditData.put("severity", auditLog.severity().name());

        // Log before execution
        long startTime = System.currentTimeMillis();
        log.info("AUDIT_START: {} - User {} executing {}", action, userId, methodName);

        Object result;
        try {
            // Execute the method
            result = joinPoint.proceed();

            auditData.put("status", "SUCCESS");
            log.info("AUDIT_SUCCESS: {}", action);

        } catch (Exception e) {
            auditData.put("status", "FAILURE");
            auditData.put("error", e.getMessage());
            auditData.put("errorType", e.getClass().getSimpleName());

            log.error("AUDIT_FAILURE: {} - Error: {}", action, e.getMessage());
            throw e;

        } finally {
            // Calculate duration and log complete audit event
            long duration = System.currentTimeMillis() - startTime;
            auditData.put("durationMs", duration);

            // Send structured audit log to CloudWatch
            // The AUDIT_EVENT prefix allows CloudWatch to filter audit logs
            log.info("AUDIT_EVENT: {}", auditData);

            // Log based on severity
            switch (auditLog.severity()) {
                case CRITICAL:
                    log.error("CRITICAL_AUDIT: User {} performed {} - Status: {} - Duration: {}ms",
                        userId, action, auditData.get("status"), duration);
                    break;
                case WARNING:
                    log.warn("WARNING_AUDIT: User {} performed {} - Status: {} - Duration: {}ms",
                        userId, action, auditData.get("status"), duration);
                    break;
                case INFO:
                default:
                    log.info("INFO_AUDIT: User {} performed {} - Status: {} - Duration: {}ms",
                        userId, action, auditData.get("status"), duration);
                    break;
            }
        }

        return result;
    }

    /**
     * Extract user ID from Security Context
     */
    private String extractUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }

        return "anonymous";
    }
}
