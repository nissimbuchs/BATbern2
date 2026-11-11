package ch.batbern.partners.contract;

import ch.batbern.partners.config.CumsInternalUsers;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.domain.TopicSuggestion;
import ch.batbern.partners.domain.MeetingType;
import ch.batbern.partners.domain.PartnerMeeting;
import ch.batbern.partners.repository.PartnerMeetingRepository;
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
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static ch.batbern.shared.test.contract.ProviderContract.assertExamplesMatchSchema;
import static ch.batbern.shared.test.contract.ProviderContract.assertResponseMatchesSpec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider half of the S2S contract (Story 17.0b1): the two PCS operations EMS calls on the public
 * API ({@code PartnerApiClientImpl}) run through the real controller, exactly as EMS calls them,
 * and must match the spec EMS's contract test answers from. {@code include=company} makes PCS call
 * CUMS in turn, through its real client, on {@code /internal/companies/{name}} (6b; the public
 * companies-api before) with the organizer's JWT forwarded; the caller lookup goes to CUMS
 * {@code /internal/users/{username}}. Both validated against cums-internal (17.0b2).
 *
 * <p>Story 17.0b2: the one PCS {@code /internal} operation, {@code POST /internal/partner-meetings/rsvps}
 * (EMS {@code PartnerMeetingRsvpClientImpl}), against {@code docs/api/internal/pcs-internal.openapi.yml}.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("PCS honours the S2S contract EMS is tested against")
class S2sProviderContractIntegrationTest extends AbstractIntegrationTest {

    private static final String PARTNERS = "docs/api/partners-api.openapi.yml";
    private static final String COMPANY = "contractco";
    private static final String PCS_INTERNAL = "docs/api/internal/pcs-internal.openapi.yml";
    private static final String RSVPS = "/internal/partner-meetings/rsvps";

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
    @Autowired
    private PartnerMeetingRepository meetingRepository;

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
        cums = ContractServer.bindTo(restTemplate, CumsInternalUsers.CUMS_INTERNAL);
        // Every topic list looks the caller up in CUMS, through the internal API (17.0b2: specified).
        CumsInternalUsers.answerOn(cums).put("organizer.alice", "organizer.alice", null);
    }

    @AfterEach
    void cleanUp() {
        if (cums != null) {
            cums.close();
        }
        topicRepository.findAll().stream().filter(t -> COMPANY.equals(t.getCompanyName()))
                .forEach(topicRepository::delete);
        partnerRepository.findByCompanyName(COMPANY).ifPresent(partnerRepository::delete);
        meetingRepository.findAll().stream().filter(m -> "contract-0b2".equals(m.getCreatedBy()))
                .forEach(meetingRepository::delete);
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
        cums.expect(GET, "/internal/companies/{name}").times(ExpectedCount.manyTimes())
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
        return jwt().jwt(j -> j.subject("organizer.alice").claim("custom:username", "organizer.alice")
                        .expiresAt(Instant.now().plusSeconds(3600)))
                .authorities(new SimpleGrantedAuthority("ROLE_ORGANIZER"));
    }

    // ------------------------------------------------------------------ /internal (Story 17.0b2)

    @Test
    void should_haveValidExamples_forInternalOperations() {
        assertExamplesMatchSchema(PCS_INTERNAL, "POST", RSVPS);
    }

    private MvcResult rsvp(String json, int expectedStatus) throws Exception {
        MvcResult result = mockMvc.perform(post(RSVPS).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expectedStatus)).andReturn();
        assertResponseMatchesSpec(PCS_INTERNAL, result);
        return result;
    }

    @Test
    @DisplayName("POST /internal/partner-meetings/rsvps: 200 recorded, 400 unknown partStat, 404 unknown meeting")
    void recordRsvp_matchesSpec() throws Exception {
        UUID meeting = meetingRepository.save(PartnerMeeting.builder()
                .eventCode("BATbern9017").meetingType(MeetingType.SPRING).meetingDate(LocalDate.of(2026, 5, 14))
                .startTime(LocalTime.of(12, 0)).endTime(LocalTime.of(14, 0)).location("Kursaal")
                .createdBy("contract-0b2").build()).getId();
        String body = """
                {"meetingId": "%s", "attendeeEmail": "Partner.Contact@Example.com", "partStat": "%s"}""";

        MvcResult recorded = rsvp(body.formatted(meeting, "accepted"), 200);
        // Unknown partStat: 400 without a body, a known deviation from the spec's 400 (see the
        // spec's description; Story 17.6), so status only. EMS's client discards any 4xx.
        mockMvc.perform(post(RSVPS).contentType(MediaType.APPLICATION_JSON).content(body.formatted(meeting, "MAYBE")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
        rsvp("{\"attendeeEmail\": \"partner.contact@example.com\", \"partStat\": \"ACCEPTED\"}", 400);
        rsvp(body.formatted(UUID.randomUUID(), "DECLINED"), 404);

        assertThat(recorded.getResponse().getContentAsString())
                .containsPattern("\"status\"\\s*:\\s*\"ACCEPTED\"").contains("partner.contact@example.com");
    }
}
