package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.config.InMemoryCognito;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.dto.generated.ProvisionUserRequest;
import ch.batbern.companyuser.exception.CognitoOperationException;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.UserService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test for Story 11.E.2 AC11 #3 — verifies the {@code @Transactional}
 * rollback on {@code UserService.provisionUserWithRole} when the Cognito Admin SDK call
 * fails.
 *
 * <p>This test is intentionally <strong>NOT</strong> {@code @Transactional} — Spring's
 * test-managed transaction would mask the rollback under verification. We assert on
 * committed-or-rolled-back state by querying the repository after the exception.
 */
@Import(TestAwsConfig.class)
@DisplayName("Story 11.E.2 AC11 #3 — provisionUserWithRole rollback on Cognito failure")
class UserProvisioningCognitoRollbackIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InMemoryCognito cognito;

    @BeforeEach
    void setUp() {
        cognito.reset();
        // Defensive: ensure no leftover row from a previous run by this test class.
        userRepository.findByEmailIgnoreCase("rollback.victim@example.com").ifPresent(userRepository::delete);
    }

    @AfterEach
    void tearDown() {
        userRepository.findByEmailIgnoreCase("rollback.victim@example.com").ifPresent(userRepository::delete);
    }

    @Test
    @WithMockUser(username = "organizer.alice", roles = {"ORGANIZER"})
    @DisplayName("provision rolls back the User row when AdminCreateUser fails")
    void should_rollbackUserRow_when_cognitoCreateFails() {
        // Cognito fails with a non-idempotent error (NOT UsernameExistsException).
        cognito.failNext(InMemoryCognito.Operation.ADMIN_CREATE_USER,
                InMemoryCognito.internalError("transient AWS error"));

        ProvisionUserRequest request = new ProvisionUserRequest(
                "rollback.victim@example.com",
                ProvisionUserRequest.RoleEnum.SPEAKER);
        request.setFirstName("Rollback");
        request.setLastName("Victim");

        assertThatThrownBy(() -> userService.provisionUserWithRole(request))
                .isInstanceOf(CognitoOperationException.class);

        // After the exception propagates, the @Transactional rolls back: no User row persists,
        // and Cognito holds no login for the address either.
        assertThat(userRepository.findByEmailIgnoreCase("rollback.victim@example.com")).isEmpty();
        assertThat(cognito.login("rollback.victim@example.com")).isEmpty();
    }
}
