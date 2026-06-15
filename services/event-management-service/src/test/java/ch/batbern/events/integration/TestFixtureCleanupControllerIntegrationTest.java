package ch.batbern.events.integration;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Topic;
import ch.batbern.events.dto.TestFixtureCleanupRequest;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.notification.Notification;
import ch.batbern.events.notification.NotificationRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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

    private static final String ENDPOINT = "/api/v1/admin/test-fixtures/ems/cleanup";

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

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanState() {
        // Tests start from a known-empty slate so deletion counts are deterministic.
        // Cascade deletes remove sessions, event_tasks, speaker_pool, registrations etc.
        registrationRepository.deleteAll();
        eventRepository.deleteAll();
        topicRepository.deleteAll();
        // notifications carry no FK to events (ADR-003 soft string ref), so they are NOT
        // reached by eventRepository.deleteAll() — clear them explicitly.
        notificationRepository.deleteAll();
        // Clear non-default task templates so the task-template cleanup tests start from a
        // deterministic slate (only the migration-seeded defaults remain). event_tasks were
        // already cascade-cleared by eventRepository.deleteAll() above, so no FK blocks this.
        jdbcTemplate.update("DELETE FROM task_templates WHERE is_default = false");
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
        @DisplayName("events_by_number: returns 400 when threshold sentinel is not the bound \"10000\"")
        @WithMockUser(roles = {"ORGANIZER"})
        void returns400_whenEventsByNumberThresholdNotSentinel() throws Exception {
            // Production-safety guard: the threshold is locked to "10000". A lower value like
            // "100" or "56" — which would delete real events (BATbern56, …) — must be rejected.
            for (String bad : new String[] {"100", "56", "0", "9999", "1"}) {
                TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                        .entityType("events_by_number")
                        .prefix(bad)
                        .build();

                mockMvc.perform(post(ENDPOINT)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isBadRequest());
            }
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
        @DisplayName("events_by_number: force-deletes events with event_number >= 10000, leaves real events (< 10000)")
        @WithMockUser(roles = {"ORGANIZER"})
        void deletesTestEventsByNumber_preservesRealEvents() throws Exception {
            // Given: test events in the reserved range + real events well below it.
            // event_code is irrelevant here — these carry server-generated BATbern{N} codes,
            // exactly the events the BRUNO-TEST- prefix sweep can't reach.
            eventRepository.save(buildEvent("BATbern10000", 10000)); // boundary (inclusive)
            eventRepository.save(buildEvent("BATbern50000", 50000));
            eventRepository.save(buildEvent("BATbern99999", 99999));
            eventRepository.save(buildEvent("BATbern56", 56));        // real
            eventRepository.save(buildEvent("BATbern57", 57));        // real

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("events_by_number")
                    .prefix("10000")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.events").value(3))
                    .andExpect(jsonPath("$.entityType").value("events_by_number"))
                    .andExpect(jsonPath("$.prefix").value("10000"));

            assertThat(eventRepository.findByEventCode("BATbern10000")).isEmpty();
            assertThat(eventRepository.findByEventCode("BATbern50000")).isEmpty();
            assertThat(eventRepository.findByEventCode("BATbern99999")).isEmpty();
            assertThat(eventRepository.findByEventCode("BATbern56")).isPresent();
            assertThat(eventRepository.findByEventCode("BATbern57")).isPresent();
        }

        @Test
        @DisplayName("events_by_number: force-deletes an event that has a REAL attendee registration (bypasses the 409 guard) and cascades it")
        @WithMockUser(roles = {"ORGANIZER"})
        void forceDeletesEventWithRealRegistration_byNumber() throws Exception {
            // This is the exact leak scenario (2026-06-01): a registration-fixture event with a
            // genuine anonymous attendee. The normal DELETE /events/{code} returns 409
            // (EventController.countRealAttendees > 0), so the per-test teardown silently skips
            // it and it leaks forever. The events_by_number sweep uses a native repository delete
            // that NEVER invokes the guard, so it removes the event AND cascades the registration.
            Event leaked = buildEvent("BATbern99500", 99500);
            eventRepository.save(leaked);
            registrationRepository.save(buildRealRegistration(leaked.getId(), "real.attendee@e2e.batbern.invalid"));

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("events_by_number")
                    .prefix("10000")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.events").value(1));

            assertThat(eventRepository.findByEventCode("BATbern99500")).isEmpty();
            assertThat(registrationRepository.findAll()).isEmpty(); // cascade removed the registration
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

    // ---------- Notification cleanup ----------

    @Nested
    @DisplayName("Notification cleanup")
    class NotificationCleanup {

        @Test
        @DisplayName("returns 403 when caller is SPEAKER (not ORGANIZER) for notifications")
        @WithMockUser(roles = {"SPEAKER"})
        void returns403_whenSpeakerForNotifications() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("notifications")
                    .prefix("BRUNO-TEST-")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("returns 400 when notifications prefix is not the bound sentinel (e.g. 'BRUNO')")
        @WithMockUser(roles = {"ORGANIZER"})
        void returns400_whenNotificationsPrefixNotSentinel() throws Exception {
            // "BRUNO" doesn't match the anchored ^BRUNO-TEST-$ — server-side guard.
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("notifications")
                    .prefix("BRUNO")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deletes BRUNO-TEST- / reserved-number / bruno.test.* / subject/body-marker "
                + "notifications; real ones (incl. 11-digit pathological tail) survive")
        @WithMockUser(roles = {"ORGANIZER"})
        void deletesTestNotifications_preservesRealNotifications() throws Exception {
            // Five test notifications (one per discriminator) ...
            notificationRepository.save(buildNotification("real.organizer", "BRUNO-TEST-1779647142000"));
            notificationRepository.save(buildNotification("real.organizer", "BATbern10001"));
            notificationRepository.save(buildNotification("bruno.test.foo", null));
            // (i) NULL event_code + real-organizer recipient, but the run's BRUNO-TEST-X marker
            //     is stamped in the SUBJECT → must be deleted via the subject predicate.
            notificationRepository.save(buildNotification(
                    "real.organizer", null, "Reminder for BRUNO-TEST-X event", "Innocent body"));
            // (ii) marker only in the BODY → must be deleted via the body predicate.
            notificationRepository.save(buildNotification(
                    "real.organizer", null, "Innocent subject", "References BRUNO-TEST-X internally"));
            // ... plus THREE survivors:
            // - a real event_code below the reserved range,
            // - a real recipient with a NULL event_code and no marker (the exact in-app
            //   notification an organizer would receive),
            // - a PATHOLOGICAL 11-digit BATbern tail: matches the ^BATbern[0-9]+$ shape but
            //   overflows INTEGER. The {1,9} digit bound makes it fail the numeric branch
            //   (rather than raising a CAST overflow that would 500 the whole sweep), so the
            //   row survives AND the endpoint still returns 200.
            notificationRepository.save(buildNotification("real.organizer", "BATbern56"));
            notificationRepository.save(buildNotification("real.organizer", null));
            notificationRepository.save(buildNotification("real.organizer", "BATbern99999999999"));

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("notifications")
                    .prefix("BRUNO-TEST-")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.notifications").value(5))
                    .andExpect(jsonPath("$.entityType").value("notifications"))
                    .andExpect(jsonPath("$.prefix").value("BRUNO-TEST-"));

            // Three survivors remain — assert via COUNT (native delete bypassed the L1 cache).
            Integer survivorCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notifications", Integer.class);
            assertThat(survivorCount).isEqualTo(3);
            Integer realEventCode = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE event_code = 'BATbern56'", Integer.class);
            assertThat(realEventCode).isEqualTo(1);
            Integer realRecipientNullEvent = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notifications "
                            + "WHERE recipient_username = 'real.organizer' AND event_code IS NULL "
                            + "AND subject NOT LIKE '%BRUNO-TEST-%' AND body NOT LIKE '%BRUNO-TEST-%'",
                    Integer.class);
            assertThat(realRecipientNullEvent).isEqualTo(1);
            // The pathological 11-digit tail survived (numeric branch skipped, no SQL error).
            Integer pathological = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE event_code = 'BATbern99999999999'",
                    Integer.class);
            assertThat(pathological).isEqualTo(1);
        }

        @Test
        @DisplayName("does NOT delete the boundary event BATbern9999 (just below the reserved range)")
        @WithMockUser(roles = {"ORGANIZER"})
        void preservesEventNumberBelowThreshold() throws Exception {
            notificationRepository.save(buildNotification("real.organizer", "BATbern9999"));
            notificationRepository.save(buildNotification("real.organizer", "BATbern10000")); // deleted

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("notifications")
                    .prefix("BRUNO-TEST-")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.notifications").value(1));

            Integer remaining = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM notifications WHERE event_code = 'BATbern9999'", Integer.class);
            assertThat(remaining).isEqualTo(1);
        }

        @Test
        @DisplayName("is idempotent — re-running with nothing to delete returns 0 counts")
        @WithMockUser(roles = {"ORGANIZER"})
        void isIdempotent_whenNoNotificationsMatch() throws Exception {
            notificationRepository.save(buildNotification("real.organizer", "BATbern56"));

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("notifications")
                    .prefix("BRUNO-TEST-")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.notifications").value(0));

            assertThat(notificationRepository.count()).isEqualTo(1);
        }
    }

    // ---------- Task template cleanup ----------

    @Nested
    @DisplayName("Task template cleanup")
    class TaskTemplateCleanup {

        private void insertTemplate(UUID id, String name, boolean isDefault) {
            jdbcTemplate.update(
                    "INSERT INTO task_templates "
                            + "(id, name, trigger_state, due_date_type, due_date_offset_days, is_default) "
                            + "VALUES (?, ?, 'topic_selection', 'relative_to_event', -14, ?)",
                    id, name, isDefault);
        }

        private Integer countTemplatesNamed(String name) {
            return jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM task_templates WHERE name = ?", Integer.class, name);
        }

        @Test
        @DisplayName("returns 403 when caller is SPEAKER (not ORGANIZER) for task_templates")
        @WithMockUser(roles = {"SPEAKER"})
        void returns403_whenSpeakerForTaskTemplates() throws Exception {
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("task_templates")
                    .prefix("Test Custom Template")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("returns 400 when task_templates prefix is not the bound name (e.g. 'Test')")
        @WithMockUser(roles = {"ORGANIZER"})
        void returns400_whenPrefixNotBoundName() throws Exception {
            // "Test" doesn't match the anchored ^Test Custom Template$ — request bodies cannot
            // supply an arbitrary name that might match real custom templates.
            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("task_templates")
                    .prefix("Test")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deletes non-default 'Test Custom Template' rows; preserves default + real-named templates")
        @WithMockUser(roles = {"ORGANIZER"})
        void deletesTestTemplates_preservesDefaultAndReal() throws Exception {
            insertTemplate(UUID.randomUUID(), "Test Custom Template", false);
            insertTemplate(UUID.randomUUID(), "Test Custom Template", false);
            // A non-default custom template with a NON-seeded name (avoid colliding with the
            // migration-seeded "Venue Booking" default) — must survive the name-bound sweep.
            insertTemplate(UUID.randomUUID(), "Keep This Custom Template", false);
            insertTemplate(UUID.randomUUID(), "Test Custom Template", true);     // default — guard preserves it

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("task_templates")
                    .prefix("Test Custom Template")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.task_templates").value(2))
                    .andExpect(jsonPath("$.entityType").value("task_templates"))
                    .andExpect(jsonPath("$.prefix").value("Test Custom Template"));

            // The one default "Test Custom Template" survives (is_default guard); the non-seeded
            // custom template is untouched (name doesn't match the bound sweep name).
            assertThat(countTemplatesNamed("Test Custom Template")).isEqualTo(1);
            assertThat(countTemplatesNamed("Keep This Custom Template")).isEqualTo(1);
        }

        @Test
        @DisplayName("never deletes a template still referenced by an event_tasks FK (no sweep FK-fail)")
        @WithMockUser(roles = {"ORGANIZER"})
        void preservesTemplateReferencedByEventTask() throws Exception {
            // saveAndFlush so the event row is visible to the raw-JDBC event_tasks insert below
            // (a plain save() buffers in the persistence context and would FK-fail).
            Event ev = eventRepository.saveAndFlush(buildEvent("BATbern-tt-ref"));
            UUID referenced = UUID.randomUUID();
            insertTemplate(referenced, "Test Custom Template", false);
            jdbcTemplate.update(
                    "INSERT INTO event_tasks (id, event_id, template_id, task_name, trigger_state, status) "
                            + "VALUES (?, ?, ?, 'Referenced task', 'topic_selection', 'todo')",
                    UUID.randomUUID(), ev.getId(), referenced);

            TestFixtureCleanupRequest req = TestFixtureCleanupRequest.builder()
                    .entityType("task_templates")
                    .prefix("Test Custom Template")
                    .build();

            mockMvc.perform(post(ENDPOINT)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.deletionCounts.task_templates").value(0));

            // The referenced template survived (the NOT-IN guard skipped it — no FK violation).
            assertThat(countTemplatesNamed("Test Custom Template")).isEqualTo(1);
        }
    }

    // ---------- Test data builders ----------

    private Notification buildNotification(String recipientUsername, String eventCode) {
        return buildNotification(
                recipientUsername, eventCode,
                "Cleanup test notification", "Body for cleanup integration test");
    }

    /**
     * Build a notification with explicit subject/body — used by the subject/body-marker
     * widening tests where the BRUNO-TEST- sentinel lives in the rendered text rather than
     * in event_code or recipient_username.
     */
    private Notification buildNotification(
            String recipientUsername, String eventCode, String subject, String body) {
        return Notification.builder()
                .recipientUsername(recipientUsername)
                .eventCode(eventCode)
                .notificationType("EVENT_PUBLISHED")
                .channel("EMAIL")
                .priority("NORMAL")
                .subject(subject)
                .body(body)
                .status("PENDING")
                .build();
    }

    private Event buildEvent(String eventCode) {
        return buildEvent(eventCode, EVENT_NUMBER_SEQ.incrementAndGet());
    }

    /** Build an event with an EXPLICIT event_number — used by the events_by_number range tests. */
    private Event buildEvent(String eventCode, int eventNumber) {
        Instant now = Instant.now();
        return Event.builder()
                .eventCode(eventCode)
                .eventNumber(eventNumber)
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

    /**
     * A genuine (non-auto) attendee registration — no {@code autoRegisteredFrom} metadata, so
     * {@code countRealAttendees} would count it and the normal DELETE would 409. The cleanup
     * path ignores the guard entirely.
     */
    private Registration buildRealRegistration(UUID eventId, String email) {
        Instant now = Instant.now();
        return Registration.builder()
                .registrationCode("REG-" + UUID.randomUUID())
                .eventId(eventId)
                .attendeeUsername("real.attendee")
                .attendeeEmail(email)
                .status("registered")
                .registrationDate(now)
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
