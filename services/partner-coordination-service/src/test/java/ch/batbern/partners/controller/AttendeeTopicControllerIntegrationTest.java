package ch.batbern.partners.controller;

import ch.batbern.partners.client.CompanyServiceClient;
import ch.batbern.partners.client.UserServiceClient;
import ch.batbern.partners.config.TestAwsConfig;
import ch.batbern.partners.config.TestSecurityConfig;
import ch.batbern.partners.repository.TopicRepository;
import ch.batbern.partners.repository.TopicVoteRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the Attendee "Topics From the Floor" API — Story 7.1.
 *
 * <p>Verifies that a logged-in ATTENDEE can submit a topic into the existing topic pool tagged
 * {@code source=COMMUNITY}, that it surfaces in the organizer list, that validation returns 400,
 * that the role gate holds, and that partner-sourced topics keep {@code source=PARTNER}.
 *
 * <p>Uses {@link AbstractIntegrationTest} (PostgreSQL Testcontainer). {@code @Transactional} rolls
 * back each test; {@code @BeforeEach} also clears topic tables for isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class AttendeeTopicControllerIntegrationTest extends AbstractIntegrationTest {

    static final String ATTENDEE_BASE = "/api/v1/attendees/topics";
    static final String PARTNER_BASE = "/api/v1/partners/topics";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    TopicRepository topicRepository;
    @Autowired
    TopicVoteRepository topicVoteRepository;
    @MockitoBean
    CompanyServiceClient companyServiceClient;
    @MockitoBean
    UserServiceClient userServiceClient;

    @BeforeEach
    void setUp() {
        topicVoteRepository.deleteAll();
        topicRepository.deleteAll();
    }

    // ─── AC1/AC2: attendee submits a community topic ──────────────────────────

    @Test
    @WithMockUser(username = "carol", roles = {"ATTENDEE"})
    void should_createCommunityTopic_when_attendeeSubmitsValidTopic() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("title", "Event-Driven Architecture in Practice");
        body.put("description", "Lessons learned from a real migration");

        mockMvc.perform(post(ATTENDEE_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Event-Driven Architecture in Practice")))
                .andExpect(jsonPath("$.description", is("Lessons learned from a real migration")))
                .andExpect(jsonPath("$.status", is("PROPOSED")))
                .andExpect(jsonPath("$.source", is("COMMUNITY")))
                // Community suggestions have no partner company.
                .andExpect(jsonPath("$.suggestedByCompany", is(nullValue())));
    }

    @Test
    @WithMockUser(username = "carol", roles = {"ATTENDEE"})
    void should_ignoreCompanyName_when_attendeeSubmits() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("title", "Platform Engineering for Small Teams");
        body.put("companyName", "SpoofedCo"); // must be ignored — community has no company

        mockMvc.perform(post(ATTENDEE_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source", is("COMMUNITY")))
                .andExpect(jsonPath("$.suggestedByCompany", is(nullValue())));
    }

    // ─── AC3: community topic surfaces in the organizer list, source-tagged ───

    @Test
    void should_surfaceCommunityTopic_inOrganizerList_taggedCommunity() throws Exception {
        // Attendee submits
        Map<String, String> body = new HashMap<>();
        body.put("title", "Observability Beyond Dashboards");
        mockMvc.perform(post(ATTENDEE_BASE)
                        .with(user("carol").roles("ATTENDEE"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        // Organizer sees it in the combined list, tagged COMMUNITY
        mockMvc.perform(get(PARTNER_BASE).with(user("organizer").roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title", is("Observability Beyond Dashboards")))
                .andExpect(jsonPath("$[0].source", is("COMMUNITY")))
                .andExpect(jsonPath("$[0].suggestedByCompany", is(nullValue())));
    }

    // ─── AC4: validation ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "carol", roles = {"ATTENDEE"})
    void should_return400_when_titleTooShort() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("title", "Hi"); // < 5 chars

        mockMvc.perform(post(ATTENDEE_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "carol", roles = {"ATTENDEE"})
    void should_return400_when_titleMissing() throws Exception {
        mockMvc.perform(post(ATTENDEE_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HashMap<>())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "carol", roles = {"ATTENDEE"})
    void should_return400_when_titleTooLong() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("title", "a".repeat(256)); // > 255 chars

        mockMvc.perform(post(ATTENDEE_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "carol", roles = {"ATTENDEE"})
    void should_return400_when_descriptionTooLong() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("title", "A perfectly valid topic title");
        body.put("description", "d".repeat(501)); // > 500 chars

        mockMvc.perform(post(ATTENDEE_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    // ─── AC2: anonymous callers cannot submit ─────────────────────────────────

    @Test
    void should_rejectAnonymous_when_noAuthentication() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("title", "Anonymous trying to suggest a topic");

        // No @WithMockUser / no .with(user(...)) → anonymous principal.
        // In production the request is rejected with 401: the api-gateway chain
        // (anyRequest().authenticated()) and the service prod chain both sit behind a
        // bearer-token entry point. This @Import(TestSecurityConfig) harness uses the
        // permitAll testFilterChain (no entry point), so @PreAuthorize denial surfaces
        // as 403 here rather than 401 — either way it is rejected as a 4xx and, crucially,
        // no topic row is created.
        mockMvc.perform(post(ATTENDEE_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is4xxClientError());

        org.junit.jupiter.api.Assertions.assertEquals(0, topicRepository.count(),
                "anonymous submission must not create a topic row");
    }

    // ─── AC: role gate — only ATTENDEE may submit here ────────────────────────

    @Test
    @WithMockUser(username = "alice", roles = {"PARTNER"})
    void should_return403_when_nonAttendeePostsToAttendeeEndpoint() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("title", "Partner trying the attendee endpoint");

        mockMvc.perform(post(ATTENDEE_BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ─── Regression: partner-sourced topics keep source=PARTNER ───────────────

    @Test
    void should_keepPartnerSource_when_partnerSubmits() throws Exception {
        Map<String, String> body = new HashMap<>();
        body.put("title", "Partner-sourced topic stays PARTNER");

        // Partner submits via the existing partner endpoint
        org.mockito.Mockito.when(userServiceClient.getUserByUsername("alice"))
                .thenReturn(makeUser("alice", "AlphaCo"));

        mockMvc.perform(post(PARTNER_BASE)
                        .with(user("alice").roles("PARTNER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source", is("PARTNER")))
                .andExpect(jsonPath("$.suggestedByCompany", is("AlphaCo")));
    }

    private ch.batbern.partners.client.user.dto.UserResponse makeUser(String username, String companyId) {
        ch.batbern.partners.client.user.dto.UserResponse user =
                new ch.batbern.partners.client.user.dto.UserResponse();
        user.setId(username);
        user.setCompanyId(companyId);
        return user;
    }
}
