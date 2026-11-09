package ch.batbern.companyuser.contract;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.companyuser.domain.Company;
import ch.batbern.companyuser.repository.CompanyRepository;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static ch.batbern.shared.test.contract.ProviderContract.assertResponseMatchesSpec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The {@code pattern}s of the companies spec accept the values CUMS really produces (17.0 deferred
 * work). The MSW harness found in 17.0c1 that {@code CompanyResponse.createdBy}
 * ({@code ^[a-z]+\.[a-z]+(\.[0-9]+)?$}) rejects real companies: 74 of 712 in the anonymized database
 * were created by {@code anonymous} (a company named during an anonymous registration) and 10 by a
 * Cognito subject (a JWT without {@code custom:username}). One company name there had spaces, which
 * {@code POST /companies} stored unchanged; since the owner decision of 2026-11-09 it stores the slug
 * ({@code ^[a-zA-Z0-9]{1,12}$}, the response pattern again) and keeps the typed name as display name.
 *
 * <p>Each company is created through the real flow, then read back through the real controller and
 * checked against the spec.
 */
@Import(TestAwsConfig.class)
@DisplayName("CUMS companies spec patterns accept the values CUMS produces")
class SpecPatternsAcceptProducedValuesIntegrationTest extends AbstractIntegrationTest {

    private static final String COMPANIES = "docs/api/companies-api.openapi.yml";
    private static final String REGISTRANT_EMAIL = "pattern.registrant@example.com";
    private static final String COMPANY_DISPLAY = "Pattern Probe Registrant AG";
    private static final String SPACED_NAME = "Pattern Probe AG";
    private static final String SUB_NAME = "PatternSubCo";
    private static final String COGNITO_SUB = "3f2b8c1e-5d4a-4e6f-9a7b-1c2d3e4f5a6b";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        userRepository.findByEmailIgnoreCase(REGISTRANT_EMAIL).ifPresent(userRepository::delete);
        companyRepository.findAll().stream()
                .filter(c -> List.of(COMPANY_DISPLAY, SPACED_NAME, SUB_NAME).contains(c.getDisplayName()))
                .forEach(companyRepository::delete);
    }

    @Test
    @DisplayName("a company named in an anonymous registration (createdBy anonymous) matches the spec")
    void anonymouslyCreatedCompany_matchesSpec() throws Exception {
        // EMS registers an anonymous attendee: no JWT, the company is a display name
        MvcResult created = mockMvc.perform(post("/internal/users/get-or-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "firstName": "Pattern", "lastName": "Registrant",
                                 "companyId": "%s", "cognitoSync": false}"""
                                .formatted(REGISTRANT_EMAIL, COMPANY_DISPLAY)))
                .andExpect(status().isOk())
                .andReturn();
        String slug = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("user").path("companyId").asText();
        assertThat(companyRepository.findByName(slug)).map(Company::getCreatedBy).contains("anonymous");

        assertCompanyMatchesSpec(slug);
    }

    @Test
    @DisplayName("a company created with a JWT that has no custom:username (createdBy = subject) matches")
    void companyCreatedBySubjectOnlyToken_matchesSpec() throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .with(jwt().jwt(j -> j.subject(COGNITO_SUB).claim("custom:role", "ORGANIZER"))
                                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + SUB_NAME + "\"}"))
                .andExpect(status().isCreated());
        assertThat(companyRepository.findByName("patternsubco")).map(Company::getCreatedBy).contains(COGNITO_SUB);

        assertCompanyMatchesSpec("patternsubco");
    }

    @Test
    @WithMockUser(username = "organizer.alice", roles = "ORGANIZER")
    @DisplayName("a company POSTed with spaces in its name gets the slug as key and reads back valid")
    void companyNameWithSpaces_matchesSpec() throws Exception {
        mockMvc.perform(post("/api/v1/companies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"" + SPACED_NAME + "\"}"))
                .andExpect(status().isCreated());
        assertThat(companyRepository.findByName("patternprobe")).map(Company::getDisplayName).contains(SPACED_NAME);

        assertCompanyMatchesSpec("patternprobe");
    }

    private void assertCompanyMatchesSpec(String name) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/companies/{name}", name)
                        .with(jwt().jwt(j -> j.subject("pattern-reader").claim("custom:username", "organizer.alice"))
                                .authorities(List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("name").asText()).isEqualTo(name);

        assertResponseMatchesSpec(COMPANIES, result);
    }
}
