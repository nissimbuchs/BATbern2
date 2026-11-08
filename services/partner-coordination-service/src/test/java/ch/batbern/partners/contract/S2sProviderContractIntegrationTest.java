package ch.batbern.partners.contract;

import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.domain.TopicSuggestion;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.partners.repository.TopicRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;

import static ch.batbern.shared.test.contract.ProviderContract.assertExamplesMatchSchema;
import static ch.batbern.shared.test.contract.ProviderContract.assertResponseMatchesSpec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider half of the S2S contract (Story 17.0b1): the two PCS operations EMS calls on the public
 * API ({@code PartnerApiClientImpl}) run through the real controller, exactly as EMS calls them,
 * and must match the spec EMS's contract test answers from. {@code include=company} makes PCS call
 * CUMS in turn, through its real client against the companies-api harness.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("PCS honours the S2S contract EMS is tested against")
class S2sProviderContractIntegrationTest extends AbstractIntegrationTest {

    private static final String PARTNERS = "docs/api/partners-api.openapi.yml";
    private static final String COMPANY = "contractco";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private PartnerRepository partnerRepository;
    @Autowired
    private TopicRepository topicRepository;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private CacheManager cacheManager;

    private ContractServer cums;

    @BeforeEach
    void seed() {
        cleanUp();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        partnerRepository.save(Partner.builder()
                .companyName(COMPANY).partnershipLevel(PartnershipLevel.GOLD)
                .partnershipStartDate(LocalDate.of(2020, 1, 1))
                .build());
        topicRepository.save(TopicSuggestion.builder()
                .companyName(COMPANY).suggestedBy("partner.contract")
                .title("Contract testing across services").createdAt(Instant.now())
                .build());
        cums = ContractServer.bindTo(restTemplate, "docs/api/companies-api.openapi.yml");
        // Every topic list looks the caller up in CUMS (/internal/users, no spec until 17.0b2).
        cums.expectInternalWithoutSpec(GET, "/internal/users/{username}").times(ExpectedCount.manyTimes())
                .respondWith(HttpStatus.OK, """
                        {"id": "organizer.alice", "email": "organizer.alice@example.com", "firstName": "Alice",
                         "lastName": "Organizer", "roles": ["ORGANIZER"], "active": true,
                         "createdAt": "2025-01-15T10:00:00Z", "updatedAt": "2025-01-15T10:00:00Z"}""");
    }

    @AfterEach
    void cleanUp() {
        if (cums != null) {
            cums.close();
        }
        topicRepository.findAll().stream().filter(t -> COMPANY.equals(t.getCompanyName()))
                .forEach(topicRepository::delete);
        partnerRepository.findByCompanyName(COMPANY).ifPresent(partnerRepository::delete);
    }

    @Test
    void should_haveValidExamples_forConsumedOperations() {
        assertExamplesMatchSchema(PARTNERS, "GET", "/api/v1/partners/topics");
        assertExamplesMatchSchema(PARTNERS, "GET", "/api/v1/partners");
    }

    @Test
    void topics_matchSpec() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/partners/topics").with(organizer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.suggestedByCompany == '" + COMPANY + "')]").exists())
                .andReturn();

        assertResponseMatchesSpec(PARTNERS, result);
    }

    @Test
    @DisplayName("GET /partners?include=company&size=200, as EMS sends it, returns every partner and matches the spec")
    void partnerListWithCompany_matchesSpec() throws Exception {
        cums.expect(GET, "/api/v1/companies/{name}").times(ExpectedCount.manyTimes())
                .andExpect(r -> assertThat(r.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).startsWith("Bearer "))
                .respondWithExample();

        MvcResult result = mockMvc.perform(get("/api/v1/partners")
                        .param("include", "company").param("size", "200").with(organizer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.companyName == '" + COMPANY + "')].company.displayName")
                        .value("Swiss IT Solutions AG"))
                .andReturn();

        assertResponseMatchesSpec(PARTNERS, result);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor organizer() {
        return jwt().jwt(j -> j.subject("organizer.alice").claim("custom:username", "organizer.alice"))
                .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"));
    }
}
