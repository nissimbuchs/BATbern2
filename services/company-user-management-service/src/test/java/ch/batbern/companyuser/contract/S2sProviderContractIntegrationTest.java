package ch.batbern.companyuser.contract;

import ch.batbern.companyuser.config.InMemoryCognito;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static ch.batbern.shared.test.contract.ProviderContract.assertExamplesMatchSchema;
import static ch.batbern.shared.test.contract.ProviderContract.assertResponseMatchesSpec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider half of the S2S contract (Story 17.0b1): every CUMS operation that EMS or PCS call on
 * the public API runs through the real controller, and its response must match the spec the
 * consumers' {@code ContractServer} answers from. The spec examples those consumers receive must
 * match the same schema.
 *
 * <p>Consumers: EMS {@code UserApiClientImpl} (provision, patch profile, invitation credentials,
 * company list and detail), PCS {@code CompanyServiceClientImpl} (company detail) and
 * {@code UserServiceClientImpl} ({@code /users/me}).
 *
 * <p>Story 17.0b2: the same for every {@code /internal} operation in
 * {@code docs/api/internal/cums-internal.openapi.yml}, called by EMS {@code UserApiClientImpl},
 * PCS {@code UserServiceClientImpl} and the gateway's {@code GatewayUserStatusClient}.
 */
@Import(TestAwsConfig.class)
@DisplayName("CUMS honours the S2S contract its consumers are tested against")
class S2sProviderContractIntegrationTest extends AbstractIntegrationTest {

    private static final String USERS = "docs/api/users-api.openapi.yml";
    private static final String COMPANIES = "docs/api/companies-api.openapi.yml";
    private static final String CUMS_INTERNAL = "docs/api/internal/cums-internal.openapi.yml";
    private static final String SPEAKER = "contract.speaker";
    private static final String SPEAKER_EMAIL = "contract.speaker@example.com";
    private static final String COMPANY = "ContractCo";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private InMemoryCognito cognito;

    @BeforeEach
    void seed() {
        cleanUp();
        cognito.reset();
        companyRepository.save(Company.builder()
                .name(COMPANY).displayName("Contract Co AG").isVerified(true)
                .createdAt(Instant.now()).updatedAt(Instant.now()).createdBy("organizer.alice")
                .build());
        userRepository.save(User.builder()
                .username(SPEAKER).email(SPEAKER_EMAIL)
                .firstName("Contract").lastName("Speaker").companyId(COMPANY)
                .roles(new HashSet<>(Set.of(Role.SPEAKER)))
                .build());
    }

    @AfterEach
    void cleanUp() {
        userRepository.findByUsername(SPEAKER).ifPresent(userRepository::delete);
        userRepository.findByEmailIgnoreCase("contract.new@example.com").ifPresent(userRepository::delete);
        companyRepository.findByName(COMPANY).ifPresent(companyRepository::delete);
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "users-api, POST,  /api/v1/users/provision",
        "users-api, PATCH, /api/v1/users/{username}/profile",
        "users-api, POST,  /api/v1/users/{username}/issue-invitation-credentials",
        "users-api, GET,   /api/v1/users/me",
        "companies-api, GET, /api/v1/companies",
        "companies-api, GET, /api/v1/companies/{name}",
    })
    @DisplayName("spec examples of consumed operations match their schema")
    void should_haveValidExamples_forConsumedOperations(String spec, String method, String path) {
        assertExamplesMatchSchema("docs/api/" + spec + ".openapi.yml", method, path);
    }

    @Test
    @WithMockUser(username = "organizer.alice", roles = "ORGANIZER")
    void provision_matchesSpec() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/users/provision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"contract.new@example.com\", \"role\": \"SPEAKER\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertResponseMatchesSpec(USERS, result);
    }

    @Test
    @WithMockUser(username = "organizer.alice", roles = "ORGANIZER")
    void patchProfile_matchesSpec() throws Exception {
        MvcResult result = mockMvc.perform(patch("/api/v1/users/" + SPEAKER + "/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bio\": \"Architect.\"}"))
                .andExpect(status().isOk())
                .andReturn();

        assertResponseMatchesSpec(USERS, result);
    }

    @ParameterizedTest
    @CsvSource({"FORCE_CHANGE_PASSWORD", "CONFIRMED"})
    @WithMockUser(username = "organizer.alice", roles = "ORGANIZER")
    void issueInvitationCredentials_matchesSpec(UserStatusType cognitoStatus) throws Exception {
        cognito.givenLogin(SPEAKER_EMAIL, cognitoStatus);

        MvcResult result = mockMvc.perform(post("/api/v1/users/" + SPEAKER + "/issue-invitation-credentials"))
                .andExpect(status().isOk())
                .andReturn();

        assertResponseMatchesSpec(USERS, result);
    }

    @Test
    void currentUser_matchesSpec() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/users/me")
                        .with(jwt().jwt(j -> j.subject("contract-sub").claim("custom:username", SPEAKER))))
                .andExpect(status().isOk())
                .andReturn();

        assertResponseMatchesSpec(USERS, result);
    }

    @Test
    @WithMockUser(username = "organizer.alice", roles = "ORGANIZER")
    void companyList_asPagedByEms_matchesSpec() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/companies").param("page", "1").param("limit", "100"))
                .andExpect(status().isOk())
                .andReturn();

        assertResponseMatchesSpec(COMPANIES, result);
    }

    @Test
    @WithMockUser(username = "organizer.alice", roles = "ORGANIZER")
    void companyDetail_matchesSpec() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/companies/" + COMPANY))
                .andExpect(status().isOk())
                .andReturn();

        assertResponseMatchesSpec(COMPANIES, result);
    }

    // ------------------------------------------------------------------ /internal (Story 17.0b2)

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET,  /internal/users/{username}",
        "GET,  /internal/users",
        "GET,  /internal/email-owners",
        "GET,  /internal/companies",
        "POST, /internal/users/get-or-create",
    })
    @DisplayName("internal spec examples match their schema")
    void should_haveValidExamples_forInternalOperations(String method, String path) {
        assertExamplesMatchSchema(CUMS_INTERNAL, method, path);
    }

    private MvcResult internal(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
        assertResponseMatchesSpec(CUMS_INTERNAL, result);
        return result;
    }

    @Test
    @DisplayName("GET /internal/users/{username}, plain and with include=preferences, without any JWT")
    void internalUser_matchesSpec() throws Exception {
        internal(get("/internal/users/" + SPEAKER), 200);
        internal(get("/internal/users/" + SPEAKER).param("include", "preferences"), 200);
        internal(get("/internal/users/" + SPEAKER).param("include", "company,roles,preferences"), 200);
        internal(get("/internal/users/ghost.user"), 404);
    }

    @Test
    @DisplayName("an encoded path segment is decoded: the provider looks up exactly the name the client sent")
    void internalUser_decodesEncodedSegment() throws Exception {
        // CUMS usernames match ^[a-z]+\.[a-z]+(\.[0-9]+)?$ (DB check), so this one cannot exist: a 404
        // naming the decoded value proves the segment arrived whole, not cut at the apostrophe or space.
        MvcResult result = internal(get(URI.create("/internal/users/jean-luc.o%27brien%20x")), 404);

        assertThat(result.getResponse().getContentAsString()).contains("jean-luc.o'brien x");
    }

    @Test
    @DisplayName("GET /internal/users?role&page&limit, as EMS and PCS page it")
    void internalUserList_matchesSpec() throws Exception {
        MvcResult result = internal(get("/internal/users").param("role", "SPEAKER").param("page", "1")
                .param("limit", "100"), 200);
        internal(get("/internal/users").param("role", "SPEAKER").param("company", COMPANY).param("page", "1")
                .param("limit", "100"), 200);

        assertThat(result.getResponse().getContentAsString()).contains("\"" + SPEAKER + "\"");
    }

    @Test
    @DisplayName("GET /internal/companies?search, as the PCS partner search calls it (Story 17.17)")
    void internalCompanyNames_matchesSpec() throws Exception {
        MvcResult byDisplayName = internal(get("/internal/companies").param("search", "contract co"), 200);
        internal(get("/internal/companies").param("search", "zz-no-such-company-zz"), 200);
        internal(get("/internal/companies"), 200);

        assertThat(byDisplayName.getResponse().getContentAsString()).contains("\"" + COMPANY + "\"");
    }

    @Test
    void internalEmailOwners_matchesSpec() throws Exception {
        MvcResult known = internal(get("/internal/email-owners").param("email", SPEAKER_EMAIL), 200);
        internal(get("/internal/email-owners").param("email", "nobody@example.com"), 200);

        assertThat(known.getResponse().getContentAsString().replaceAll("\\s", "")).isEqualTo("[\"" + SPEAKER + "\"]");
    }

    @Test
    void internalGetOrCreate_matchesSpec() throws Exception {
        internal(post("/internal/users/get-or-create").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "%s", "firstName": "Contract", "lastName": "Speaker", "cognitoSync": false}"""
                .formatted(SPEAKER_EMAIL)), 200);
        internal(post("/internal/users/get-or-create").contentType(MediaType.APPLICATION_JSON).content("""
                {"email": "contract.new@example.com", "firstName": "Contract", "lastName": "New",
                 "cognitoSync": false}"""), 200);
    }
}
