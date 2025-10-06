package ch.batbern.gateway.auth;

import ch.batbern.gateway.auth.model.AuthorizationEvent;
import ch.batbern.gateway.auth.model.AuthenticationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLoggerTest {

    private AuditLogger auditLogger;

    @Mock
    private AuditEventRepository auditEventRepository;

    @BeforeEach
    void setUp() {
        auditLogger = new AuditLogger(auditEventRepository);
    }

    @Test
    @DisplayName("should_logAuthorizationDecision_when_accessChecked")
    void should_logAuthorizationDecision_when_accessChecked() {
        // Given
        String userId = "user-123";
        String resource = "/api/events/create";
        boolean granted = true;

        // When
        auditLogger.logAuthorizationDecision(userId, resource, granted);

        // Then
        ArgumentCaptor<AuthorizationEvent> eventCaptor = ArgumentCaptor.forClass(AuthorizationEvent.class);
        verify(auditEventRepository).save(eventCaptor.capture());

        AuthorizationEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getUserId()).isEqualTo(userId);
        assertThat(capturedEvent.getResource()).isEqualTo(resource);
        assertThat(capturedEvent.isGranted()).isEqualTo(granted);
        assertThat(capturedEvent.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("should_logFailedAuthentication_when_invalidCredentials")
    void should_logFailedAuthentication_when_invalidCredentials() {
        // Given
        AuthenticationEvent authEvent = AuthenticationEvent.builder()
            .userId("user-456")
            .email("test@example.com")
            .attemptType("JWT_VALIDATION")
            .success(false)
            .errorMessage("Invalid token signature")
            .clientIp("192.168.1.100")
            .userAgent("BATbern-Frontend/1.0")
            .build();

        // When
        auditLogger.logFailedAuthentication(authEvent);

        // Then
        verify(auditEventRepository).save(authEvent);
    }

    @Test
    @DisplayName("should_logSuccessfulAuthentication_when_validCredentials")
    void should_logSuccessfulAuthentication_when_validCredentials() {
        // Given
        AuthenticationEvent authEvent = AuthenticationEvent.builder()
            .userId("user-789")
            .email("organizer@batbern.ch")
            .attemptType("JWT_VALIDATION")
            .success(true)
            .clientIp("192.168.1.101")
            .userAgent("BATbern-Frontend/1.0")
            .build();

        // When
        auditLogger.logSuccessfulAuthentication(authEvent);

        // Then
        verify(auditEventRepository).save(authEvent);
    }

    @Test
    @DisplayName("should_logSecurityEvent_when_suspiciousActivityDetected")
    void should_logSecurityEvent_when_suspiciousActivityDetected() {
        // Given
        String eventType = "SUSPICIOUS_ACTIVITY";
        String description = "Multiple failed login attempts";
        String userId = "user-456";
        String clientIp = "192.168.1.200";

        // When
        auditLogger.logSecurityEvent(eventType, description, userId, clientIp);

        // Then
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(auditEventRepository).save(eventCaptor.capture());

        // Verify that security event was logged with proper details
        assertThat(eventCaptor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("should_includeRequestMetadata_when_loggingAuthorizationEvent")
    void should_includeRequestMetadata_when_loggingAuthorizationEvent() {
        // Given
        String userId = "user-123";
        String resource = "/api/partners/analytics";
        boolean granted = false;
        String requestId = "req-456";
        String sessionId = "sess-789";

        // When
        auditLogger.logAuthorizationDecisionWithMetadata(userId, resource, granted, requestId, sessionId);

        // Then
        ArgumentCaptor<AuthorizationEvent> eventCaptor = ArgumentCaptor.forClass(AuthorizationEvent.class);
        verify(auditEventRepository).save(eventCaptor.capture());

        AuthorizationEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.getRequestId()).isEqualTo(requestId);
        assertThat(capturedEvent.getSessionId()).isEqualTo(sessionId);
    }

    @Test
    @DisplayName("should_handleNullValues_when_partialDataProvided")
    void should_handleNullValues_when_partialDataProvided() {
        // Given
        String userId = null;
        String resource = "/api/events/list";
        boolean granted = false;

        // When / Then
        assertThatCode(() -> auditLogger.logAuthorizationDecision(userId, resource, granted))
            .doesNotThrowAnyException();

        verify(auditEventRepository).save(any(AuthorizationEvent.class));
    }

    @Test
    @DisplayName("should_batchLogEvents_when_multipleEventsGenerated")
    void should_batchLogEvents_when_multipleEventsGenerated() {
        // Given
        String[] userIds = {"user-1", "user-2", "user-3"};
        String resource = "/api/content/search";

        // When
        for (String userId : userIds) {
            auditLogger.logAuthorizationDecision(userId, resource, true);
        }

        // Then
        verify(auditEventRepository, times(3)).save(any(AuthorizationEvent.class));
    }

    @Test
    @DisplayName("should_formatAuditMessage_when_structuredLoggingRequired")
    void should_formatAuditMessage_when_structuredLoggingRequired() {
        // Given
        String userId = "user-123";
        String action = "CREATE";
        String resource = "/api/events/create";
        boolean granted = true;

        // When
        String auditMessage = auditLogger.formatAuditMessage(userId, action, resource, granted);

        // Then
        assertThat(auditMessage).contains(userId);
        assertThat(auditMessage).contains(action);
        assertThat(auditMessage).contains(resource);
        assertThat(auditMessage).contains("GRANTED");
    }
}