package ch.batbern.partners.security;

import ch.batbern.partners.config.CumsInternalUsers;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.domain.TopicStatus;
import ch.batbern.partners.domain.TopicSuggestion;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.partners.repository.TopicRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.ContractServer;
import ch.batbern.shared.test.security.RoleStubTables;
import ch.batbern.shared.test.security.TestTokens;
import ch.batbern.shared.utils.DateTimeUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Agentic interaction 0.2: an agent token is recorded as {@code agent:<run> for <username>} in a
 * who-acted column, while ownership and identity still read the username. Real agent tokens
 * ({@link TestTokens}) go through the production chain and the real decoder; CUMS and EMS answer at
 * the wire ({@link ContractServer}).
 *
 * <p>PCS owns no {@code user_profiles} or {@code role_assignments} (production shares one database
 * with CUMS), so this test creates both as stubs ({@link RoleStubTables}): {@code JwtRolesConverter}
 * resolves an agent's roles from them by its {@code sub}, as production does.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("PCS: agent actions are recorded as agent:<run> for <username>")
class AgentAttributionIntegrationTest extends AbstractIntegrationTest {

    private static final String ORGANIZER = "carla.agentattr";
    private static final String ORGANIZER_SUB = "b0000000-0000-4000-8000-0000000000b1";
    private static final String PARTNER = "anna.agentattr";
    private static final String PARTNER_SUB = "b0000000-0000-4000-8000-0000000000b2";
    private static final String COMPANY = "AgentAttrCo";
    private static final String OTHER_COMPANY = "OtherAttrCo";
    private static final String RUN = "run-pcs-1";
    private static final String EMS_INTERNAL = "docs/api/internal/ems-internal.openapi.yml";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    private CacheManager cacheManager;
    @Autowired
    private PartnerRepository partnerRepository;
    @Autowired
    private TopicRepository topicRepository;

    private ContractServer peers;

    @BeforeEach
    void setUp() {
        RoleStubTables.create(jdbc);
        cleanUp();
        RoleStubTables.grant(jdbc, ORGANIZER, ORGANIZER_SUB, "ORGANIZER");
        RoleStubTables.grant(jdbc, PARTNER, PARTNER_SUB, "PARTNER");
        partner(COMPANY);
        partner(OTHER_COMPANY);

        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        peers = ContractServer.bindTo(restTemplate, EMS_INTERNAL, CumsInternalUsers.CUMS_INTERNAL);
        peers.expect(HttpMethod.GET, "/internal/events/{eventCode}/schedule").times(ExpectedCount.manyTimes())
                .respondWith(HttpStatus.OK, """
                        {"eventCode": "BATbern57", "title": "BATbern Spring 2026", "date": "2026-05-14T16:00:00Z",
                         "venueName": "Bern Congress Centre", "venueAddress": "Bern", "eventType": "EVENING",
                         "startTime": "2026-05-14T16:00:00Z", "endTime": "2026-05-14T20:00:00Z"}""");
        CumsInternalUsers.answerOn(peers).put(PARTNER, PARTNER, COMPANY);
    }

    @AfterEach
    void cleanUp() {
        if (peers != null) {
            peers.close();
            peers = null;
        }
        jdbc.update("DELETE FROM partner_meetings WHERE created_by LIKE ?", "%" + ORGANIZER);
        jdbc.update("DELETE FROM topic_suggestions WHERE company_name IN (?, ?)", COMPANY, OTHER_COMPANY);
        jdbc.update("DELETE FROM partners WHERE company_name IN (?, ?)", COMPANY, OTHER_COMPANY);
        RoleStubTables.remove(jdbc, ORGANIZER_SUB, PARTNER_SUB);
    }

    private void partner(String companyName) {
        partnerRepository.save(Partner.builder()
                .companyName(companyName)
                .partnershipLevel(PartnershipLevel.GOLD)
                .partnershipStartDate(DateTimeUtils.todaySwiss().minusYears(1))
                .partnershipEndDate(DateTimeUtils.todaySwiss().plusYears(1))
                .build());
    }

    private static String bearer(String sub, String username) {
        return "Bearer " + TestTokens.agentToken(sub, username, RUN, "organizer");
    }

    private String meetingBody() throws Exception {
        return objectMapper.writeValueAsString(Map.of("eventCode", "BATbern57", "meetingType", "SPRING",
                "startTime", "12:00", "endTime", "14:00", "location", "Lunch Hall"));
    }

    private String createdByOfOnlyMeeting() {
        return jdbc.queryForObject("SELECT created_by FROM partner_meetings WHERE created_by LIKE ?", String.class,
                "%" + ORGANIZER);
    }

    @Test
    @DisplayName("an organizer's agent creates a partner meeting: created_by = agent:<run> for <username>")
    void should_recordAgentActor_when_agentCreatesMeeting() throws Exception {
        mockMvc.perform(post("/api/v1/partner-meetings")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ORGANIZER_SUB, ORGANIZER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(meetingBody()))
                .andExpect(status().isCreated());

        assertThat(createdByOfOnlyMeeting()).isEqualTo("agent:" + RUN + " for " + ORGANIZER);
    }

    @Test
    @DisplayName("positive control: the organizer signed in as a person is recorded by username")
    void should_recordUsername_when_personCreatesMeeting() throws Exception {
        mockMvc.perform(post("/api/v1/partner-meetings")
                        .with(user(ORGANIZER).roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(meetingBody()))
                .andExpect(status().isCreated());

        assertThat(createdByOfOnlyMeeting()).isEqualTo(ORGANIZER);
    }

    @Test
    @DisplayName("ownership reads the username: a partner's agent edits its company's topic, not another's")
    void should_passOwnership_when_partnerAgentEditsOwnCompanysTopic() throws Exception {
        String suggested = mockMvc.perform(post("/api/v1/partners/topics")
                        .header(HttpHeaders.AUTHORIZATION, bearer(PARTNER_SUB, PARTNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Agent-suggested topic\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String ownTopic = objectMapper.readTree(suggested).get("id").asText();
        // Identity, not audit: the suggester stays the person's username
        assertThat(jdbc.queryForObject("SELECT suggested_by FROM topic_suggestions WHERE id = ?", String.class,
                UUID.fromString(ownTopic))).isEqualTo(PARTNER);

        mockMvc.perform(patch("/api/v1/partners/topics/" + ownTopic)
                        .header(HttpHeaders.AUTHORIZATION, bearer(PARTNER_SUB, PARTNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Edited by the agent\"}"))
                .andExpect(status().isOk());

        TopicSuggestion other = topicRepository.saveAndFlush(TopicSuggestion.builder()
                .companyName(OTHER_COMPANY)
                .suggestedBy("bob.other")
                .title("Another company's topic")
                .status(TopicStatus.PROPOSED)
                .build());
        mockMvc.perform(patch("/api/v1/partners/topics/" + other.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(PARTNER_SUB, PARTNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Not mine\"}"))
                .andExpect(status().isForbidden());
    }
}
