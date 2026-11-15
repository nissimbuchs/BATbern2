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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for Story 11.E.2 AC11 #3 — verifies the {@code @Transactional}
 * rollback on {@code UserService.provisionUserWithRole} when the Cognito Admin SDK call
 * fails.
 *
 * <p>This test is intentionally <strong>NOT</strong> {@code @Transactional} — Spring's
 * test-managed transaction would mask the rollback under verification. We assert on
 * committed-or-rolled-back state by querying the repository after the exception.
 *
 * <p>Every Cognito failure, a service error ({@code InternalErrorException}) or a client failure
 * ({@code SdkClientException}: no credentials, no connection, a timeout), is a
 * {@code CognitoOperationException}: the row rolls back and the caller gets 502 with the standard
 * error body.
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

    @Autowired
    private MockMvc mockMvc;

    private static final String PROVISION_BODY = """
            {"email": "rollback.victim@example.com", "firstName": "Rollback", "lastName": "Victim",
             "role": "SPEAKER"}""";

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

        assertThatThrownBy(() -> userService.provisionUserWithRole(request, "system:test"))
                .isInstanceOf(CognitoOperationException.class);

        // After the exception propagates, the @Transactional rolls back: no User row persists,
        // and Cognito holds no login for the address either.
        assertThat(userRepository.findByEmailIgnoreCase("rollback.victim@example.com")).isEmpty();
        assertThat(cognito.login("rollback.victim@example.com")).isEmpty();
    }

    @Test
    @WithMockUser(username = "organizer.alice", roles = {"ORGANIZER"})
    @DisplayName("provision rolls back the User row when the Cognito client itself fails")
    void should_rollbackUserRow_when_cognitoClientFails() {
        cognito.failNext(InMemoryCognito.Operation.ADMIN_CREATE_USER,
                SdkClientException.create("Unable to load credentials from any of the providers"));

        ProvisionUserRequest request = new ProvisionUserRequest(
                "rollback.victim@example.com",
                ProvisionUserRequest.RoleEnum.SPEAKER);
        request.setFirstName("Rollback");
        request.setLastName("Victim");

        assertThatThrownBy(() -> userService.provisionUserWithRole(request, "system:test"))
                .isInstanceOf(CognitoOperationException.class);

        assertThat(userRepository.findByEmailIgnoreCase("rollback.victim@example.com")).isEmpty();
        assertThat(cognito.login("rollback.victim@example.com")).isEmpty();
    }

    @Test
    @DisplayName("POST /internal/users/provision answers 502 when the Cognito client fails")
    void should_answer502_when_cognitoClientFailsOnProvision() throws Exception {
        assertProvisionAnswers502(SdkClientException.create("Unable to load credentials from any of the providers"));
    }

    @Test
    @DisplayName("POST /internal/users/provision answers 502 when Cognito answers an internal error")
    void should_answer502_when_cognitoServiceFailsOnProvision() throws Exception {
        assertProvisionAnswers502(InMemoryCognito.internalError("transient AWS error"));
    }

    private void assertProvisionAnswers502(SdkException failure) throws Exception {
        cognito.failNext(InMemoryCognito.Operation.ADMIN_CREATE_USER, failure);

        // No user JWT: EMS calls provision from the speaker workflow, /internal authorizes by operation
        mockMvc.perform(post("/internal/users/provision")
                        .contentType(MediaType.APPLICATION_JSON).content(PROVISION_BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status", is(502)))
                .andExpect(jsonPath("$.error", is("Bad Gateway")))
                .andExpect(jsonPath("$.message", is("Identity provider unavailable; please retry shortly")))
                .andExpect(jsonPath("$.correlationId", notNullValue()))
                .andExpect(jsonPath("$.path", is("/internal/users/provision")));

        assertThat(userRepository.findByEmailIgnoreCase("rollback.victim@example.com")).isEmpty();
        assertThat(cognito.login("rollback.victim@example.com")).isEmpty();
    }
}
