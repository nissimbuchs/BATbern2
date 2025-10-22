package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.GetOrCreateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    // Test 1: syncUserAttributes - should sync all user attributes to Cognito
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

        when(cognitoClient.adminUpdateUserAttributes(any(AdminUpdateUserAttributesRequest.class)))
                .thenReturn(AdminUpdateUserAttributesResponse.builder().build());

        // When
        cognitoService.syncUserAttributes(user);

        // Then
        verify(cognitoClient).adminUpdateUserAttributes(updateAttributesCaptor.capture());
        AdminUpdateUserAttributesRequest request = updateAttributesCaptor.getValue();

        assertThat(request.userPoolId()).isEqualTo(userPoolId);
        assertThat(request.username()).isEqualTo("cognito-123");
        assertThat(request.userAttributes()).hasSize(5);
        assertThat(request.userAttributes()).containsExactlyInAnyOrder(
                AttributeType.builder().name("email").value("john.doe@example.com").build(),
                AttributeType.builder().name("given_name").value("John").build(),
                AttributeType.builder().name("family_name").value("Doe").build(),
                AttributeType.builder().name("custom:companyId").value("GoogleZH").build(),
                AttributeType.builder().name("custom:role").value("ATTENDEE,ORGANIZER").build()  // Sorted alphabetically
        );
    }

    // Test 2: syncUserAttributes - should handle null company ID
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

        when(cognitoClient.adminUpdateUserAttributes(any(AdminUpdateUserAttributesRequest.class)))
                .thenReturn(AdminUpdateUserAttributesResponse.builder().build());

        // When
        cognitoService.syncUserAttributes(user);

        // Then
        verify(cognitoClient).adminUpdateUserAttributes(updateAttributesCaptor.capture());
        AdminUpdateUserAttributesRequest request = updateAttributesCaptor.getValue();

        assertThat(request.userAttributes()).hasSize(4);  // No companyId attribute
        assertThat(request.userAttributes()).noneMatch(attr -> attr.name().equals("custom:companyId"));
    }

    // Test 3: syncUserAttributes - should throw exception when Cognito fails
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

        when(cognitoClient.adminUpdateUserAttributes(any(AdminUpdateUserAttributesRequest.class)))
                .thenThrow(UserNotFoundException.builder().message("User not found in Cognito").build());

        // When/Then
        assertThatThrownBy(() -> cognitoService.syncUserAttributes(user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to sync user attributes to Cognito");
    }

    // Test 4: createCognitoUser - should create user with all attributes
    @Test
    @DisplayName("should_createUserWithAllAttributes_when_createCognitoUserCalled")
    void should_createUserWithAllAttributes_when_createCognitoUserCalled() {
        // Given
        GetOrCreateUserRequest request = GetOrCreateUserRequest.builder()
                .email("jane.smith@example.com")
                .firstName("Jane")
                .lastName("Smith")
                .companyId("MicrosoftZH")
                .build();

        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenReturn(AdminCreateUserResponse.builder()
                        .user(UserType.builder()
                                .username("cognito-new-user-123")
                                .build())
                        .build());

        // When
        String cognitoUserId = cognitoService.createCognitoUser(request);

        // Then
        assertThat(cognitoUserId).isEqualTo("cognito-new-user-123");

        verify(cognitoClient).adminCreateUser(createUserCaptor.capture());
        AdminCreateUserRequest cognitoRequest = createUserCaptor.getValue();

        assertThat(cognitoRequest.userPoolId()).isEqualTo(userPoolId);
        assertThat(cognitoRequest.username()).isEqualTo("jane.smith@example.com");  // Email as username
        assertThat(cognitoRequest.userAttributes()).containsExactlyInAnyOrder(
                AttributeType.builder().name("email").value("jane.smith@example.com").build(),
                AttributeType.builder().name("email_verified").value("true").build(),
                AttributeType.builder().name("given_name").value("Jane").build(),
                AttributeType.builder().name("family_name").value("Smith").build(),
                AttributeType.builder().name("custom:companyId").value("MicrosoftZH").build(),
                AttributeType.builder().name("custom:role").value("ATTENDEE").build()  // Default role
        );
        assertThat(cognitoRequest.desiredDeliveryMediums()).containsExactly(DeliveryMediumType.EMAIL);
        assertThat(cognitoRequest.messageAction()).isEqualTo(MessageActionType.SUPPRESS);  // Don't send welcome email
    }

    // Test 5: createCognitoUser - should handle null company ID
    @Test
    @DisplayName("should_handleNullCompanyId_when_creatingCognitoUser")
    void should_handleNullCompanyId_when_creatingCognitoUser() {
        // Given
        GetOrCreateUserRequest request = GetOrCreateUserRequest.builder()
                .email("jane.smith@example.com")
                .firstName("Jane")
                .lastName("Smith")
                .companyId(null)  // No company
                .build();

        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenReturn(AdminCreateUserResponse.builder()
                        .user(UserType.builder()
                                .username("cognito-new-user-123")
                                .build())
                        .build());

        // When
        String cognitoUserId = cognitoService.createCognitoUser(request);

        // Then
        assertThat(cognitoUserId).isEqualTo("cognito-new-user-123");

        verify(cognitoClient).adminCreateUser(createUserCaptor.capture());
        AdminCreateUserRequest cognitoRequest = createUserCaptor.getValue();

        assertThat(cognitoRequest.userAttributes()).noneMatch(attr -> attr.name().equals("custom:companyId"));
    }

    // Test 6: createCognitoUser - should throw exception when Cognito fails
    @Test
    @DisplayName("should_throwException_when_cognitoCreateUserFails")
    void should_throwException_when_cognitoCreateUserFails() {
        // Given
        GetOrCreateUserRequest request = GetOrCreateUserRequest.builder()
                .email("jane.smith@example.com")
                .firstName("Jane")
                .lastName("Smith")
                .companyId("MicrosoftZH")
                .build();

        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenThrow(UsernameExistsException.builder().message("User already exists").build());

        // When/Then
        assertThatThrownBy(() -> cognitoService.createCognitoUser(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to create user in Cognito");
    }
}
