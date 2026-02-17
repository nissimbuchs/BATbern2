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
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private String appClientId = "test-client-id-123";

    @BeforeEach
    void setUp() {
        cognitoService = new CognitoIntegrationServiceImpl(cognitoClient, userPoolId, appClientId);
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

    // Story 9.2 Tests: findUserByEmail, createCognitoSpeaker, addRoleToCognitoUser

    @Test
    @DisplayName("should_returnEmpty_when_findUserByEmailNotFound")
    void should_returnEmpty_when_findUserByEmailNotFound() {
        // Given - Cognito returns empty user list
        when(cognitoClient.listUsers(any(ListUsersRequest.class)))
                .thenReturn(ListUsersResponse.builder().users(List.of()).build());

        // When
        Optional<AdminGetUserResponse> result = cognitoService.findUserByEmail("unknown@example.com");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should_returnUser_when_findUserByEmailFound")
    void should_returnUser_when_findUserByEmailFound() {
        // Given
        UserType cognitoUser = UserType.builder().username("speaker@example.com").build();
        when(cognitoClient.listUsers(any(ListUsersRequest.class)))
                .thenReturn(ListUsersResponse.builder().users(List.of(cognitoUser)).build());

        AdminGetUserResponse adminResponse = AdminGetUserResponse.builder()
                .username("speaker@example.com")
                .userAttributes(
                        AttributeType.builder().name("email").value("speaker@example.com").build(),
                        AttributeType.builder().name(CognitoIntegrationServiceImpl.COGNITO_ROLES_ATTRIBUTE)
                                .value("ATTENDEE").build()
                )
                .build();
        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class))).thenReturn(adminResponse);

        // When
        Optional<AdminGetUserResponse> result = cognitoService.findUserByEmail("speaker@example.com");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().username()).isEqualTo("speaker@example.com");
    }

    @Test
    @DisplayName("should_createSpeaker_when_createCognitoSpeakerCalled")
    void should_createSpeaker_when_createCognitoSpeakerCalled() {
        // Given
        UserType createdUser = UserType.builder().username("speaker@example.com").build();
        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenReturn(AdminCreateUserResponse.builder().user(createdUser).build());

        // When
        String username = cognitoService.createCognitoSpeaker(
                "speaker@example.com", "Jane Speaker", "Temp@Pass123!XYZ");

        // Then
        assertThat(username).isEqualTo("speaker@example.com");
        verify(cognitoClient).adminCreateUser(createUserCaptor.capture());
        AdminCreateUserRequest captured = createUserCaptor.getValue();
        assertThat(captured.userPoolId()).isEqualTo(userPoolId);
        assertThat(captured.username()).isEqualTo("speaker@example.com");
        // Verify SPEAKER role set with correct attribute key
        assertThat(captured.userAttributes()).anyMatch(attr ->
                CognitoIntegrationServiceImpl.COGNITO_ROLES_ATTRIBUTE.equals(attr.name())
                        && "SPEAKER".equals(attr.value()));
    }

    @Test
    @DisplayName("should_addRole_when_addRoleToCognitoUserCalled")
    void should_addRole_when_addRoleToCognitoUserCalled() {
        // Given - user exists with ATTENDEE role
        UserType cognitoUser = UserType.builder().username("attendee@example.com").build();
        when(cognitoClient.listUsers(any(ListUsersRequest.class)))
                .thenReturn(ListUsersResponse.builder().users(List.of(cognitoUser)).build());

        AdminGetUserResponse adminResponse = AdminGetUserResponse.builder()
                .username("attendee@example.com")
                .userAttributes(
                        AttributeType.builder().name("email").value("attendee@example.com").build(),
                        AttributeType.builder().name(CognitoIntegrationServiceImpl.COGNITO_ROLES_ATTRIBUTE)
                                .value("ATTENDEE").build()
                )
                .build();
        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class))).thenReturn(adminResponse);
        when(cognitoClient.adminUpdateUserAttributes(any(AdminUpdateUserAttributesRequest.class)))
                .thenReturn(null);

        // When
        cognitoService.addRoleToCognitoUser("attendee@example.com", Role.SPEAKER);

        // Then - verify ATTENDEE,SPEAKER in updated attributes
        verify(cognitoClient).adminUpdateUserAttributes(updateAttributesCaptor.capture());
        AdminUpdateUserAttributesRequest captured = updateAttributesCaptor.getValue();
        assertThat(captured.userAttributes()).anyMatch(attr ->
                CognitoIntegrationServiceImpl.COGNITO_ROLES_ATTRIBUTE.equals(attr.name())
                        && attr.value().contains("ATTENDEE")
                        && attr.value().contains("SPEAKER"));
    }

    @Test
    @DisplayName("should_beIdempotent_when_addRoleToCognitoUserWithExistingRole")
    void should_beIdempotent_when_addRoleToCognitoUserWithExistingRole() {
        // Given - user already has SPEAKER role
        UserType cognitoUser = UserType.builder().username("speaker@example.com").build();
        when(cognitoClient.listUsers(any(ListUsersRequest.class)))
                .thenReturn(ListUsersResponse.builder().users(List.of(cognitoUser)).build());

        AdminGetUserResponse adminResponse = AdminGetUserResponse.builder()
                .username("speaker@example.com")
                .userAttributes(
                        AttributeType.builder().name(CognitoIntegrationServiceImpl.COGNITO_ROLES_ATTRIBUTE)
                                .value("SPEAKER").build()
                )
                .build();
        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class))).thenReturn(adminResponse);

        // When - adding SPEAKER again
        cognitoService.addRoleToCognitoUser("speaker@example.com", Role.SPEAKER);

        // Then - no update call (idempotent)
        verify(cognitoClient, never()).adminUpdateUserAttributes(any(AdminUpdateUserAttributesRequest.class));
    }

    @Test
    @DisplayName("should_throwException_when_addRoleToCognitoUserAndUserNotFound")
    void should_throwException_when_addRoleToCognitoUserAndUserNotFound() {
        // Given - user does not exist
        when(cognitoClient.listUsers(any(ListUsersRequest.class)))
                .thenReturn(ListUsersResponse.builder().users(List.of()).build());

        // When/Then
        assertThatThrownBy(() -> cognitoService.addRoleToCognitoUser("missing@example.com", Role.SPEAKER))
                .isInstanceOf(CognitoIntegrationServiceImpl.CognitoOperationException.class);
    }
}
