package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.InvitationCredentialsResponse;
import ch.batbern.companyuser.exception.UnprocessableInvitationStateException;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.CognitoIntegrationService;
import ch.batbern.companyuser.service.UserService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Integration tests for Story 11.E.2 AC11 #5-#8 — {@code UserService.issueInvitationCredentials}.
 *
 * <p>Covers the four Cognito-status branches from AC2: FORCE_CHANGE_PASSWORD,
 * CONFIRMED, UNCONFIRMED, and the operator-intervention statuses
 * (ARCHIVED/COMPROMISED/UNKNOWN).
 */
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("Story 11.E.2 — UserService.issueInvitationCredentials integration")
class UserServiceIssueCredentialsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CognitoIntegrationService cognitoIntegrationService;

    @BeforeEach
    void resetCognitoMock() {
        Mockito.reset(cognitoIntegrationService);
    }

    private User seedSpeaker(String username, String email) {
        return userRepository.save(User.builder()
                .username(username)
                .email(email)
                .firstName("Speaker")
                .lastName("Test")
                .roles(new HashSet<>(Set.of(Role.SPEAKER)))
                .build());
    }

    @Test
    @DisplayName("AC11 #5: FORCE_CHANGE_PASSWORD → fresh temp password issued")
    void should_returnFreshTempPassword_when_userInForceChangePassword() {
        User u = seedSpeaker("speaker.fcp", "speaker.fcp@example.com");

        Mockito.when(cognitoIntegrationService.getUserStatus(u.getEmail()))
                .thenReturn(UserStatusType.FORCE_CHANGE_PASSWORD);

        InvitationCredentialsResponse response = userService.issueInvitationCredentials(u.getUsername());

        assertThat(response.getAction())
                .isEqualTo(InvitationCredentialsResponse.ActionEnum.FRESH_TEMP_PASSWORD);
        assertThat(response.getTemporaryPassword()).isNotNull().hasSize(16);

        // adminSetTemporaryPassword called exactly once with a 16-char password that matches
        // the response value.
        Mockito.verify(cognitoIntegrationService).adminSetTemporaryPassword(
                eq(u.getEmail()),
                argThat(pw -> pw != null && pw.length() == 16
                        && pw.equals(response.getTemporaryPassword())));
    }

    @Test
    @DisplayName("AC11 #6: CONFIRMED → no Cognito mutation, returns USE_EXISTING_PASSWORD")
    void should_returnNullPassword_when_userInConfirmed() {
        User u = seedSpeaker("speaker.cnf", "speaker.cnf@example.com");

        Mockito.when(cognitoIntegrationService.getUserStatus(u.getEmail()))
                .thenReturn(UserStatusType.CONFIRMED);

        InvitationCredentialsResponse response = userService.issueInvitationCredentials(u.getUsername());

        assertThat(response.getAction())
                .isEqualTo(InvitationCredentialsResponse.ActionEnum.USE_EXISTING_PASSWORD);
        assertThat(response.getTemporaryPassword()).isNull();

        // No password mutation.
        Mockito.verify(cognitoIntegrationService, Mockito.never())
                .adminSetTemporaryPassword(anyString(), anyString());
    }

    @Test
    @DisplayName("AC11 #7: UNCONFIRMED → defensively treat as FORCE_CHANGE_PASSWORD (fresh temp password)")
    void should_treatUnconfirmedAsForceChangePassword() {
        User u = seedSpeaker("speaker.unc", "speaker.unc@example.com");

        Mockito.when(cognitoIntegrationService.getUserStatus(u.getEmail()))
                .thenReturn(UserStatusType.UNCONFIRMED);

        InvitationCredentialsResponse response = userService.issueInvitationCredentials(u.getUsername());

        assertThat(response.getAction())
                .isEqualTo(InvitationCredentialsResponse.ActionEnum.FRESH_TEMP_PASSWORD);
        assertThat(response.getTemporaryPassword()).isNotNull().hasSize(16);

        Mockito.verify(cognitoIntegrationService).adminSetTemporaryPassword(
                eq(u.getEmail()), anyString());
    }

    @Test
    @DisplayName("AC11 #8: ARCHIVED → throws UnprocessableInvitationStateException (422)")
    void should_throw422_when_userInArchivedOrCompromised() {
        User u = seedSpeaker("speaker.arc", "speaker.arc@example.com");

        Mockito.when(cognitoIntegrationService.getUserStatus(u.getEmail()))
                .thenReturn(UserStatusType.ARCHIVED);

        assertThatThrownBy(() -> userService.issueInvitationCredentials(u.getUsername()))
                .isInstanceOf(UnprocessableInvitationStateException.class)
                .hasMessageContaining("ARCHIVED")
                .hasMessageContaining("operator intervention");

        // No Cognito mutation on the 422 path.
        Mockito.verify(cognitoIntegrationService, Mockito.never())
                .adminSetTemporaryPassword(anyString(), anyString());
    }
}
