package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.GetOrCreateUserRequest;
import ch.batbern.companyuser.exception.CognitoOperationException;
import ch.batbern.companyuser.exception.UserNotFoundException;
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
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InternalErrorException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CognitoIntegrationServiceImpl}.
 *
 * Story 1.14-2 (AC2): NO-OP legacy methods.
 * Story 11.E.2 (AC3): real Admin SDK calls (adminCreateUserSilently / getUserStatus /
 * adminSetTemporaryPassword).
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

    @Captor
    private ArgumentCaptor<AdminGetUserRequest> getUserCaptor;

    @Captor
    private ArgumentCaptor<AdminSetUserPasswordRequest> setPasswordCaptor;

    private CognitoIntegrationService cognitoService;

    private final String userPoolId = "eu-central-1_TEST123";

    @BeforeEach
    void setUp() {
        cognitoService = new CognitoIntegrationServiceImpl(cognitoClient, userPoolId);
    }

    // --- Story 1.14-2 legacy NO-OP behaviour (kept) ----------------------------------------

    @Test
    @DisplayName("should_syncAllAttributes_when_syncUserAttributesCalled")
    void should_syncAllAttributes_when_syncUserAttributesCalled() {
        User user = User.builder()
                .username("john.doe")
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .cognitoUserId("cognito-123")
                .companyId("GoogleZH")
                .roles(new HashSet<>(Set.of(Role.ORGANIZER, Role.ATTENDEE)))
                .build();

        cognitoService.syncUserAttributes(user);

        verify(cognitoClient, never()).adminUpdateUserAttributes(any(AdminUpdateUserAttributesRequest.class));
    }

    @Test
    @DisplayName("should_handleNullCompanyId_when_syncingUserAttributes")
    void should_handleNullCompanyId_when_syncingUserAttributes() {
        User user = User.builder()
                .username("john.doe")
                .email("john.doe@example.com")
                .firstName("John")
                .lastName("Doe")
                .cognitoUserId("cognito-123")
                .companyId(null)
                .roles(new HashSet<>(Set.of(Role.ATTENDEE)))
                .build();

        cognitoService.syncUserAttributes(user);

        verify(cognitoClient, never()).adminUpdateUserAttributes(any(AdminUpdateUserAttributesRequest.class));
    }

    @Test
    @DisplayName("should_returnNull_when_createCognitoUserCalledOnLegacyNoOpPath")
    void should_returnNull_when_createCognitoUserCalledOnLegacyNoOpPath() {
        GetOrCreateUserRequest request = new GetOrCreateUserRequest()
                .email("jane.smith@example.com")
                .firstName("Jane")
                .lastName("Smith")
                .companyId("MicrosoftZH");

        String cognitoUserId = cognitoService.createCognitoUser(request);

        assertThat(cognitoUserId).isNull();
        verify(cognitoClient, never()).adminCreateUser(any(AdminCreateUserRequest.class));
    }

    // --- Story 11.E.2 adminCreateUserSilently --------------------------------------------------

    @Test
    @DisplayName("should_callAdminCreateUserWithSuppress_when_adminCreateUserSilentlyCalled")
    void should_callAdminCreateUserWithSuppress_when_adminCreateUserSilentlyCalled() {
        cognitoService.adminCreateUserSilently("speaker@example.com", "ThrowAway12!@#x", "speaker.bob");

        verify(cognitoClient).adminCreateUser(createUserCaptor.capture());
        AdminCreateUserRequest captured = createUserCaptor.getValue();
        assertThat(captured.userPoolId()).isEqualTo(userPoolId);
        assertThat(captured.username()).isEqualTo("speaker@example.com");
        assertThat(captured.temporaryPassword()).isEqualTo("ThrowAway12!@#x");
        assertThat(captured.messageAction()).isEqualTo(MessageActionType.SUPPRESS);

        List<AttributeType> attrs = captured.userAttributes();
        assertThat(attrs).extracting(AttributeType::name)
                .containsExactlyInAnyOrder("email", "email_verified", "preferred_username");
        assertThat(attrs).extracting(AttributeType::name, AttributeType::value)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("email", "speaker@example.com"),
                        org.assertj.core.groups.Tuple.tuple("email_verified", "true"),
                        org.assertj.core.groups.Tuple.tuple("preferred_username", "speaker.bob"));
    }

    @Test
    @DisplayName("should_swallowUsernameExistsException_when_adminCreateUserSilentlyHitsExistingUser")
    void should_swallowUsernameExistsException_when_adminCreateUserSilentlyHitsExistingUser() {
        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenThrow(UsernameExistsException.builder().message("already exists").build());

        // Method should return normally, not throw.
        cognitoService.adminCreateUserSilently("speaker@example.com", "ThrowAway12!@#x", "speaker.bob");

        verify(cognitoClient).adminCreateUser(any(AdminCreateUserRequest.class));
    }

    @Test
    @DisplayName("should_throwCognitoOperationException_when_adminCreateUserFailsForOtherReason")
    void should_throwCognitoOperationException_when_adminCreateUserFailsForOtherReason() {
        when(cognitoClient.adminCreateUser(any(AdminCreateUserRequest.class)))
                .thenThrow(InternalErrorException.builder().message("transient AWS error").build());

        assertThatThrownBy(() -> cognitoService.adminCreateUserSilently(
                "speaker@example.com", "ThrowAway12!@#x", "speaker.bob"))
                .isInstanceOf(CognitoOperationException.class)
                .extracting("action")
                .isEqualTo("adminCreateUser");
    }

    // --- Story 11.E.2 getUserStatus ------------------------------------------------------------

    @Test
    @DisplayName("should_returnUserStatus_when_getUserStatusSucceeds")
    void should_returnUserStatus_when_getUserStatusSucceeds() {
        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class)))
                .thenReturn(AdminGetUserResponse.builder()
                        .userStatus(UserStatusType.FORCE_CHANGE_PASSWORD)
                        .build());

        UserStatusType status = cognitoService.getUserStatus("speaker@example.com");

        assertThat(status).isEqualTo(UserStatusType.FORCE_CHANGE_PASSWORD);
        verify(cognitoClient).adminGetUser(getUserCaptor.capture());
        assertThat(getUserCaptor.getValue().userPoolId()).isEqualTo(userPoolId);
        assertThat(getUserCaptor.getValue().username()).isEqualTo("speaker@example.com");
    }

    @Test
    @DisplayName("should_throwUserNotFound_when_getUserStatusGetsCognitoUserNotFound")
    void should_throwUserNotFound_when_getUserStatusGetsCognitoUserNotFound() {
        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class)))
                .thenThrow(software.amazon.awssdk.services.cognitoidentityprovider.model
                        .UserNotFoundException.builder().message("not found").build());

        assertThatThrownBy(() -> cognitoService.getUserStatus("missing@example.com"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    @DisplayName("should_throwCognitoOperationException_when_getUserStatusFailsForOtherReason")
    void should_throwCognitoOperationException_when_getUserStatusFailsForOtherReason() {
        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class)))
                .thenThrow(InternalErrorException.builder().message("transient AWS error").build());

        assertThatThrownBy(() -> cognitoService.getUserStatus("speaker@example.com"))
                .isInstanceOf(CognitoOperationException.class)
                .extracting("action")
                .isEqualTo("adminGetUser");
    }

    // --- Story 11.E.2 adminSetTemporaryPassword -------------------------------------------------

    @Test
    @DisplayName("should_callAdminSetUserPasswordWithPermanentFalse_when_adminSetTemporaryPasswordCalled")
    void should_callAdminSetUserPasswordWithPermanentFalse_when_adminSetTemporaryPasswordCalled() {
        cognitoService.adminSetTemporaryPassword("speaker@example.com", "Fresh1234!@#abcde");

        verify(cognitoClient).adminSetUserPassword(setPasswordCaptor.capture());
        AdminSetUserPasswordRequest captured = setPasswordCaptor.getValue();
        assertThat(captured.userPoolId()).isEqualTo(userPoolId);
        assertThat(captured.username()).isEqualTo("speaker@example.com");
        assertThat(captured.password()).isEqualTo("Fresh1234!@#abcde");
        assertThat(captured.permanent()).isFalse();
    }

    @Test
    @DisplayName("should_throwCognitoOperationException_when_adminSetUserPasswordFails")
    void should_throwCognitoOperationException_when_adminSetUserPasswordFails() {
        when(cognitoClient.adminSetUserPassword(any(AdminSetUserPasswordRequest.class)))
                .thenThrow(InternalErrorException.builder().message("transient AWS error").build());

        assertThatThrownBy(() -> cognitoService.adminSetTemporaryPassword(
                "speaker@example.com", "Fresh1234!@#abcde"))
                .isInstanceOf(CognitoOperationException.class)
                .extracting("action")
                .isEqualTo("adminSetUserPassword");
    }
}
