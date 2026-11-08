package ch.batbern.companyuser.contract;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.service.CognitoIntegrationService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserStatusType;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static ch.batbern.shared.test.contract.ProviderContract.assertExamplesMatchSchema;
import static ch.batbern.shared.test.contract.ProviderContract.assertResponseMatchesSpec;
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
 */
@Import(TestAwsConfig.class)
@DisplayName("CUMS honours the S2S contract its consumers are tested against")
class S2sProviderContractIntegrationTest extends AbstractIntegrationTest {

    private static final String USERS = "docs/api/users-api.openapi.yml";
    private static final String COMPANIES = "docs/api/companies-api.openapi.yml";
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
    private CognitoIntegrationService cognitoIntegrationService;

    @BeforeEach
    void seed() {
        cleanUp();
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
        // The mock is shared by the cached context and carries TestAwsConfig's default stubs:
        // undo only this test's stub (back to Mockito's default) and its recorded calls.
        Mockito.when(cognitoIntegrationService.getUserStatus(SPEAKER_EMAIL)).thenReturn(null);
        Mockito.clearInvocations(cognitoIntegrationService);
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
        Mockito.when(cognitoIntegrationService.getUserStatus(SPEAKER_EMAIL))
                .thenReturn(cognitoStatus);

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
}
