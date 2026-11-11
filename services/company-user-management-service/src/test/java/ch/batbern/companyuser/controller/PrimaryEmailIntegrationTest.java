package ch.batbern.companyuser.controller;

import ch.batbern.companyuser.client.EventManagementContract;
import ch.batbern.companyuser.client.HttpEventManagementUserMergeClient;
import ch.batbern.companyuser.config.InMemoryCognito;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserAdditionalEmail;
import ch.batbern.companyuser.repository.UserAdditionalEmailRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

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
 *
 * <p>Story 4.10d: the address is chosen by its {@code id}, loaded scoped to the caller. The happy
 * path runs only here, with Cognito at the boundary ({@link InMemoryCognito}), never against a real
 * pool. Every test commits; {@code @AfterEach} removes the profiles.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestAwsConfig.class)
@DisplayName("#1244 — make an additional address the primary one")
class PrimaryEmailIntegrationTest extends AbstractIntegrationTest {

    private static final String WORK = "anna@work.example.com";
    private static final String PRIVATE = "anna@private.example.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserAdditionalEmailRepository additionalEmailRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    /** Cognito (the boundary): Anna's login is her work address. */
    @Autowired
    private InMemoryCognito cognito;
    /** The real client; event-management answers through the contract harness at the wire. */
    @Autowired
    private HttpEventManagementUserMergeClient eventManagementClient;

    private ContractServer eventManagement;
    private String annasSub;
    private UUID privateId;
    private UUID pendingId;
    private UUID foreignId;

    @BeforeEach
    void setUp() {
        removeProfiles();
        cognito.reset();
        eventManagement = EventManagementContract.bindTo(eventManagementClient);
        annasSub = cognito.givenLogin(WORK, UserStatusType.CONFIRMED).sub();
        User anna = userRepository.save(User.builder().username("anna.muster").email(WORK)
                .firstName("Anna").lastName("Muster").cognitoUserId(annasSub)
                .roles(new HashSet<>(Set.of(Role.ATTENDEE))).build());
        privateId = address(anna, PRIVATE, true);
        pendingId = address(anna, "anna@pending.example.com", false);
        User bert = userRepository.save(User.builder().username("bert.beispiel").email("bert@example.com")
                .firstName("Bert").lastName("Beispiel").roles(new HashSet<>(Set.of(Role.ATTENDEE))).build());
        foreignId = address(bert, "bert@private.example.com", true);
    }

    @AfterEach
    void tearDown() {
        eventManagement.close();
        removeProfiles();
    }

    private void removeProfiles() {
        jdbcTemplate.update("DELETE FROM user_profiles WHERE username IN ('anna.muster', 'bert.beispiel')");
    }

    private UUID address(User owner, String email, boolean verified) {
        return additionalEmailRepository.saveAndFlush(UserAdditionalEmail.builder().user(owner).email(email)
                .verifiedAt(verified ? Instant.now() : null).createdAt(Instant.now()).build()).getId();
    }

    private String makePrimaryPath(Object id) {
        return "/api/v1/users/me/additional-emails/" + id + "/make-primary";
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

        mockMvc.perform(post(makePrimaryPath(privateId)))
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
                .andExpect(jsonPath("$.additionalEmails[?(@.email == '" + PRIVATE + "')]").isEmpty())
                // the old primary is a new row: confirmed, under a new id; the chosen id is gone
                .andExpect(jsonPath("$.additionalEmails[?(@.email == '" + WORK + "')].id")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.not(privateId.toString()),
                                org.hamcrest.Matchers.not(pendingId.toString())))));
        assertThat(additionalEmailRepository.findById(privateId)).isEmpty();
    }

    @Test
    @WithMockUser(username = "anna.muster")
    @DisplayName("an unconfirmed address cannot become the primary (409 NOT_VERIFIED), Cognito untouched")
    void should_return409_when_addressUnconfirmed() throws Exception {
        expectNoPrimaryEmailChange();
        mockMvc.perform(post(makePrimaryPath(pendingId)))
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
        mockMvc.perform(post(makePrimaryPath(privateId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_HAS_OTHER_LOGIN"));

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(jsonPath("$.email").value(WORK));
        assertThat(loginEmail()).isEqualTo(WORK);
        eventManagement.verify();
    }

    @Test
    @WithMockUser(username = "anna.muster")
    @DisplayName("someone else's or an unknown id is 404; no profile, login or event-management change")
    void should_return404_when_notOwnAddress() throws Exception {
        expectNoPrimaryEmailChange();
        mockMvc.perform(post(makePrimaryPath(foreignId)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(makePrimaryPath(UUID.randomUUID())))
                .andExpect(status().isNotFound());

        assertThat(loginEmail()).isEqualTo(WORK);
        assertThat(userRepository.findByUsername("anna.muster").orElseThrow().getEmail()).isEqualTo(WORK);
        assertThat(userRepository.findByUsername("bert.beispiel").orElseThrow().getEmail())
                .isEqualTo("bert@example.com");
        assertThat(additionalEmailRepository.findById(foreignId)).isPresent();
        eventManagement.verify();
    }

    @Test
    @DisplayName("needs a login (401)")
    void should_return401_when_anonymous() throws Exception {
        mockMvc.perform(post(makePrimaryPath(privateId)))
                .andExpect(status().isUnauthorized());
        assertThat(loginEmail()).isEqualTo(WORK);
    }

    @Test
    @WithMockUser(username = "anna.muster")
    @DisplayName("4.10d: the old address path is gone (400, the segment is no id); login unchanged")
    void should_return400_when_addressedByEmail() throws Exception {
        expectNoPrimaryEmailChange();
        mockMvc.perform(post(makePrimaryPath(PRIVATE)))
                .andExpect(status().isBadRequest());
        assertThat(loginEmail()).isEqualTo(WORK);
        eventManagement.verify();
    }
}
