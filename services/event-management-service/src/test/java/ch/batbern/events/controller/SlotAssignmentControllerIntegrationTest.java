package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.oneOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Slot Assignment endpoints
 * Story BAT-11 (5.7): Slot Assignment & Progressive Publishing - Task 2a
 *
 * IMPORTANT: These are RED PHASE tests (TDD). They will FAIL until
 * SlotAssignmentController and supporting services are implemented.
 *
 * Tests cover:
 * - GET /api/v1/events/{eventCode}/sessions/unassigned - Get sessions without timing (AC5, AC12)
 * - PATCH /api/v1/events/{eventCode}/sessions/{sessionSlug}/timing - Assign timing (AC5-AC9)
 * - POST /api/v1/events/{eventCode}/sessions/bulk-timing - Bulk assign (AC13)
 * - GET /api/v1/events/{eventCode}/sessions/conflicts - Detect conflicts (AC9)
 *
 * Requirements:
 * - Migration V28 applied (session_timing_history table)
 * - SessionTimingService implementation
 * - ConflictDetectionService implementation
 * - AbstractIntegrationTest provides PostgreSQL via Testcontainers
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
public class SlotAssignmentControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @MockitoBean
    private UserApiClient userApiClient;

    @Autowired
    private ObjectMapper objectMapper;

    private Event testEvent;
    private Session placeholderSession1;
    private Session placeholderSession2;
    private Session assignedSession;
    private SpeakerPool speaker1;
    private SpeakerPool speaker2;
    private String eventCode;
    private Instant eventDate;

    @BeforeEach
    void setUp() {
        // Clean database
        sessionRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        eventRepository.deleteAll();

        // Create test event in SLOT_ASSIGNMENT workflow state
        eventCode = "BATbern997";
        eventDate = Instant.now().plus(30, ChronoUnit.DAYS);
        testEvent = Event.builder()
                .eventCode(eventCode)
                .eventNumber(997)
                .title("Slot Assignment Test Event")
                .description("Testing slot assignment workflows")
                .date(eventDate)
                .registrationDeadline(Instant.now().plus(15, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .organizerUsername("test.organizer")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.SLOT_ASSIGNMENT) // Ready for slot assignment
                .build();
        testEvent = eventRepository.save(testEvent);

        // Mock user API responses (simplified for slot assignment testing)
        // UserResponse mocking temporarily disabled - focus on slot assignment logic

        // Create speakers in pool with ACCEPTED status
        speaker1 = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .username("john.doe")
                .speakerName("John Doe")
                .status(SpeakerWorkflowState.ACCEPTED) // Confirmed speaker
                .build();
        speaker1 = speakerPoolRepository.save(speaker1);

        speaker2 = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .username("jane.smith")
                .speakerName("Jane Smith")
                .status(SpeakerWorkflowState.ACCEPTED)
                .build();
        speaker2 = speakerPoolRepository.save(speaker2);

        // Create placeholder sessions (no timing assigned yet)
        // These are created when speaker accepts invitation
        placeholderSession1 = Session.builder()
                .eventId(testEvent.getId())
                .eventCode(testEvent.getEventCode())
                .sessionSlug("john-doe-techcorp")
                .title("John Doe - TechCorp")
                .description("Cloud Architecture Patterns")
                .sessionType("presentation")
                // startTime and endTime are NULL (placeholder session)
                .speakerPoolId(speaker1.getId()) // Link to speaker via speaker_pool
                .build();
        placeholderSession1 = sessionRepository.save(placeholderSession1);

        placeholderSession2 = Session.builder()
                .eventId(testEvent.getId())
                .eventCode(testEvent.getEventCode())
                .sessionSlug("jane-smith-datainc")
                .title("Jane Smith - DataInc")
                .description("Machine Learning in Practice")
                .sessionType("presentation")
                // startTime and endTime are NULL (placeholder session)
                .speakerPoolId(speaker2.getId())
                .build();
        placeholderSession2 = sessionRepository.save(placeholderSession2);

        // Create one session with timing already assigned (for conflict testing)
        assignedSession = Session.builder()
                .eventId(testEvent.getId())
                .eventCode(testEvent.getEventCode())
                .sessionSlug("existing-session")
                .title("Existing Session")
                .description("Already scheduled")
                .sessionType("presentation")
                .startTime(eventDate.plus(2, ChronoUnit.HOURS)) // 18:00
                .endTime(eventDate.plus(2, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES)) // 18:45
                .room("Main Hall")
                .build();
        assignedSession = sessionRepository.save(assignedSession);
    }

    /**
     * Test: Get unassigned sessions (placeholder sessions without timing)
     * AC5, AC12: Show unassigned speakers list with real-time updates
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_getUnassignedSessions_when_placeholderSessionsExist() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/sessions/unassigned", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))) // 2 placeholder sessions
                .andExpect(jsonPath("$[*].sessionSlug", containsInAnyOrder(
                        "john-doe-techcorp",
                        "jane-smith-datainc"
                )))
                .andExpect(jsonPath("$[0].startTime").doesNotExist()) // NULL
                .andExpect(jsonPath("$[0].endTime").doesNotExist())   // NULL
                .andExpect(jsonPath("$[0].room").doesNotExist());     // NULL
    }

    /**
     * Test: Assign timing to a placeholder session (drag-and-drop slot assignment)
     * AC5-AC6: Drag-and-drop UI to assign sessions, visual timeline
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_assignTiming_when_validTimingProvided() throws Exception {
        String timingRequest = """
                {
                  "startTime": "%s",
                  "endTime": "%s",
                  "room": "Main Hall",
                  "sessionType": "PRESENTATION",
                  "changeReason": "drag_drop_reassignment",
                  "notes": "Organizer drag-and-drop assignment"
                }
                """.formatted(
                eventDate.plus(3, ChronoUnit.HOURS).toString(), // 19:00
                eventDate.plus(3, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES).toString() // 19:45
        );

        mockMvc.perform(patch("/api/v1/events/{eventCode}/sessions/{sessionSlug}/timing",
                        eventCode, "john-doe-techcorp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timingRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionSlug").value("john-doe-techcorp"))
                .andExpect(jsonPath("$.startTime").exists())
                .andExpect(jsonPath("$.endTime").exists())
                .andExpect(jsonPath("$.room").value("Main Hall"));

        // Verify session no longer appears in unassigned list
        mockMvc.perform(get("/api/v1/events/{eventCode}/sessions/unassigned", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))) // Only 1 unassigned now
                .andExpect(jsonPath("$[0].sessionSlug").value("jane-smith-datainc"));
    }

    /**
     * Test: Detect room overlap conflict
     * AC9: Warn if speaker has conflicting commitment at same time
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return409Conflict_when_roomOverlapDetected() throws Exception {
        // Try to assign to same time slot as existing session (18:00-18:45, Main Hall)
        String conflictingTimingRequest = """
                {
                  "startTime": "%s",
                  "endTime": "%s",
                  "room": "Main Hall",
                  "changeReason": "drag_drop_reassignment"
                }
                """.formatted(
                eventDate.plus(2, ChronoUnit.HOURS).plus(15, ChronoUnit.MINUTES).toString(), // 18:15 (overlaps with 18:00-18:45)
                eventDate.plus(2, ChronoUnit.HOURS).plus(60, ChronoUnit.MINUTES).toString()  // 19:00
        );

        mockMvc.perform(patch("/api/v1/events/{eventCode}/sessions/{sessionSlug}/timing",
                        eventCode, "john-doe-techcorp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictingTimingRequest))
                .andExpect(status().isConflict()) // 409
                .andExpect(jsonPath("$.error").value("TIMING_CONFLICT"))
                .andExpect(jsonPath("$.message").value(containsString("conflicts with existing schedule")))
                .andExpect(jsonPath("$.conflicts").isArray())
                .andExpect(jsonPath("$.conflicts[0].type").value("room_overlap"))
                .andExpect(jsonPath("$.conflicts[0].conflictingSessionSlug").value("existing-session"));
    }

    /**
     * Test: Detect speaker double-booking conflict
     * AC9: Same speaker cannot be in two sessions at overlapping times
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return409Conflict_when_speakerDoubleBookingDetected() throws Exception {
        // First, assign john.doe to 18:00-18:45
        String firstAssignment = """
                {
                  "startTime": "%s",
                  "endTime": "%s",
                  "room": "Room A",
                  "changeReason": "drag_drop_reassignment"
                }
                """.formatted(
                eventDate.plus(2, ChronoUnit.HOURS).toString(), // 18:00
                eventDate.plus(2, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES).toString() // 18:45
        );

        mockMvc.perform(patch("/api/v1/events/{eventCode}/sessions/{sessionSlug}/timing",
                        eventCode, "john-doe-techcorp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstAssignment))
                .andExpect(status().isOk());

        // Create another session for the same speaker
        Session duplicateSpeakerSession = Session.builder()
                .eventId(testEvent.getId())
                .eventCode(testEvent.getEventCode())
                .sessionSlug("john-doe-second-talk")
                .title("John Doe - Second Talk")
                .description("Another topic")
                .sessionType("workshop")
                .speakerPoolId(speaker1.getId()) // Same speaker
                .build();
        sessionRepository.save(duplicateSpeakerSession);

        // Try to assign the second session at overlapping time
        String conflictingAssignment = """
                {
                  "startTime": "%s",
                  "endTime": "%s",
                  "room": "Room B",
                  "changeReason": "drag_drop_reassignment"
                }
                """.formatted(
                eventDate.plus(2, ChronoUnit.HOURS).plus(30, ChronoUnit.MINUTES).toString(), // 18:30 (overlaps)
                eventDate.plus(2, ChronoUnit.HOURS).plus(75, ChronoUnit.MINUTES).toString()  // 19:15
        );

        mockMvc.perform(patch("/api/v1/events/{eventCode}/sessions/{sessionSlug}/timing",
                        eventCode, "john-doe-second-talk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictingAssignment))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.conflicts[0].type").value("speaker_double_booked"))
                .andExpect(jsonPath("$.conflicts[0].conflictingSessionSlug").value("john-doe-techcorp"));
    }

    /**
     * Test: Bulk assign timing to multiple sessions
     * AC13: Provide bulk auto-assignment based on preferences
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_bulkAssignTiming_when_validBulkRequestProvided() throws Exception {
        String bulkTimingRequest = """
                {
                  "assignments": [
                    {
                      "sessionSlug": "john-doe-techcorp",
                      "startTime": "%s",
                      "endTime": "%s",
                      "room": "Main Hall"
                    },
                    {
                      "sessionSlug": "jane-smith-datainc",
                      "startTime": "%s",
                      "endTime": "%s",
                      "room": "Main Hall"
                    }
                  ],
                  "changeReason": "preference_matching"
                }
                """.formatted(
                eventDate.plus(2, ChronoUnit.HOURS).toString(),
                eventDate.plus(2, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES).toString(),
                eventDate.plus(3, ChronoUnit.HOURS).toString(),
                eventDate.plus(3, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES).toString()
        );

        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/bulk-timing", eventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkTimingRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedCount").value(2))
                .andExpect(jsonPath("$.sessions", hasSize(2)));

        // Verify all sessions assigned
        mockMvc.perform(get("/api/v1/events/{eventCode}/sessions/unassigned", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0))); // All assigned
    }

    /**
     * Test: Bulk assignment fails atomically if conflicts detected
     * AC13: Bulk operation should be atomic (all or nothing)
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return409_when_bulkAssignmentHasConflicts() throws Exception {
        // Bulk request with room overlap conflict (both at same time in same room)
        String conflictingBulkRequest = """
                {
                  "assignments": [
                    {
                      "sessionSlug": "john-doe-techcorp",
                      "startTime": "%s",
                      "endTime": "%s",
                      "room": "Main Hall"
                    },
                    {
                      "sessionSlug": "jane-smith-datainc",
                      "startTime": "%s",
                      "endTime": "%s",
                      "room": "Main Hall"
                    }
                  ],
                  "changeReason": "preference_matching"
                }
                """.formatted(
                eventDate.plus(2, ChronoUnit.HOURS).toString(),        // Both at 18:00
                eventDate.plus(2, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES).toString(),
                eventDate.plus(2, ChronoUnit.HOURS).toString(),        // Same time!
                eventDate.plus(2, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES).toString()
        );

        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/bulk-timing", eventCode)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conflictingBulkRequest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BULK_TIMING_CONFLICTS"))
                .andExpect(jsonPath("$.message").value(containsString("no changes applied")))
                .andExpect(jsonPath("$.conflictCount").value(greaterThan(0)));

        // Verify NO sessions were assigned (atomic rollback)
        mockMvc.perform(get("/api/v1/events/{eventCode}/sessions/unassigned", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2))); // Still 2 unassigned
    }

    /**
     * Test: Get complete conflict analysis for event
     * AC9: Comprehensive conflict detection across all sessions
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_detectConflicts_when_conflictsExist() throws Exception {
        // Create conflicting assignments
        Session conflict1 = Session.builder()
                .eventId(testEvent.getId())
                .eventCode(testEvent.getEventCode())
                .sessionSlug("conflict-session-1")
                .title("Conflict Session 1")
                .sessionType("presentation")
                .startTime(eventDate.plus(2, ChronoUnit.HOURS))
                .endTime(eventDate.plus(2, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES))
                .room("Main Hall") // Same room as assignedSession
                .build();
        sessionRepository.save(conflict1);

        mockMvc.perform(get("/api/v1/events/{eventCode}/sessions/conflicts", eventCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasConflicts").value(true))
                .andExpect(jsonPath("$.conflictCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.conflicts").isArray())
                .andExpect(jsonPath("$.conflicts[0].conflictType").value("room_overlap"))
                .andExpect(jsonPath("$.conflicts[0].severity").value(oneOf("error", "warning")))
                .andExpect(jsonPath("$.conflicts[0].resolution").exists());
    }

    /**
     * Test: Event not found returns 404
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return404_when_eventNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/sessions/unassigned", "INVALID"))
                .andExpect(status().isNotFound());
    }

    /**
     * Test: Session not found returns 404
     */
    @Test
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return404_when_sessionNotFound() throws Exception {
        String timingRequest = """
                {
                  "startTime": "%s",
                  "endTime": "%s",
                  "room": "Main Hall"
                }
                """.formatted(
                eventDate.plus(2, ChronoUnit.HOURS).toString(),
                eventDate.plus(2, ChronoUnit.HOURS).plus(45, ChronoUnit.MINUTES).toString()
        );

        mockMvc.perform(patch("/api/v1/events/{eventCode}/sessions/{sessionSlug}/timing",
                        eventCode, "non-existent-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timingRequest))
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
        mockMvc.perform(get("/api/v1/events/{eventCode}/sessions/unassigned", eventCode))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Test: Non-organizer access returns 403
     */
    @Test
    @WithMockUser(username = "regular.user", roles = {"ATTENDEE"})
    void should_return403_when_notOrganizer() throws Exception {
        mockMvc.perform(get("/api/v1/events/{eventCode}/sessions/unassigned", eventCode))
                .andExpect(status().isForbidden());
    }
}
