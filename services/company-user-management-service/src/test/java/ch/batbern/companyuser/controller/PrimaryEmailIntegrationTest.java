package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.client.EventManagementContract;
import ch.batbern.companyuser.client.HttpEventManagementUserMergeClient;
import ch.batbern.companyuser.config.InMemoryCognito;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer;
import ch.batbern.shared.types.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #1244 step 2 — "Als Hauptadresse verwenden": a confirmed additional address becomes the primary
 * (login) address. Cognito moves the login first; then the profile swaps the two addresses, the
 * old primary stays as a confirmed additional address; event-management moves upcoming
 * registrations made under the old address.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestAwsConfig.class)
@DisplayName("#1244 — make an additional address the primary one")
class PrimaryEmailIntegrationTest extends AbstractIntegrationTest {

    private static final String WORK = "anna@work.example.com";
    private static final String PRIVATE = "anna@private.example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    /** Cognito (the boundary): Anna's login is her work address. */
    @Autowired
    private InMemoryCognito cognito;
    /** The real client; event-management answers through the contract harness at the wire. */
    @Autowired
    private HttpEventManagementUserMergeClient eventManagementClient;

    private ContractServer eventManagement;
    private String annasSub;

    @BeforeEach
    void setUp() {
        cognito.reset();
        eventManagement = EventManagementContract.bindTo(eventManagementClient);
        annasSub = cognito.givenLogin(WORK, UserStatusType.CONFIRMED).sub();
        User anna = userRepository.save(User.builder().username("anna.muster").email(WORK)
                .firstName("Anna").lastName("Muster").cognitoUserId(annasSub)
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build());
        anna.addAdditionalEmail(UserAdditionalEmail.builder().email(PRIVATE)
                .verifiedAt(Instant.now()).createdAt(Instant.now()).build());
        anna.addAdditionalEmail(UserAdditionalEmail.builder().email("anna@pending.example.com")
                .createdAt(Instant.now()).build());
        userRepository.saveAndFlush(anna);
    }

    @AfterEach
    void tearDown() {
        eventManagement.close();
    }

    /** Telling event-management about a new primary fails the test at {@code verify()}. */
    private void expectNoPrimaryEmailChange() {
        eventManagement.expect(POST, "/internal/users/primary-email").times(ExpectedCount.never())
                .respondWithExample();
    }

    private String loginEmail() {
        return cognito.login(annasSub).orElseThrow().email();
    }

    @Test
    @WithMockUser(username = "anna.muster")
    @DisplayName("a confirmed address becomes the primary; the old primary stays as a confirmed address")
    void should_swapAddresses_when_confirmedAddressMadePrimary() throws Exception {
        eventManagement.expect(POST, "/internal/users/primary-email")
                .andExpect(r -> assertThat(((MockClientHttpRequest) r).getBodyAsString().replaceAll("\\s", ""))
                        .contains("\"username\":\"anna.muster\"", "\"oldEmail\":\"" + WORK + "\"",
                                "\"newEmail\":\"" + PRIVATE + "\""))
                .respondWithExample();

        mockMvc.perform(post("/api/v1/users/me/additional-emails/Anna@Private.example.com/make-primary"))
                .andExpect(status().isNoContent());

        eventManagement.verify();
        assertThat(loginEmail()).isEqualTo(PRIVATE);
        assertThat(cognito.login(annasSub).orElseThrow().attribute("email_verified")).isEqualTo("true");
        assertThat(cognito.login(WORK)).isEmpty();

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(PRIVATE))
                .andExpect(jsonPath("$.additionalEmails[?(@.email == '" + WORK + "')].verifiedAt")
                        .exists())
                .andExpect(jsonPath("$.additionalEmails[?(@.email == '" + PRIVATE + "')]").isEmpty());
    }

    @Test
    @WithMockUser(username = "anna.muster")
    @DisplayName("an unconfirmed address cannot become the primary (409 NOT_VERIFIED), Cognito untouched")
    void should_return409_when_addressUnconfirmed() throws Exception {
        expectNoPrimaryEmailChange();
        mockMvc.perform(post("/api/v1/users/me/additional-emails/anna@pending.example.com/make-primary"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NOT_VERIFIED"));
        assertThat(loginEmail()).isEqualTo(WORK);
        eventManagement.verify();
    }

    @Test
    @WithMockUser(username = "anna.muster")
    @DisplayName("the address is another login's → 409 EMAIL_HAS_OTHER_LOGIN, profile unchanged")
    void should_return409_when_addressIsAnotherLogin() throws Exception {
        cognito.givenLogin(PRIVATE, UserStatusType.CONFIRMED);

        expectNoPrimaryEmailChange();
        mockMvc.perform(post("/api/v1/users/me/additional-emails/" + PRIVATE + "/make-primary"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_HAS_OTHER_LOGIN"));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(jsonPath("$.email").value(WORK));
        assertThat(loginEmail()).isEqualTo(WORK);
        eventManagement.verify();
    }

    @Test
    @WithMockUser(username = "anna.muster")
    @DisplayName("someone else's or an unknown address is 404")
    void should_return404_when_notOwnAddress() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails/nobody@example.com/make-primary"))
                .andExpect(status().isNotFound());
        assertThat(loginEmail()).isEqualTo(WORK);
    }

    @Test
    @DisplayName("needs a login (401)")
    void should_return401_when_anonymous() throws Exception {
        mockMvc.perform(post("/api/v1/users/me/additional-emails/" + PRIVATE + "/make-primary"))
                .andExpect(status().isUnauthorized());
    }
}
