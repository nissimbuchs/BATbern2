package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.PublishingConfig;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.entity.EventAgendaConfig;
import ch.batbern.events.repository.EventAgendaConfigRepository;
import ch.batbern.events.event.EventPublishedEvent;
import ch.batbern.events.event.SpeakersPhasePublishedEvent;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.PublishingConfigRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Publishing Engine endpoints
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing
 * Task 3a: RED Phase - TDD Tests for Progressive Publishing (AC14-29)
 *
 * Tests publishing phases:
 * - Phase 1 (Topic): Auto-publish on event creation
 * - Phase 2 (Speakers): Auto-publish 1 month before event
 * - Phase 3 (Agenda): Auto-publish 2 weeks before event
 *
 * Story 4.9b: the phase is the addressed instance ({@code POST …/phases/{phase}/publish}), status
 * and schedule are one resource ({@code GET/PATCH …/publishing}).
 *
 * Tests functionality:
 * - Manual publish/unpublish per phase
 * - Content validation before publishing
 * - CDN cache invalidation
 * - Preview mode
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@RecordApplicationEvents
public class PublishingEngineControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private EventAgendaConfigRepository agendaConfigRepository;

    @Autowired
    private PublishingConfigRepository publishingConfigRepository;

    @Autowired
    private ApplicationEvents applicationEvents;

    private Event testEvent;
    private String eventCode;
    private Instant eventDate;

    @BeforeEach
    void setUp() {
        eventDate = Instant.now().plus(60, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS); // Event in 60 days

        // Create test event
        testEvent = Event.builder()
                .eventCode("BATbern2025")
                .title("BATbern 2025 Full Day Conference")
                .eventNumber(2025)
                .date(eventDate)
                .registrationDeadline(eventDate.minus(7, ChronoUnit.DAYS))
                .venueName("Kursaal Bern")
                .venueAddress("Kornhausstrasse 3, 3013 Bern")
                .venueCapacity(300)
                .organizerUsername("test.organizer")
                .eventType(ch.batbern.events.core.dto.generated.EventType.FULL_DAY)
                .workflowState(ch.batbern.shared.types.EventWorkflowState.SLOT_ASSIGNMENT) // Ready for publishing
                .topicCode("cloud-architecture") // Required for topic validation
                // currentPublishedPhase will be added in GREEN phase (Task 3b)
                .build();

        testEvent = eventRepository.save(testEvent);
        eventCode = testEvent.getEventCode();

        // Create assigned sessions (simulating completed slot assignment)
        Session session1 = Session.builder()
                .eventId(testEvent.getId())
                .eventCode(testEvent.getEventCode())
                .sessionSlug("john-doe-techcorp")
                .title("Microservices Architecture Patterns")
                .sessionType("presentation")
                .startTime(eventDate.plus(2, ChronoUnit.HOURS))
                .endTime(eventDate.plus(2, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES))
                .room("Main Hall")
                .build();
        sessionRepository.save(session1);

        Session session2 = Session.builder()
                .eventId(testEvent.getId())
                .eventCode(testEvent.getEventCode())
                .sessionSlug("jane-smith-datainc")
                .title("Cloud Native Development")
                .sessionType("presentation")
                .startTime(eventDate.plus(3, ChronoUnit.HOURS))
                .endTime(eventDate.plus(3, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES))
                .room("Main Hall")
                .build();
        sessionRepository.save(session2);

        // Additional sessions to meet FULL_DAY minimum requirement (6 sessions total)
        Session session3 = Session.builder()
                .eventId(testEvent.getId())
                .eventCode(testEvent.getEventCode())
                .sessionSlug("speaker-three")
                .title("Container Orchestration with Kubernetes")
                .sessionType("presentation")
                .startTime(eventDate.plus(4, ChronoUnit.HOURS))
                .endTime(eventDate.plus(4, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES))
                .room("Room B")
                .build();
        sessionRepository.save(session3);

        Session session4 = Session.builder()
                .eventId(testEvent.getId())
                .eventCode(testEvent.getEventCode())
                .sessionSlug("speaker-four")
                .title("DevOps Best Practices")
                .sessionType("presentation")
                .startTime(eventDate.plus(5, ChronoUnit.HOURS))
                .endTime(eventDate.plus(5, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES))
                .room("Main Hall")
                .build();
        sessionRepository.save(session4);

        Session session5 = Session.builder()
                .eventId(testEvent.getId())
                .eventCode(testEvent.getEventCode())
                .sessionSlug("speaker-five")
                .title("Serverless Architectures")
                .sessionType("presentation")
                .startTime(eventDate.plus(6, ChronoUnit.HOURS))
                .endTime(eventDate.plus(6, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES))
                .room("Room A")
                .build();
        sessionRepository.save(session5);

        Session session6 = Session.builder()
                .eventId(testEvent.getId())
                .eventCode(testEvent.getEventCode())
                .sessionSlug("speaker-six")
                .title("Infrastructure as Code")
                .sessionType("presentation")
                .startTime(eventDate.plus(7, ChronoUnit.HOURS))
                .endTime(eventDate.plus(7, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES))
                .room("Room B")
                .build();
        sessionRepository.save(session6);

        // Create speakers (QUALITY_REVIEWED = content approved, ready for slot assignment)
        SpeakerPool speaker1 = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .speakerName("John Doe")
                .company("TechCorp")
                .status(ch.batbern.shared.types.SpeakerWorkflowState.QUALITY_REVIEWED)
                .sessionId(session1.getId())
                .build();
        speakerPoolRepository.save(speaker1);

        SpeakerPool speaker2 = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .speakerName("Jane Smith")
                .company("DataInc")
                .status(ch.batbern.shared.types.SpeakerWorkflowState.QUALITY_REVIEWED)
                .sessionId(session2.getId())
                .build();
        speakerPoolRepository.save(speaker2);
    }

    /**
     * Test: Publish Phase 1 (Topic) manually
     * AC14: Phase 1 - Topic: Publish event topic, date, venue immediately upon creation
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_publishTopic_when_phaseOneRequested() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/topic/publish", eventCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("topic"))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.publishedAt").exists())
                .andExpect(jsonPath("$.cdnInvalidated").value(true));

        // Verify event workflow state updated
        mockMvc.perform(get("/api/v1/events/{eventCode}", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPublishedPhase").value("TOPIC"));
    }

    /**
     * Test: Publish Phase 2 (Speakers) manually
     * AC15: Phase 2 - Speakers: Publish speaker lineup 1 month before event
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_publishSpeakers_when_phaseTwoRequested() throws Exception {
        // First publish topic
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/topic/publish", eventCode));

        // Then publish speakers
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/speakers/publish", eventCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("speakers"))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.publishedAt").exists())
                .andExpect(jsonPath("$.cdnInvalidated").value(true));

        // Verify event state updated
        mockMvc.perform(get("/api/v1/events/{eventCode}", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPublishedPhase").value("SPEAKERS"));
    }

    /**
     * Test: Publish Phase 3 (Final Agenda) manually
     * AC16: Phase 3 - Final Agenda: Publish complete agenda 2 weeks before event
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_publishAgenda_when_phaseThreeRequested() throws Exception {
        // Publish topic and speakers first
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/topic/publish", eventCode));
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/speakers/publish", eventCode));

        // Publish agenda
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/agenda/publish", eventCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("agenda"))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.publishedAt").exists())
                .andExpect(jsonPath("$.cdnInvalidated").value(true));

        // Verify workflow state transition
        mockMvc.perform(get("/api/v1/events/{eventCode}", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPublishedPhase").value("AGENDA"))
                .andExpect(jsonPath("$.workflowState").value("AGENDA_PUBLISHED"));
    }

    /**
     * Test: Unpublish a phase
     * AC18: Manual unpublish buttons per phase
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_unpublishPhase_when_unpublishRequested() throws Exception {
        // Publish speakers first
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/topic/publish", eventCode));
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/speakers/publish", eventCode));

        // Unpublish speakers
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/speakers/unpublish", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("speakers"))
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.unpublishedAt").exists())
                .andExpect(jsonPath("$.cdnInvalidated").value(true));

        // Verify phase reverted
        mockMvc.perform(get("/api/v1/events/{eventCode}", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPublishedPhase").value("TOPIC"));
    }

    /**
     * Test: Validate content before publishing
     * AC21: Validate required content before allowing publish per phase
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return422_when_publishingAgendaWithoutTiming() throws Exception {
        // Create session without timing
        Session untimedSession = Session.builder()
                .eventId(testEvent.getId())
                .eventCode(testEvent.getEventCode())
                .sessionSlug("untimed-session")
                .title("Session Without Timing")
                .sessionType("presentation")
                .startTime(null) // No timing assigned
                .endTime(null)
                .room(null)
                .build();
        sessionRepository.save(untimedSession);

        // Try to publish agenda (should fail validation)
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/agenda/publish", eventCode))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("All sessions must have timing assigned")))
                .andExpect(jsonPath("$.validationErrors").isArray())
                .andExpect(jsonPath("$.validationErrors[0].sessionSlug").value("untimed-session"));
    }

    /**
     * The schedule is a PATCH of the publishing resource (Story 4.9b): partial, stored, and GET
     * returns it. Without a stored row the defaults (30 / 14 days, both enabled) are returned, not a
     * 500 as the former GET /publish/schedule answered.
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_returnDefaultSchedule_when_noScheduleStored() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/publishing", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase2Enabled").value(true))
                .andExpect(jsonPath("$.phase2DaysBeforeEvent").value(30))
                .andExpect(jsonPath("$.phase2TriggerDate").value(
                        eventDate.minus(30, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC).toString()))
                .andExpect(jsonPath("$.phase3Enabled").value(true))
                .andExpect(jsonPath("$.phase3DaysBeforeEvent").value(14));
    }

    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_storeOnlyPresentScheduleFields_when_publishingPatched() throws Exception {
        mockMvc.perform(patch("/api/v1/events/{eventCode}/publishing", eventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phase2Enabled\": false, \"phase3DaysBeforeEvent\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase2Enabled").value(false))
                .andExpect(jsonPath("$.phase2DaysBeforeEvent").value(30))
                .andExpect(jsonPath("$.phase3Enabled").value(true))
                .andExpect(jsonPath("$.phase3DaysBeforeEvent").value(10))
                .andExpect(jsonPath("$.topic.isValid").value(true));

        PublishingConfig stored = publishingConfigRepository.findByEventId(testEvent.getId()).orElseThrow();
        assertThat(stored.getAutoPublishSpeakers()).isFalse();
        assertThat(stored.getAutoPublishSpeakersDaysBefore()).isEqualTo(30);
        assertThat(stored.getAutoPublishAgenda()).isTrue();
        assertThat(stored.getAutoPublishAgendaDaysBefore()).isEqualTo(10);

        // a second PATCH changes only its own field
        mockMvc.perform(patch("/api/v1/events/{eventCode}/publishing", eventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phase2Enabled\": true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/events/{eventCode}/publishing", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase2Enabled").value(true))
                .andExpect(jsonPath("$.phase3DaysBeforeEvent").value(10));
    }

    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return400_when_scheduleDaysBelowOne() throws Exception {
        mockMvc.perform(patch("/api/v1/events/{eventCode}/publishing", eventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phase2DaysBeforeEvent\": 0}"))
                .andExpect(status().isBadRequest());
        assertThat(publishingConfigRepository.findByEventId(testEvent.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "regular.user", roles = {"ATTENDEE"})
    void should_return403_when_nonOrganizerPatchesPublishing() throws Exception {
        mockMvc.perform(patch("/api/v1/events/{eventCode}/publishing", eventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phase2Enabled\": false}"))
                .andExpect(status().isForbidden());
    }

    /**
     * The phase is an enum instance: an unknown one is a bad request, and nothing changes.
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return400_when_phaseUnknown() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/foo/publish", eventCode))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/AGENDA/unpublish", eventCode))
                .andExpect(status().isBadRequest());
        assertThat(eventRepository.findById(testEvent.getId()).orElseThrow().getCurrentPublishedPhase()).isNull();
    }

    /**
     * Folded publishEvent (Story 4.9b): the agenda publish moves an event before AGENDA_PUBLISHED
     * there, sets publishedAt and fires EventPublishedEvent; the speakers publish fires
     * SpeakersPhasePublishedEvent.
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_moveWorkflowAndFireEventPublished_when_agendaPublishedFromSlotAssignment() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/speakers/publish", eventCode))
                .andExpect(status().isOk());
        assertThat(applicationEvents.stream(SpeakersPhasePublishedEvent.class)
                .map(SpeakersPhasePublishedEvent::eventCode)).containsExactly(eventCode);
        assertThat(applicationEvents.stream(EventPublishedEvent.class)).isEmpty();

        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/agenda/publish", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("agenda"));

        Event published = eventRepository.findById(testEvent.getId()).orElseThrow();
        assertThat(published.getWorkflowState()).isEqualTo(EventWorkflowState.AGENDA_PUBLISHED);
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(applicationEvents.stream(EventPublishedEvent.class))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getEventCode()).isEqualTo(eventCode);
                    assertThat(e.getPreviousStatus()).isEqualTo("SLOT_ASSIGNMENT");
                    assertThat(e.getPublishedAt()).isEqualTo(published.getPublishedAt());
                });
    }

    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_notFireEventPublishedAgain_when_agendaRepublishedAfterAgendaPublished() throws Exception {
        testEvent.setWorkflowState(EventWorkflowState.AGENDA_PUBLISHED);
        Instant firstPublishedAt = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MICROS);
        testEvent.setPublishedAt(firstPublishedAt);
        eventRepository.save(testEvent);

        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/agenda/publish", eventCode))
                .andExpect(status().isOk());

        Event republished = eventRepository.findById(testEvent.getId()).orElseThrow();
        assertThat(republished.getWorkflowState()).isEqualTo(EventWorkflowState.AGENDA_PUBLISHED);
        assertThat(republished.getPublishedAt()).isEqualTo(firstPublishedAt);
        assertThat(applicationEvents.stream(EventPublishedEvent.class)).isEmpty();
    }

    /**
     * The validation of the former publishEvent runs on the agenda publish, alongside the timing
     * rule, and every reason is listed. Nothing changes.
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return422WithEveryReason_when_agendaHasPastDateAndUntimedSession() throws Exception {
        // (a blank title cannot be stored: the entity's bean validation refuses it first)
        testEvent.setDate(Instant.now().minus(2, ChronoUnit.DAYS));
        eventRepository.save(testEvent);
        sessionRepository.save(Session.builder()
                .eventId(testEvent.getId())
                .eventCode(testEvent.getEventCode())
                .sessionSlug("untimed-session")
                .title("Session Without Timing")
                .sessionType("presentation")
                .build());

        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/agenda/publish", eventCode))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("Event date must be in the future")))
                .andExpect(jsonPath("$.message").value(containsString("All sessions must have timing assigned")))
                .andExpect(jsonPath("$.validationErrors[*].field",
                        containsInAnyOrder("date", "timing")));

        Event unchanged = eventRepository.findById(testEvent.getId()).orElseThrow();
        assertThat(unchanged.getCurrentPublishedPhase()).isNull();
        assertThat(unchanged.getWorkflowState()).isEqualTo(EventWorkflowState.SLOT_ASSIGNMENT);
        assertThat(unchanged.getPublishedAt()).isNull();
        assertThat(applicationEvents.stream(EventPublishedEvent.class)).isEmpty();
    }

    /**
     * Unpublishing the agenda sets the phase back to speakers; the workflow state stays.
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_keepWorkflowState_when_agendaUnpublished() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/agenda/publish", eventCode))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/agenda/unpublish", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("agenda"))
                .andExpect(jsonPath("$.published").value(false));

        Event event = eventRepository.findById(testEvent.getId()).orElseThrow();
        assertThat(event.getCurrentPublishedPhase()).isEqualTo("speakers");
        assertThat(event.getWorkflowState()).isEqualTo(EventWorkflowState.AGENDA_PUBLISHED);
    }

    /**
     * Test: Event not found returns 404
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return404_when_eventNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/events/INVALID/phases/topic/publish"))
                .andExpect(status().isNotFound());
    }

    /**
     * Test: Unauthorized access returns 401 (the production chain runs in tests since 17.0b3)
     */
    @Test
    void should_return401_when_notAuthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/topic/publish", eventCode))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test: Non-organizer access returns 403
     */
    @Test
    @WithMockUser(username = "regular.user", roles = {"ATTENDEE"})
    void should_return403_when_notOrganizer() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/topic/publish", eventCode))
                .andExpect(status().isForbidden());
    }

    // ========== Publishing Status Endpoint Tests (Task 7) ==========

    /**
     * Test: Get publishing status when no phase published
     * Validates that status endpoint returns correct validation data for unpublished event
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_returnPublishingStatus_when_noPhasePublished() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/publishing", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPhase").doesNotExist())
                .andExpect(jsonPath("$.publishedPhases").isEmpty())
                .andExpect(jsonPath("$.topic.isValid").value(true))
                .andExpect(jsonPath("$.speakers.isValid").value(true))
                .andExpect(jsonPath("$.sessions.isValid").value(true))
                .andExpect(jsonPath("$.sessions.assignedCount").value(6))
                .andExpect(jsonPath("$.sessions.totalCount").value(6))
                .andExpect(jsonPath("$.sessions.unassignedSessions").isEmpty());
    }

    /**
     * The minimum-sessions check must read the event's effective agenda config: a per-event
     * override (event_agenda_config) wins over the event-type template.
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_requireOverrideMinSlots_when_perEventAgendaOverrideExists() throws Exception {
        agendaConfigRepository.save(EventAgendaConfig.builder()
                .eventId(testEvent.getId())
                .minSlots(7)
                .maxSlots(9)
                .slotDuration(45)
                .defaultCapacity(300)
                .build());

        mockMvc.perform(get("/api/v1/events/{eventCode}/publishing", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speakers.isValid").value(false))
                .andExpect(jsonPath("$.speakers.errors[0]", containsString("At least 7 sessions")));
    }

    /**
     * Test: Get publishing status with topic phase published
     * Validates that status reflects phase 1 publication state
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_returnPublishingStatus_with_topicPhasePublished() throws Exception {
        // Publish topic phase first
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/topic/publish", eventCode))
                .andExpect(status().isOk());

        // Verify status reflects topic publication
        mockMvc.perform(get("/api/v1/events/{eventCode}/publishing", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPhase").value("topic"))
                .andExpect(jsonPath("$.publishedPhases").isArray())
                .andExpect(jsonPath("$.publishedPhases", hasSize(1)))
                .andExpect(jsonPath("$.publishedPhases[0]").value("topic"))
                .andExpect(jsonPath("$.topic.isValid").value(true))
                .andExpect(jsonPath("$.speakers.isValid").value(true))
                .andExpect(jsonPath("$.sessions.isValid").value(true));
    }

    /**
     * Test: Get publishing status with all phases published
     * Validates that status reflects complete publication state
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_returnPublishingStatus_with_allPhasesPublished() throws Exception {
        // Publish all three phases
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/topic/publish", eventCode))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/speakers/publish", eventCode))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/events/{eventCode}/phases/agenda/publish", eventCode))
                .andExpect(status().isOk());

        // Verify status reflects all publications
        mockMvc.perform(get("/api/v1/events/{eventCode}/publishing", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentPhase").value("agenda"))
                .andExpect(jsonPath("$.publishedPhases", hasSize(3)))
                .andExpect(jsonPath("$.publishedPhases[0]").value("topic"))
                .andExpect(jsonPath("$.publishedPhases[1]").value("speakers"))
                .andExpect(jsonPath("$.publishedPhases[2]").value("agenda"))
                .andExpect(jsonPath("$.topic.isValid").value(true))
                .andExpect(jsonPath("$.speakers.isValid").value(true))
                .andExpect(jsonPath("$.sessions.isValid").value(true));
    }

    /**
     * Test: Get publishing status with unassigned sessions
     * Validates that sessions validation fails when sessions lack timing
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_returnInvalidSessionStatus_when_sessionsLackTiming() throws Exception {
        // Create session without timing
        Session untimedSession = Session.builder()
                .eventId(testEvent.getId())
                .eventCode(testEvent.getEventCode())
                .sessionSlug("bob-jones-cloudco")
                .title("Kubernetes Best Practices")
                .sessionType("presentation")
                // No startTime or endTime - invalid for publishing
                .build();
        sessionRepository.save(untimedSession);

        // Verify status shows invalid sessions
        mockMvc.perform(get("/api/v1/events/{eventCode}/publishing", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions.isValid").value(false))
                .andExpect(jsonPath("$.sessions.assignedCount").value(6))
                .andExpect(jsonPath("$.sessions.totalCount").value(7))
                .andExpect(jsonPath("$.sessions.unassignedSessions", hasSize(1)))
                .andExpect(jsonPath("$.sessions.unassignedSessions[0].sessionSlug").value("bob-jones-cloudco"))
                .andExpect(jsonPath("$.sessions.unassignedSessions[0].title").value("Kubernetes Best Practices"))
                .andExpect(jsonPath("$.sessions.errors", hasSize(greaterThan(0))));
    }

    /**
     * Test: Status endpoint requires authentication
     * Note: the production chain returns 401 (Unauthorized) for unauthenticated requests
     */
    @Test
    void should_return401_when_statusRequestedWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/publishing", eventCode))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test: Status endpoint returns 404 for non-existent event
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return404_when_statusRequestedForNonExistentEvent() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/publishing", "NON-EXISTENT-CODE"))
                .andExpect(status().isNotFound());
    }
}
