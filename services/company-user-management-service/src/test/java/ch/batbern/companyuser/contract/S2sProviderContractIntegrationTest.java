package ch.batbern.companyuser.contract;

import ch.batbern.companyuser.config.InMemoryCognito;
import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.Role;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
 * Provider half of the S2S contract (Stories 17.0b1, 17.0b2): every {@code /internal} operation in
 * {@code docs/api/internal/cums-internal.openapi.yml} runs through the real controller, and its
 * response must match the spec the consumers' {@code ContractServer} answers from; the spec
 * examples those consumers receive must match the same schema. Consumers: EMS
 * {@code UserApiClientImpl}, PCS {@code UserServiceClientImpl} and {@code CompanyServiceClientImpl},
 * and the gateway's {@code GatewayUserStatusClient}. Epic 17 entry 6 added the three speaker writes
 * and the company read, each run with a forwarded JWT and without any (ADR-016 decision 3).
 *
 * <p>No service calls a public CUMS operation any more: EMS since Epic 17 entry 6, PCS since 6b
 * (its company read and its {@code /users/me} moved to {@code /internal/companies/{name}} and
 * {@code /internal/users/{username}}).
 */
@Import(TestAwsConfig.class)
@DisplayName("CUMS honours the S2S contract its consumers are tested against")
class S2sProviderContractIntegrationTest extends AbstractIntegrationTest {

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

    // ------------------------------------------------------------------ /internal (Story 17.0b2)

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET,  /internal/users/{username}",
        "GET,  /internal/users",
        "GET,  /internal/email-owners",
        "GET,  /internal/companies",
        "POST, /internal/users/get-or-create",
        "POST, /internal/users/provision",
        "PATCH, /internal/users/{username}/profile",
        "POST, /internal/users/{username}/issue-invitation-credentials",
        "GET,  /internal/companies/{name}",
        "POST, /internal/mail-center-requests",
        "POST, /internal/mail-center-requests/{requestId}/decision",
        "GET,  /internal/user-references/preview",
        "POST, /internal/user-references/merge",
        "POST, /internal/user-references/primary-email",
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
    @DisplayName("GET /internal/users/{username}, plain and with include=settings, without any JWT")
    void internalUser_matchesSpec() throws Exception {
        internal(get("/internal/users/" + SPEAKER), 200);
        internal(get("/internal/users/" + SPEAKER).param("include", "settings"), 200);
        internal(get("/internal/users/" + SPEAKER).param("include", "company,roles,settings"), 200);
        internal(get("/internal/users/ghost.user"), 404);
    }

    @Test
    @DisplayName("6b: GET /internal/users/{username} with the partner's JWT forwarded, as PCS /partners/me sends it")
    void internalUser_withForwardedPartnerJwt_matchesSpec() throws Exception {
        MvcResult result = internal(get("/internal/users/" + SPEAKER).with(jwt().jwt(j -> j.subject("partner-sub")
                .claim("custom:username", SPEAKER).claim("custom:role", "PARTNER"))), 200);
        internal(get("/internal/users/ghost.user").with(jwt().jwt(j -> j.subject("partner-sub")
                .claim("custom:username", "ghost.user").claim("custom:role", "PARTNER"))), 404);

        assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.companyId")).isEqualTo(COMPANY);
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

    // ------------------------------------------------------------------ Epic 17 entry 6

    /** The organizer's JWT as EMS forwards it; {@code false} runs the call without any. */
    private static MockHttpServletRequestBuilder withUser(MockHttpServletRequestBuilder request, boolean user) {
        return user ? request.with(jwt().jwt(j -> j.subject("organizer-sub")
                .claim("custom:username", "organizer.alice").claim("custom:role", "ORGANIZER"))) : request;
    }

    @ParameterizedTest(name = "forwarded JWT: {0}")
    @CsvSource({"true", "false"})
    void internalProvision_matchesSpec(boolean user) throws Exception {
        internal(withUser(post("/internal/users/provision"), user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"contract.new@example.com\", \"role\": \"SPEAKER\"}"), 200);
        internal(withUser(post("/internal/users/provision"), user).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"contract.new@example.com\", \"role\": \"ORGANIZER\"}"), 400);
    }

    @ParameterizedTest(name = "forwarded JWT: {0}")
    @CsvSource({"true", "false"})
    void internalPatchProfile_matchesSpec(boolean user) throws Exception {
        internal(withUser(patch("/internal/users/" + SPEAKER + "/profile"), user)
                .contentType(MediaType.APPLICATION_JSON).content("{\"bio\": \"Architect.\"}"), 200);
        internal(withUser(patch("/internal/users/ghost.user/profile"), user)
                .contentType(MediaType.APPLICATION_JSON).content("{\"bio\": \"Architect.\"}"), 404);
    }

    @ParameterizedTest(name = "{0}, forwarded JWT: {1}")
    @CsvSource({"FORCE_CHANGE_PASSWORD, true", "CONFIRMED, false"})
    void internalIssueInvitationCredentials_matchesSpec(UserStatusType cognitoStatus, boolean user)
            throws Exception {
        cognito.givenLogin(SPEAKER_EMAIL, cognitoStatus);

        internal(withUser(post("/internal/users/" + SPEAKER + "/issue-invitation-credentials"), user), 200);
        internal(withUser(post("/internal/users/ghost.user/issue-invitation-credentials"), user), 404);
    }

    @ParameterizedTest(name = "forwarded JWT: {0}")
    @CsvSource({"true", "false"})
    void internalCompany_matchesSpec(boolean user) throws Exception {
        MvcResult result = internal(withUser(get("/internal/companies/" + COMPANY), user), 200);
        internal(withUser(get("/internal/companies/ghost"), user), 404);

        assertThat(result.getResponse().getContentAsString()).contains("\"Contract Co AG\"");
    }
}
