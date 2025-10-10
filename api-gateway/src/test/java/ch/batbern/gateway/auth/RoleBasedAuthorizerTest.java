package ch.batbern.gateway.auth;

import ch.batbern.gateway.auth.model.UserContext;
import ch.batbern.gateway.auth.exception.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RoleBasedAuthorizerTest {

    private RoleBasedAuthorizer roleBasedAuthorizer;

    @Mock
    private AuditLogger auditLogger;

    @BeforeEach
    void setUp() {
        roleBasedAuthorizer = new RoleBasedAuthorizer(auditLogger);
    }

    // Test 9.1: should_allowOrganizerAccess_when_organizerEndpointCalled
    @Test
    @DisplayName("should_allowOrganizerAccess_when_organizerEndpointCalled")
    void should_allowOrganizerAccess_when_organizerEndpointCalled() {
        // Given
        UserContext organizerContext = UserContext.builder()
            .userId("org-123")
            .role("organizer")
            .companyId("company-456")
            .build();

        String resource = "/api/events/create";
        String action = "CREATE";

        // When
        boolean hasPermission = roleBasedAuthorizer.hasPermission(organizerContext, resource, action);

        // Then
        assertThat(hasPermission).isTrue();
        verify(auditLogger).logAuthorizationDecision(eq("org-123"), eq(resource), eq(true));
    }

    // Test 9.2: should_denyUnauthorizedAccess_when_insufficientPermissions
    @Test
    @DisplayName("should_denyUnauthorizedAccess_when_insufficientPermissions")
    void should_denyUnauthorizedAccess_when_insufficientPermissions() {
        // Given
        UserContext attendeeContext = UserContext.builder()
            .userId("att-789")
            .role("attendee")
            .build();

        String resource = "/api/events/create";
        String action = "CREATE";

        // When
        boolean hasPermission = roleBasedAuthorizer.hasPermission(attendeeContext, resource, action);

        // Then
        assertThat(hasPermission).isFalse();
        verify(auditLogger).logAuthorizationDecision(eq("att-789"), eq(resource), eq(false));
    }

    // Test 9.3: should_logAuthorizationDecision_when_accessChecked
    @Test
    @DisplayName("should_logAuthorizationDecision_when_accessChecked")
    void should_logAuthorizationDecision_when_accessChecked() {
        // Given
        UserContext speakerContext = UserContext.builder()
            .userId("spk-456")
            .role("speaker")
            .build();

        String resource = "/api/speakers/profile";
        String action = "READ";

        // When
        roleBasedAuthorizer.hasPermission(speakerContext, resource, action);

        // Then
        verify(auditLogger, times(1)).logAuthorizationDecision(eq("spk-456"), eq(resource), any(Boolean.class));
    }

    // Test 9.4: should_returnForbiddenResponse_when_accessDenied
    @Test
    @DisplayName("should_returnForbiddenResponse_when_accessDenied")
    void should_returnForbiddenResponse_when_accessDenied() {
        // Given
        UserContext attendeeContext = UserContext.builder()
            .userId("att-123")
            .role("attendee")
            .build();

        String resource = "/api/partners/analytics";
        String action = "READ";

        // When / Then
        assertThatThrownBy(() -> roleBasedAuthorizer.enforcePermission(attendeeContext, resource, action))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("Access denied");
    }

    @Test
    @DisplayName("should_allowSpeakerAccess_when_speakerResourceAccessed")
    void should_allowSpeakerAccess_when_speakerResourceAccessed() {
        // Given
        UserContext speakerContext = UserContext.builder()
            .userId("spk-789")
            .role("speaker")
            .build();

        String resource = "/api/speakers/materials";
        String action = "UPDATE";

        // When
        boolean hasPermission = roleBasedAuthorizer.hasPermission(speakerContext, resource, action);

        // Then
        assertThat(hasPermission).isTrue();
    }

    @Test
    @DisplayName("should_allowPartnerAccess_when_partnerResourceAccessed")
    void should_allowPartnerAccess_when_partnerResourceAccessed() {
        // Given
        UserContext partnerContext = UserContext.builder()
            .userId("ptr-456")
            .role("partner")
            .companyId("partner-company-123")
            .build();

        String resource = "/api/partners/analytics";
        String action = "READ";

        // When
        boolean hasPermission = roleBasedAuthorizer.hasPermission(partnerContext, resource, action);

        // Then
        assertThat(hasPermission).isTrue();
    }

    @Test
    @DisplayName("should_denyPartnerAccess_when_organizerResourceAccessed")
    void should_denyPartnerAccess_when_organizerResourceAccessed() {
        // Given
        UserContext partnerContext = UserContext.builder()
            .userId("ptr-789")
            .role("partner")
            .build();

        String resource = "/api/events/delete";
        String action = "DELETE";

        // When
        boolean hasPermission = roleBasedAuthorizer.hasPermission(partnerContext, resource, action);

        // Then
        assertThat(hasPermission).isFalse();
    }

    @Test
    @DisplayName("should_allowAttendeeReadAccess_when_contentResourceAccessed")
    void should_allowAttendeeReadAccess_when_contentResourceAccessed() {
        // Given
        UserContext attendeeContext = UserContext.builder()
            .userId("att-456")
            .role("attendee")
            .build();

        String resource = "/api/content/search";
        String action = "READ";

        // When
        boolean hasPermission = roleBasedAuthorizer.hasPermission(attendeeContext, resource, action);

        // Then
        assertThat(hasPermission).isTrue();
    }

    @Test
    @DisplayName("should_supportHierarchicalPermissions_when_organizerAccessesAllResources")
    void should_supportHierarchicalPermissions_when_organizerAccessesAllResources() {
        // Given
        UserContext organizerContext = UserContext.builder()
            .userId("org-456")
            .role("organizer")
            .build();

        // When / Then - Organizer should have access to all resources
        assertThat(roleBasedAuthorizer.hasPermission(organizerContext, "/api/events/create", "CREATE")).isTrue();
        assertThat(roleBasedAuthorizer.hasPermission(organizerContext, "/api/speakers/invite", "CREATE")).isTrue();
        assertThat(roleBasedAuthorizer.hasPermission(organizerContext, "/api/partners/reports", "READ")).isTrue();
        assertThat(roleBasedAuthorizer.hasPermission(organizerContext, "/api/content/manage", "UPDATE")).isTrue();
    }

    @Test
    @DisplayName("should_handleNullUserContext_when_noUserProvided")
    void should_handleNullUserContext_when_noUserProvided() {
        // Given
        String resource = "/api/events/list";
        String action = "READ";

        // When / Then
        assertThatThrownBy(() -> roleBasedAuthorizer.hasPermission(null, resource, action))
            .isInstanceOf(AuthorizationException.class)
            .hasMessageContaining("User context is required");
    }

    @Test
    @DisplayName("should_handleInvalidRole_when_unknownRoleProvided")
    void should_handleInvalidRole_when_unknownRoleProvided() {
        // Given
        UserContext invalidRoleContext = UserContext.builder()
            .userId("usr-123")
            .role("invalid-role")
            .build();

        String resource = "/api/events/list";
        String action = "READ";

        // When
        boolean hasPermission = roleBasedAuthorizer.hasPermission(invalidRoleContext, resource, action);

        // Then
        assertThat(hasPermission).isFalse();
        verify(auditLogger).logAuthorizationDecision(eq("usr-123"), eq(resource), eq(false));
    }
}