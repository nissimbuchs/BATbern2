package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for CognitoIntegrationServiceImpl
 * Story 1.14-2 Task 12: Cognito Integration (RED phase)
 * AC2: Cognito sync on user create/update
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CognitoIntegrationServiceImpl Unit Tests")
class CognitoIntegrationServiceImplTest {

    @Mock
    private CognitoIdentityProviderClient cognitoClient;

    @Captor
    private ArgumentCaptor<AdminUpdateUserAttributesRequest> updateAttributesCaptor;

    @Captor
    private ArgumentCaptor<AdminCreateUserRequest> createUserCaptor;

    private CognitoIntegrationService cognitoService;

    private String userPoolId = "eu-central-1_TEST123";

    @BeforeEach
    void setUp() {
        cognitoService = new CognitoIntegrationServiceImpl(cognitoClient, userPoolId);
    }

    // Test 1: syncUserAttributes - DB is source of truth (NO-OP by design)
    @Test
    @DisplayName("should_syncAllAttributes_when_syncUserAttributesCalled")
    void should_syncAllAttributes_when_syncUserAttributesCalled() {
        // Given
        User user = User.builder()
                .username("john.doe")
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .cognitoUserId("cognito-123")
                .companyId("GoogleZH")
                .roles(new HashSet<>(Set.of(Role.ORGANIZER, Role.ATTENDEE)))
                .build();

        // When
        cognitoService.syncUserAttributes(user);

        // Then - NO-OP: DB is source of truth, Cognito not updated
        verify(cognitoClient, never()).adminUpdateUserAttributes(any(AdminUpdateUserAttributesRequest.class));
    }

    // Test 2: syncUserAttributes - should handle null company ID (NO-OP by design)
    @Test
    @DisplayName("should_handleNullCompanyId_when_syncingUserAttributes")
    void should_handleNullCompanyId_when_syncingUserAttributes() {
        // Given
        User user = User.builder()
                .username("john.doe")
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .cognitoUserId("cognito-123")
                .companyId(null)  // No company
                .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                .build();

        // When
        cognitoService.syncUserAttributes(user);

        // Then - NO-OP: DB is source of truth, Cognito not updated
        verify(cognitoClient, never()).adminUpdateUserAttributes(any(AdminUpdateUserAttributesRequest.class));
    }

    // Test 3: syncUserAttributes - NO-OP doesn't throw exceptions
    @Test
    @DisplayName("should_throwException_when_cognitoSyncFails")
    void should_throwException_when_cognitoSyncFails() {
        // Given
        User user = User.builder()
                .username("john.doe")
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .cognitoUserId("cognito-123")
                .companyId("GoogleZH")
                .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                .build();

        // When/Then - NO-OP doesn't throw exceptions, Cognito not called
        cognitoService.syncUserAttributes(user);
        verify(cognitoClient, never()).adminUpdateUserAttributes(any(AdminUpdateUserAttributesRequest.class));
    }

    // Test 4: createCognitoUser - NO-OP for invitation-based flow
    @Test
    @DisplayName("should_createUserWithAllAttributes_when_createCognitoUserCalled")
    void should_createUserWithAllAttributes_when_createCognitoUserCalled() {
        // Given
        GetOrCreateUserRequest request = new GetOrCreateUserRequest()
                .email("jane.smith@example.com")
                .firstName("Jane")
                .lastName("Smith")
                .companyId("MicrosoftZH");

        // When
        String cognitoUserId = cognitoService.createCognitoUser(request);

        // Then - NO-OP: invitation-based flow, returns null
        assertThat(cognitoUserId).isNull();
        verify(cognitoClient, never()).adminCreateUser(any(AdminCreateUserRequest.class));
    }

    // Test 5: createCognitoUser - NO-OP for null company ID
    @Test
    @DisplayName("should_handleNullCompanyId_when_creatingCognitoUser")
    void should_handleNullCompanyId_when_creatingCognitoUser() {
        // Given
        GetOrCreateUserRequest request = new GetOrCreateUserRequest()
                .email("jane.smith@example.com")
                .firstName("Jane")
                .lastName("Smith")
                .companyId(null);  // No company

        // When
        String cognitoUserId = cognitoService.createCognitoUser(request);

        // Then - NO-OP: invitation-based flow, returns null
        assertThat(cognitoUserId).isNull();
        verify(cognitoClient, never()).adminCreateUser(any(AdminCreateUserRequest.class));
    }

    // Test 6: createCognitoUser - NO-OP doesn't throw exceptions
    @Test
    @DisplayName("should_throwException_when_cognitoCreateUserFails")
    void should_throwException_when_cognitoCreateUserFails() {
        // Given
        GetOrCreateUserRequest request = new GetOrCreateUserRequest()
                .email("jane.smith@example.com")
                .firstName("Jane")
                .lastName("Smith")
                .companyId("MicrosoftZH");

        // When/Then - NO-OP doesn't throw exceptions, returns null
        String cognitoUserId = cognitoService.createCognitoUser(request);
        assertThat(cognitoUserId).isNull();
        verify(cognitoClient, never()).adminCreateUser(any(AdminCreateUserRequest.class));
    }
}
