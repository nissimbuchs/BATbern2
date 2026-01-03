package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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
 * Tests functionality:
 * - Manual publish/unpublish per phase
 * - Content validation before publishing
 * - Version tracking and rollback
 * - CDN cache invalidation
 * - Preview mode
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class PublishingEngineControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    private Event testEvent;
    private String eventCode;
    private Instant eventDate;

    @BeforeEach
    void setUp() {
        eventDate = Instant.now().plus(60, ChronoUnit.DAYS); // Event in 60 days

        // Create test event
        testEvent = Event.builder()
                .eventCode("BAT2025-FULL-DAY")
                .title("BATbern 2025 Full Day Conference")
                .eventNumber(2025)
                .date(eventDate)
                .registrationDeadline(eventDate.minus(7, ChronoUnit.DAYS))
                .venueName("Kursaal Bern")
                .venueAddress("Kornhausstrasse 3, 3013 Bern")
                .venueCapacity(300)
                .organizerUsername("test.organizer")
                .eventType(ch.batbern.events.dto.generated.EventType.FULL_DAY)
                .workflowState(ch.batbern.shared.types.EventWorkflowState.SLOT_ASSIGNMENT) // Ready for publishing
                // currentPublishedPhase will be added in GREEN phase (Task 3b)
                .build();

        testEvent = eventRepository.save(testEvent);
        eventCode = testEvent.getEventCode();

        // Create assigned sessions (simulating completed slot assignment)
        Session session1 = Session.builder()
                .eventId(testEvent.getId())
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
                .sessionSlug("jane-smith-datainc")
                .title("Cloud Native Development")
                .sessionType("presentation")
                .startTime(eventDate.plus(3, ChronoUnit.HOURS))
                .endTime(eventDate.plus(3, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES))
                .room("Main Hall")
                .build();
        sessionRepository.save(session2);

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
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/topic", eventCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("topic"))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.publishedAt").exists())
                .andExpect(jsonPath("$.version").exists())
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
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/topic", eventCode));

        // Then publish speakers
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/speakers", eventCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("speakers"))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.publishedAt").exists())
                .andExpect(jsonPath("$.version").exists())
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
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/topic", eventCode));
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/speakers", eventCode));

        // Publish agenda
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/agenda", eventCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase").value("agenda"))
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.publishedAt").exists())
                .andExpect(jsonPath("$.version").exists())
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
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/topic", eventCode));
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/speakers", eventCode));

        // Unpublish speakers
        mockMvc.perform(post("/api/v1/events/{eventCode}/unpublish/speakers", eventCode))
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
                .sessionSlug("untimed-session")
                .title("Session Without Timing")
                .sessionType("presentation")
                .startTime(null) // No timing assigned
                .endTime(null)
                .room(null)
                .build();
        sessionRepository.save(untimedSession);

        // Try to publish agenda (should fail validation)
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/agenda", eventCode))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(containsString("All sessions must have timing assigned")))
                .andExpect(jsonPath("$.validationErrors").isArray())
                .andExpect(jsonPath("$.validationErrors[0].sessionSlug").value("untimed-session"));
    }

    /**
     * Test: Get publishing preview
     * AC20: Preview mode to see public appearance before publishing
     * AC29: Preview shows correct content based on publishing mode
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_getPreview_when_previewModeRequested() throws Exception {
        // Publish topic and speakers
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/topic", eventCode));
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/speakers", eventCode));

        // Get preview
        mockMvc.perform(get("/api/v1/events/{eventCode}/publish/preview", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventCode").value(eventCode))
                .andExpect(jsonPath("$.currentPhase").value("SPEAKERS"))
                .andExpect(jsonPath("$.topicPublished").value(true))
                .andExpect(jsonPath("$.speakersPublished").value(true))
                .andExpect(jsonPath("$.agendaPublished").value(false))
                .andExpect(jsonPath("$.speakers").isArray())
                .andExpect(jsonPath("$.speakers", hasSize(2)))
                .andExpect(jsonPath("$.sessions").isEmpty()); // Sessions not published yet
    }

    /**
     * Test: Track publishing versions
     * AC26: Track all publishing versions with timestamp
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_trackVersion_when_contentPublished() throws Exception {
        // Publish topic
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/topic", eventCode))
                .andExpect(jsonPath("$.version").value(1));

        // Update and re-publish
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/topic", eventCode))
                .andExpect(jsonPath("$.version").value(2));

        // Get version history
        mockMvc.perform(get("/api/v1/events/{eventCode}/publish/versions", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versions").isArray())
                .andExpect(jsonPath("$.versions", hasSize(2)))
                .andExpect(jsonPath("$.versions[0].versionNumber").value(2))
                .andExpect(jsonPath("$.versions[0].phase").value("topic"))
                .andExpect(jsonPath("$.versions[0].publishedAt").exists());
    }

    /**
     * Test: Rollback to previous version
     * AC27: Rollback to previous version capability
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_rollbackVersion_when_rollbackRequested() throws Exception {
        // Publish and update multiple times
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/topic", eventCode));

        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/topic", eventCode));

        // Rollback to version 1
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/rollback/1", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rolledBack").value(true))
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andExpect(jsonPath("$.cdnInvalidated").value(true));
    }

    /**
     * Test: Get change log after publish
     * AC28: Change log for all post-publish updates
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    @org.junit.jupiter.api.Disabled("TODO: Implement change log tracking - needs diff logic between versions")
    void should_getChangeLog_when_updatesOccurAfterPublish() throws Exception {
        // Publish topic
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/topic", eventCode));

        // Update event details
        String updateRequest = """
                {
                  "eventName": "BATbern 2025 Full Day Conference - Updated"
                }
                """;
        mockMvc.perform(patch("/api/v1/events/{eventCode}", eventCode)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateRequest));

        // Re-publish to create new version
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/topic", eventCode));

        // Get change log
        mockMvc.perform(get("/api/v1/events/{eventCode}/publish/changelog", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes").isArray())
                .andExpect(jsonPath("$.changes", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.changes[0].field").exists())
                .andExpect(jsonPath("$.changes[0].oldValue").exists())
                .andExpect(jsonPath("$.changes[0].newValue").exists())
                .andExpect(jsonPath("$.changes[0].changedAt").exists());
    }

    /**
     * Test: Configure auto-publish schedule
     * AC19: Auto-publish scheduling: Phase 2 at 1 month, Phase 3 at 2 weeks before event
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_configureAutoPublish_when_scheduleSet() throws Exception {
        String scheduleRequest = """
                {
                  "phase2Enabled": true,
                  "phase2DaysBeforeEvent": 30,
                  "phase3Enabled": true,
                  "phase3DaysBeforeEvent": 14
                }
                """;

        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/schedule", eventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(scheduleRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheduled").value(true))
                .andExpect(jsonPath("$.phase2TriggerDate").exists())
                .andExpect(jsonPath("$.phase3TriggerDate").exists());

        // Verify schedule was saved
        mockMvc.perform(get("/api/v1/events/{eventCode}/publish/schedule", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phase2Enabled").value(true))
                .andExpect(jsonPath("$.phase2DaysBeforeEvent").value(30));
    }

    /**
     * Test: Event not found returns 404
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return404_when_eventNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/events/INVALID/publish/topic"))
                .andExpect(status().isNotFound());
    }

    /**
     * Test: Unauthorized access returns 401
     * NOTE: Disabled in test mode because TestSecurityConfig uses .permitAll() at HTTP level
     * to allow testing method-level @PreAuthorize security. In production, SecurityConfig
     * requires authentication at HTTP level.
     */
    @Test
    @org.junit.jupiter.api.Disabled("HTTP-level auth disabled in TestSecurityConfig - method-level @PreAuthorize tested separately")
    void should_return401_when_notAuthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/topic", eventCode))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test: Non-organizer access returns 403
     */
    @Test
    @WithMockUser(username = "regular.user", roles = {"ATTENDEE"})
    void should_return403_when_notOrganizer() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/publish/topic", eventCode))
                .andExpect(status().isForbidden());
    }
}
