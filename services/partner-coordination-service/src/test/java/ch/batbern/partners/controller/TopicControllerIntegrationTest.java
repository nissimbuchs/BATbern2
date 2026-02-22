package ch.batbern.partners.controller;

import ch.batbern.partners.client.CompanyServiceClient;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.domain.Partner;
import ch.batbern.partners.domain.PartnerContact;
import ch.batbern.partners.domain.PartnershipLevel;
import ch.batbern.partners.repository.PartnerContactRepository;
import ch.batbern.partners.repository.PartnerRepository;
import ch.batbern.partners.repository.TopicRepository;
import ch.batbern.partners.repository.TopicVoteRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Partner Topics API — Story 8.2.
 * Tests cover AC1–6 (list, vote-toggle, suggest, organizer-review, status-visibility, role-access).
 *
 * Uses AbstractIntegrationTest (PostgreSQL Testcontainer).
 * Written BEFORE implementation (TDD RED phase).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class TopicControllerIntegrationTest extends AbstractIntegrationTest {

    static final String BASE = "/api/v1/partners/topics";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    PartnerRepository partnerRepository;
    @Autowired
    PartnerContactRepository partnerContactRepository;
    @Autowired
    TopicRepository topicRepository;
    @Autowired
    TopicVoteRepository topicVoteRepository;
    @MockitoBean
    CompanyServiceClient companyServiceClient;

    Partner partnerAlpha;
    Partner partnerBeta;

    @BeforeEach
    void setUp() {
        topicVoteRepository.deleteAll();
        topicRepository.deleteAll();
        partnerContactRepository.deleteAll();
        partnerRepository.deleteAll();

        partnerAlpha = createPartnerWithContact("AlphaCo", "alice");
        partnerBeta  = createPartnerWithContact("BetaCo", "bob");
    }

    // ─── AC1: Topic list sorted by vote count descending ─────────────────────

    @Test
    @WithMockUser(username = "alice", roles = {"PARTNER"})
    void should_returnEmptyList_when_noTopicsExist() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "alice", roles = {"PARTNER"})
    void should_listTopicsSortedByVoteCountDesc() throws Exception {
        // Topic A has 0 votes, Topic B will get 1 vote
        suggestTopic("alice", "AlphaCo", "Topic A", null);
        String topicBId = suggestTopic("bob", "BetaCo", "Topic B", null);
        castVote("alice", "AlphaCo", topicBId);

        mockMvc.perform(get(BASE).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("alice").roles("PARTNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].title", is("Topic B")))   // 1 vote — first
                .andExpect(jsonPath("$[0].voteCount", is(1)))
                .andExpect(jsonPath("$[1].title", is("Topic A")))   // 0 votes — second
                .andExpect(jsonPath("$[1].voteCount", is(0)));
    }

    @Test
    @WithMockUser(username = "alice", roles = {"PARTNER"})
    void should_showTopicFields_whenListingTopics() throws Exception {
        suggestTopic("alice", "AlphaCo", "Microservices Patterns", "Deep dive into microservices");

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").exists())
                .andExpect(jsonPath("$[0].title", is("Microservices Patterns")))
                .andExpect(jsonPath("$[0].description", is("Deep dive into microservices")))
                .andExpect(jsonPath("$[0].suggestedByCompany", is("AlphaCo")))
                .andExpect(jsonPath("$[0].voteCount", is(0)))
                .andExpect(jsonPath("$[0].status", is("PROPOSED")))
                .andExpect(jsonPath("$[0].currentPartnerHasVoted", is(false)));
    }

    // ─── AC2: Vote toggle ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice", roles = {"PARTNER"})
    void should_markCurrentPartnerHasVoted_when_partnerHasVoted() throws Exception {
        String topicId = suggestTopic("bob", "BetaCo", "Cloud Native", null);
        castVote("alice", "AlphaCo", topicId);

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentPartnerHasVoted", is(true)))
                .andExpect(jsonPath("$[0].voteCount", is(1)));
    }

    @Test
    @WithMockUser(username = "alice", roles = {"PARTNER"})
    void should_castVote_and_increment_voteCount() throws Exception {
        String topicId = suggestTopic("bob", "BetaCo", "DevOps Practices", null);

        mockMvc.perform(post(BASE + "/" + topicId + "/vote"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].voteCount", is(1)))
                .andExpect(jsonPath("$[0].currentPartnerHasVoted", is(true)));
    }

    @Test
    @WithMockUser(username = "alice", roles = {"PARTNER"})
    void should_beIdempotent_when_votingTwice() throws Exception {
        String topicId = suggestTopic("bob", "BetaCo", "Test-Driven Development", null);

        mockMvc.perform(post(BASE + "/" + topicId + "/vote")).andExpect(status().isNoContent());
        mockMvc.perform(post(BASE + "/" + topicId + "/vote")).andExpect(status().isNoContent()); // idempotent

        mockMvc.perform(get(BASE))
                .andExpect(jsonPath("$[0].voteCount", is(1)));
    }

    @Test
    @WithMockUser(username = "alice", roles = {"PARTNER"})
    void should_removeVote_and_decrement_voteCount() throws Exception {
        String topicId = suggestTopic("bob", "BetaCo", "Domain-Driven Design", null);
        castVote("alice", "AlphaCo", topicId);

        mockMvc.perform(delete(BASE + "/" + topicId + "/vote"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(BASE))
                .andExpect(jsonPath("$[0].voteCount", is(0)))
                .andExpect(jsonPath("$[0].currentPartnerHasVoted", is(false)));
    }

    @Test
    @WithMockUser(username = "alice", roles = {"PARTNER"})
    void should_beIdempotent_when_removingVoteThatDoesNotExist() throws Exception {
        String topicId = suggestTopic("bob", "BetaCo", "Event Sourcing Patterns", null);

        mockMvc.perform(delete(BASE + "/" + topicId + "/vote"))
                .andExpect(status().isNoContent()); // no error even if vote doesn't exist
    }

    @Test
    @WithMockUser(username = "alice", roles = {"PARTNER"})
    void should_countVotesAccurately_when_multiplePartnersVote() throws Exception {
        String topicId = suggestTopic("bob", "BetaCo", "Software Architecture", null);
        castVote("alice", "AlphaCo", topicId);
        castVote("bob", "BetaCo", topicId);

        mockMvc.perform(get(BASE))
                .andExpect(jsonPath("$[0].voteCount", is(2)));
    }

    // ─── AC3: Suggest topic ───────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice", roles = {"PARTNER"})
    void should_createTopic_when_validTitleProvided() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("title", "Kotlin for Architects");

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Kotlin for Architects")))
                .andExpect(jsonPath("$.status", is("PROPOSED")))
                .andExpect(jsonPath("$.suggestedByCompany", is("AlphaCo")));
    }

    @Test
    @WithMockUser(username = "alice", roles = {"PARTNER"})
    void should_return400_when_titleTooShort() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("title", "Hi"); // < 5 chars

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "alice", roles = {"PARTNER"})
    void should_return400_when_titleMissing() throws Exception {
        Map<String, String> body = new HashMap<>();

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ─── AC4: Organizer review ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    void should_allowOrganizer_to_listTopics() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    void should_allowOrganizer_to_updateTopicStatus_to_SELECTED() throws Exception {
        String topicId = suggestTopic("alice", "AlphaCo", "API Design Patterns", null);

        Map<String, String> update = new HashMap<>();
        update.put("status", "SELECTED");
        update.put("plannedEvent", "BATbern58");

        mockMvc.perform(patch(BASE + "/" + topicId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("SELECTED")))
                .andExpect(jsonPath("$.plannedEvent", is("BATbern58")));
    }

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    void should_allowOrganizer_to_updateTopicStatus_to_DECLINED() throws Exception {
        String topicId = suggestTopic("alice", "AlphaCo", "Blockchain Technology", null);

        Map<String, String> update = new HashMap<>();
        update.put("status", "DECLINED");

        mockMvc.perform(patch(BASE + "/" + topicId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DECLINED")));
    }

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    void should_return404_when_updatingStatus_of_nonExistentTopic() throws Exception {
        Map<String, String> update = new HashMap<>();
        update.put("status", "SELECTED");

        mockMvc.perform(patch(BASE + "/" + UUID.randomUUID() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound());
    }

    // ─── AC5: Status visibility ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice", roles = {"PARTNER"})
    void should_showPlannedEvent_when_topicIsSelected() throws Exception {
        String topicId = suggestTopic("alice", "AlphaCo", "Service Mesh Patterns", null);
        organizerSelectTopic(topicId, "BATbern59");

        mockMvc.perform(get(BASE))
                .andExpect(jsonPath("$[0].status", is("SELECTED")))
                .andExpect(jsonPath("$[0].plannedEvent", is("BATbern59")));
    }

    // ─── AC6: Role-based access ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice", roles = {"PARTNER"})
    void should_return403_when_partnerTriesToUpdateStatus() throws Exception {
        String topicId = suggestTopic("alice", "AlphaCo", "Legacy System Migration", null);

        Map<String, String> update = new HashMap<>();
        update.put("status", "SELECTED");

        mockMvc.perform(patch(BASE + "/" + topicId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    void should_return403_when_organizerTriesToSuggestTopic() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("title", "Organizer Topic Attempt");

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "organizer", roles = {"ORGANIZER"})
    void should_return403_when_organizerTriesToVote() throws Exception {
        String topicId = suggestTopic("alice", "AlphaCo", "CQRS Architecture Pattern", null);

        mockMvc.perform(post(BASE + "/" + topicId + "/vote"))
                .andExpect(status().isForbidden());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Direct DB insertion — bypasses controller auth for test setup */
    private String suggestTopic(String username, String companyName, String title, String description)
            throws Exception {
        // Use the suggest endpoint via the partner user
        Map<String, String> body = new HashMap<>();
        body.put("title", title);
        if (description != null) {
            body.put("description", description);
        }

        MvcResult result = mockMvc.perform(post(BASE)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(username).roles("PARTNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asText();
    }

    /** Cast a vote as a specific partner user */
    private void castVote(String username, String companyName, String topicId) throws Exception {
        mockMvc.perform(post(BASE + "/" + topicId + "/vote")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(username).roles("PARTNER")))
                .andExpect(status().isNoContent());
    }

    /** Organizer selects a topic (used for AC5 test setup) */
    private void organizerSelectTopic(String topicId, String plannedEvent) throws Exception {
        Map<String, String> update = new HashMap<>();
        update.put("status", "SELECTED");
        update.put("plannedEvent", plannedEvent);

        mockMvc.perform(patch(BASE + "/" + topicId + "/status")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user("organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk());
    }

    private Partner createPartnerWithContact(String companyName, String username) {
        Partner partner = Partner.builder()
                .companyName(companyName)
                .partnershipLevel(PartnershipLevel.GOLD)
                .partnershipStartDate(LocalDate.now().minusYears(1))
                .partnershipEndDate(LocalDate.now().plusYears(1))
                .build();
        partner = partnerRepository.save(partner);

        PartnerContact contact = new PartnerContact();
        contact.setPartnerId(partner.getId());
        contact.setUsername(username);
        contact.setContactRole(ch.batbern.partners.domain.ContactRole.PRIMARY);
        contact.setPrimary(true);
        partnerContactRepository.save(contact);
        return partner;
    }
}
