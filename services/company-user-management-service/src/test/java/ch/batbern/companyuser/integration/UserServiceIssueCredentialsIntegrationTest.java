package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.config.InMemoryCognito;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.shared.types.Role;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.dto.generated.InvitationCredentialsResponse;
import ch.batbern.companyuser.exception.UnprocessableInvitationStateException;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.UserService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Story 11.E.2 AC11 #5-#8 — {@code UserService.issueInvitationCredentials}.
 *
 * <p>Covers the four Cognito-status branches from AC2: FORCE_CHANGE_PASSWORD,
 * CONFIRMED, UNCONFIRMED, and the operator-intervention statuses
 * (ARCHIVED/COMPROMISED/UNKNOWN), plus the self-heal when the login is missing. The real
 * {@code CognitoIntegrationServiceImpl} runs on the in-memory Cognito; the tests assert the
 * login's state there.
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
    private InMemoryCognito cognito;

    @BeforeEach
    void resetCognito() {
        cognito.reset();
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

        cognito.givenLogin(u.getEmail(), UserStatusType.FORCE_CHANGE_PASSWORD);

        InvitationCredentialsResponse response = userService.issueInvitationCredentials(u.getUsername());

        assertThat(response.getAction())
                .isEqualTo(InvitationCredentialsResponse.ActionEnum.FRESH_TEMP_PASSWORD);
        assertThat(response.getTemporaryPassword()).isNotNull().hasSize(16);

        // The login now has exactly that password, temporary (the speaker must change it).
        InMemoryCognito.Login login = cognito.login(u.getEmail()).orElseThrow();
        assertThat(login.password()).isEqualTo(response.getTemporaryPassword());
        assertThat(login.passwordPermanent()).isFalse();
        assertThat(login.status()).isEqualTo(UserStatusType.FORCE_CHANGE_PASSWORD);
    }

    @Test
    @DisplayName("AC11 #6: CONFIRMED → no Cognito mutation, returns USE_EXISTING_PASSWORD")
    void should_returnNullPassword_when_userInConfirmed() {
        User u = seedSpeaker("speaker.cnf", "speaker.cnf@example.com");

        cognito.givenLogin(u.getEmail(), UserStatusType.CONFIRMED);

        InvitationCredentialsResponse response = userService.issueInvitationCredentials(u.getUsername());

        assertThat(response.getAction())
                .isEqualTo(InvitationCredentialsResponse.ActionEnum.USE_EXISTING_PASSWORD);
        assertThat(response.getTemporaryPassword()).isNull();

        // No password mutation: the speaker keeps the password they chose.
        InMemoryCognito.Login login = cognito.login(u.getEmail()).orElseThrow();
        assertThat(login.password()).isNull();
        assertThat(login.status()).isEqualTo(UserStatusType.CONFIRMED);
    }

    @Test
    @DisplayName("AC11 #7: UNCONFIRMED → defensively treat as FORCE_CHANGE_PASSWORD (fresh temp password)")
    void should_treatUnconfirmedAsForceChangePassword() {
        User u = seedSpeaker("speaker.unc", "speaker.unc@example.com");

        cognito.givenLogin(u.getEmail(), UserStatusType.UNCONFIRMED);

        InvitationCredentialsResponse response = userService.issueInvitationCredentials(u.getUsername());

        assertThat(response.getAction())
                .isEqualTo(InvitationCredentialsResponse.ActionEnum.FRESH_TEMP_PASSWORD);
        assertThat(response.getTemporaryPassword()).isNotNull().hasSize(16);

        assertThat(cognito.login(u.getEmail()).orElseThrow().password()).isEqualTo(response.getTemporaryPassword());
    }

    @Test
    @DisplayName("AC11 #8: ARCHIVED → throws UnprocessableInvitationStateException (422)")
    void should_throw422_when_userInArchivedOrCompromised() {
        User u = seedSpeaker("speaker.arc", "speaker.arc@example.com");

        cognito.givenLogin(u.getEmail(), UserStatusType.ARCHIVED);

        assertThatThrownBy(() -> userService.issueInvitationCredentials(u.getUsername()))
                .isInstanceOf(UnprocessableInvitationStateException.class)
                .hasMessageContaining("ARCHIVED")
                .hasMessageContaining("operator intervention");

        // No Cognito mutation on the 422 path.
        InMemoryCognito.Login login = cognito.login(u.getEmail()).orElseThrow();
        assertThat(login.password()).isNull();
        assertThat(login.status()).isEqualTo(UserStatusType.ARCHIVED);
    }

    @Test
    @DisplayName("no login in Cognito → the shell is created, linked to the profile, and a temp password issued")
    void should_createAndLinkLogin_when_cognitoHasNone() {
        User u = seedSpeaker("speaker.missing", "speaker.missing@example.com");
        assertThat(cognito.login(u.getEmail())).isEmpty();

        InvitationCredentialsResponse response = userService.issueInvitationCredentials(u.getUsername());

        assertThat(response.getAction())
                .isEqualTo(InvitationCredentialsResponse.ActionEnum.FRESH_TEMP_PASSWORD);
        InMemoryCognito.Login login = cognito.login(u.getEmail()).orElseThrow();
        assertThat(login.password()).isEqualTo(response.getTemporaryPassword());
        assertThat(login.attribute("preferred_username")).isEqualTo("speaker.missing");
        assertThat(login.attribute("custom:role")).isEqualTo("UNUSED");
        assertThat(userRepository.findByUsername("speaker.missing").orElseThrow().getCognitoUserId())
                .isEqualTo(login.sub());
    }
}
