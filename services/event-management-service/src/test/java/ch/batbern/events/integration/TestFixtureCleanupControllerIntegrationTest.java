package ch.batbern.events.integration;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.dto.TestFixtureCleanupRequest;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.TopicRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code TestFixtureCleanupController} on EMS.
 *
 * <p>Covers:
 * <ul>
 *   <li>Authorization: 403 (non-organizer roles), 200 (organizer)</li>
 *   <li>Prefix validation: invalid prefix rejected with 400 (defense against {@code prefix=BAT}
 *       which would otherwise match real events like {@code BATbern56})</li>
 *   <li>Cross-entity prefix rejection: events prefix supplied for topics entityType → 400</li>
 *   <li>SQL-injection-shaped prefixes → 400 (regex enforcement at service layer)</li>
 *   <li>Unknown entityType → 400</li>
 *   <li>Empty / missing prefix → 400</li>
 *   <li>Successful deletion: only the matching test rows deleted; real-shaped rows survive</li>
 * </ul>
 *
 * <p>The 401-unauthenticated case is NOT testable here because EMS's {@link TestSecurityConfig}
 * sets {@code .anyRequest().permitAll()} at the HTTP layer (production auth is at the API
 * Gateway layer per Story 1.2); method-level {@code @PreAuthorize} returns 403 even without
 * any authentication, so 401 only manifests in production. Mirrors the precedent set in
 * {@code SlotAssignmentControllerIntegrationTest}.
 *
 * <p>Defensive assertion: every successful-cleanup test seeds a "real-looking" row (e.g.,
 * an event {@code BATbern56} or topic {@code cloud-native-security}) and verifies it
 * survives. This is the production-safety guarantee that justifies running this endpoint
 * against the production account.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("TestFixtureCleanup REST API Integration Tests (EMS)")
class TestFixtureCleanupControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String ENDPOINT = "/api/v1/admin/test-fixtures/cleanup";

    /** Source of unique event_number values across the whole class (table has UNIQUE constraint). */
    private static final AtomicInteger EVENT_NUMBER_SEQ = new AtomicInteger(900_000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TopicRepository topicRepository;

    @BeforeEach
    void cleanState() {
        // Tests start from a known-empty slate so deletion counts are deterministic.
        // Cascade deletes remove sessions, event_tasks, speaker_pool, registrations etc.
        eventRepository.deleteAll();
        topicRepository.deleteAll();
    }

    // ---------- Authorization ----------

    @Nested
    @DisplayName("Authorization")
    class Authorization {

        @Test
        @DisplayName("returns 401 when caller is unauthenticated (HTTP-level disabled in test config)")
        @Disabled("HTTP-level auth disabled in TestSecurityConfig — method-level @PreAuthorize "
                + "returns 403 without auth. 401 only manifests behind the API Gateway in prod.")
        void returns401_whenUnauthenticated() throws Exception {
            // Documented expectation; not exercisable in test config — see class javadoc.
        }

        @Test
        @DisplayName("returns 403 when caller is ATTENDEE (not ORGANIZER)")
        @WithMockUser(roles = {"ATTENDEE"})
        void returns403_whenAttendee() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("events")
                    .prefix("BRUNO-TEST-")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("returns 403 when caller is SPEAKER (not ORGANIZER)")
        @WithMockUser(roles = {"SPEAKER"})
        void returns403_whenSpeaker() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("events")
                    .prefix("BRUNO-TEST-")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("returns 403 when caller is PARTNER (not ORGANIZER)")
        @WithMockUser(roles = {"PARTNER"})
        void returns403_whenPartner() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("events")
                    .prefix("BRUNO-TEST-")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }
    }

    // ---------- Input validation ----------

    @Nested
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("returns 400 when entityType is unknown")
        @WithMockUser(roles = {"ORGANIZER"})
        void returns400_whenUnknownEntityType() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("invoices")
                    .prefix("BRUNO-TEST-")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when prefix is empty")
        @WithMockUser(roles = {"ORGANIZER"})
        void returns400_whenPrefixEmpty() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("events")
                    .prefix("")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when prefix would match real events (e.g. BAT)")
        @WithMockUser(roles = {"ORGANIZER"})
        void returns400_whenPrefixWouldMatchRealEvents() throws Exception {
            // "BAT" would match BATbern56, BATbern57, ... — exactly the scenario the regex prevents
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("events")
                    .prefix("BAT")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when events prefix supplied for topics entityType")
        @WithMockUser(roles = {"ORGANIZER"})
        void returns400_whenCrossEntityPrefix() throws Exception {
            // BRUNO-TEST- is the events prefix; doesn't match topics regex (^bruno-test-topic-$)
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("topics")
                    .prefix("BRUNO-TEST-")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when prefix contains SQL-injection-shaped chars")
        @WithMockUser(roles = {"ORGANIZER"})
        void returns400_whenSqlInjectionShapedPrefix() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("events")
                    .prefix("BRUNO-TEST-'; DROP TABLE events; --")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("returns 400 when prefix contains wildcard chars")
        @WithMockUser(roles = {"ORGANIZER"})
        void returns400_whenWildcardPrefix() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("events")
                    .prefix("BRUNO-TEST-%")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ---------- Successful cleanup ----------

    @Nested
    @DisplayName("Successful cleanup")
    class SuccessfulCleanup {

        @Test
        @DisplayName("deletes BRUNO-TEST-* events but leaves real BATbern* events alone")
        @WithMockUser(roles = {"ORGANIZER"})
        void deletesTestEvents_preservesRealEvents() throws Exception {
            // Given: two test events + two real-shaped events
            eventRepository.save(buildEvent("BRUNO-TEST-1779647142000"));
            eventRepository.save(buildEvent("BRUNO-TEST-1779647142001"));
            eventRepository.save(buildEvent("BATbern56"));
            eventRepository.save(buildEvent("BATbern57"));

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("events")
                    .prefix("BRUNO-TEST-")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.events").value(2))
                    .andExpect(jsonPath("$.entityType").value("events"))
                    .andExpect(jsonPath("$.prefix").value("BRUNO-TEST-"));

            assertThat(eventRepository.findByEventCode("BRUNO-TEST-1779647142000")).isEmpty();
            assertThat(eventRepository.findByEventCode("BRUNO-TEST-1779647142001")).isEmpty();
            assertThat(eventRepository.findByEventCode("BATbern56")).isPresent();
            assertThat(eventRepository.findByEventCode("BATbern57")).isPresent();
        }

        @Test
        @DisplayName("deletes bruno-test-topic-* topics but leaves real-shaped topics alone")
        @WithMockUser(roles = {"ORGANIZER"})
        void deletesTestTopics_preservesRealTopics() throws Exception {
            // Given: test topics (canonical pattern) + real-shaped topics
            topicRepository.save(buildTopic("bruno-test-topic-1779647142000"));
            topicRepository.save(buildTopic("bruno-test-topic-1779647142001"));
            topicRepository.save(buildTopic("cloud-native-security"));
            topicRepository.save(buildTopic("kubernetes-at-scale"));

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("topics")
                    .prefix("bruno-test-topic-")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.topics").value(2));

            assertThat(topicRepository.findByTopicCode("bruno-test-topic-1779647142000")).isEmpty();
            assertThat(topicRepository.findByTopicCode("bruno-test-topic-1779647142001")).isEmpty();
            assertThat(topicRepository.findByTopicCode("cloud-native-security")).isPresent();
            assertThat(topicRepository.findByTopicCode("kubernetes-at-scale")).isPresent();
        }

        @Test
        @DisplayName("is idempotent — re-running with nothing to delete returns 0 counts")
        @WithMockUser(roles = {"ORGANIZER"})
        void isIdempotent_whenNothingMatches() throws Exception {
            eventRepository.save(buildEvent("BATbern56"));

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("events")
                    .prefix("BRUNO-TEST-")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.events").value(0));

            assertThat(eventRepository.findByEventCode("BATbern56")).isPresent();
        }

        @Test
        @DisplayName("accepts an event in any workflow state and force-deletes via native query")
        @WithMockUser(roles = {"ORGANIZER"})
        void deletesEventInNonInitialWorkflowState() throws Exception {
            // Given: a test event in AGENDA_PUBLISHED — service-layer guards would normally
            // block deletion. The native DELETE bypasses those guards.
            Event published = buildEvent("BRUNO-TEST-1779647142099");
            published.setWorkflowState(EventWorkflowState.AGENDA_PUBLISHED);
            eventRepository.save(published);

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("events")
                    .prefix("BRUNO-TEST-")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.events").value(1));

            assertThat(eventRepository.findByEventCode("BRUNO-TEST-1779647142099")).isEmpty();
        }
    }

    // ---------- Test data builders ----------

    private Event buildEvent(String eventCode) {
        Instant now = Instant.now();
        return Event.builder()
                .eventCode(eventCode)
                .eventNumber(EVENT_NUMBER_SEQ.incrementAndGet())
                .title("Cleanup Test Event " + eventCode)
                .date(now.plus(60, ChronoUnit.DAYS))
                .registrationDeadline(now.plus(50, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address 1, 3000 Bern")
                .venueCapacity(100)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.CREATED)
                .organizerUsername("test.cleanup.organizer")
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Topic buildTopic(String topicCode) {
        Topic topic = new Topic();
        topic.setTopicCode(topicCode);
        topic.setTitle("Cleanup Test Topic " + topicCode);
        topic.setCategory("technical");
        topic.setCreatedDate(LocalDateTime.now());
        return topic;
    }
}
