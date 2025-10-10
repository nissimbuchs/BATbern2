package ch.batbern.gateway.auth;

import ch.batbern.gateway.auth.model.AuthenticationEvent;
import ch.batbern.gateway.auth.model.AuthorizationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogger {

    private final AuditEventRepository auditEventRepository;

    public void logAuthorizationDecision(String userId, String resource, boolean granted) {
        log.info("Authorization decision - User: {}, Resource: {}, Granted: {}", userId, resource, granted);

        AuthorizationEvent event = AuthorizationEvent.of(userId, resource, granted);
        auditEventRepository.save(event);
    }

    public void logAuthorizationDecisionWithMetadata(String userId, String resource, boolean granted,
                                                     String requestId, String sessionId) {
        log.info("Authorization decision - User: {}, Resource: {}, Granted: {}, RequestId: {}, SessionId: {}",
            userId, resource, granted, requestId, sessionId);

        AuthorizationEvent event = AuthorizationEvent.withMetadata(userId, resource, granted, requestId, sessionId);
        auditEventRepository.save(event);
    }

    public void logFailedAuthentication(AuthenticationEvent authEvent) {
        log.warn("Failed authentication - User: {}, Email: {}, Error: {}, IP: {}",
            authEvent.getUserId(), authEvent.getEmail(), authEvent.getErrorMessage(), authEvent.getClientIp());

        auditEventRepository.save(authEvent);
    }

    public void logSuccessfulAuthentication(AuthenticationEvent authEvent) {
        log.info("Successful authentication - User: {}, Email: {}, IP: {}",
            authEvent.getUserId(), authEvent.getEmail(), authEvent.getClientIp());

        auditEventRepository.save(authEvent);
    }

    public void logSecurityEvent(String eventType, String description, String userId, String clientIp) {
        log.warn("Security event - Type: {}, Description: {}, User: {}, IP: {}",
            eventType, description, userId, clientIp);

        // Create a generic security event
        Object securityEvent = new SecurityEvent(eventType, description, userId, clientIp, LocalDateTime.now());
        auditEventRepository.save(securityEvent);
    }

    public void logPasswordResetAttempt(String email, String clientIp, String status) {
        log.info("Password reset attempt - Email: {}, IP: {}, Status: {}",
            maskEmail(email), clientIp, status);

        Object passwordResetEvent = new PasswordResetEvent(email, clientIp, status, LocalDateTime.now());
        auditEventRepository.save(passwordResetEvent);
    }

    public String formatAuditMessage(String userId, String action, String resource, boolean granted) {
        String decision = granted ? "GRANTED" : "DENIED";
        return String.format("User [%s] %s access to [%s] for action [%s]", userId, decision, resource, action);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@");
        String local = parts[0];
        String domain = parts[1];
        if (local.length() <= 1) {
            return "*@" + domain;
        }
        return local.charAt(0) + "***@" + domain;
    }

    // Inner class for security events
    private static class SecurityEvent {
        private final String eventType;
        private final String description;
        private final String userId;
        private final String clientIp;
        private final LocalDateTime timestamp;

        SecurityEvent(String eventType, String description, String userId,
                String clientIp, LocalDateTime timestamp) {
            this.eventType = eventType;
            this.description = description;
            this.userId = userId;
            this.clientIp = clientIp;
            this.timestamp = timestamp;
        }

        // Getters for JSON serialization
        public String getEventType() {
            return eventType;
        }

        public String getDescription() {
            return description;
        }

        public String getUserId() {
            return userId;
        }

        public String getClientIp() {
            return clientIp;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }

    // Inner class for password reset events
    private static class PasswordResetEvent {
        private final String email;
        private final String clientIp;
        private final String status;
        private final LocalDateTime timestamp;

        PasswordResetEvent(String email, String clientIp, String status, LocalDateTime timestamp) {
            this.email = email;
            this.clientIp = clientIp;
            this.status = status;
            this.timestamp = timestamp;
        }

        // Getters for JSON serialization
        public String getEmail() {
            return email;
        }

        public String getClientIp() {
            return clientIp;
        }

        public String getStatus() {
            return status;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }
    }
}