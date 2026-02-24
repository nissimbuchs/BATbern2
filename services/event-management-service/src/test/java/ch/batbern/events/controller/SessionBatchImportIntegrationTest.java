package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.shared.types.EventWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration Tests for Session Batch Import
 *
 * Test Scenarios:
 * - Import sessions with valid JSON data
 * - Skip duplicate sessions (same event + title)
 * - Assign speakers by matching speakerId to username
 * - Assign event organizer as moderator when no speakers
 * - Calculate sequential 45-minute time slots
 * - Handle missing speakers gracefully
 *
 * TDD Workflow: RED Phase - These tests will fail until implementation is complete
 *
 * Uses Testcontainers PostgreSQL for production parity.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
public class SessionBatchImportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SessionUserRepository sessionUserRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserApiClient userApiClient;

    private Event testEvent;
    private static final String TEST_EVENT_CODE = "BATbern142";
    private static final int TEST_EVENT_NUMBER = 142;

    @BeforeEach
    void setUp() {
        // Reset mocks
        reset(userApiClient);

        // Clean database
        sessionUserRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();

        // Mock UserApiClient
        mockUserApiClient();

        // Create test event
        testEvent = createTestEvent();
    }

    private Event createTestEvent() {
        Event event = new Event();
        event.setEventNumber(TEST_EVENT_NUMBER);
        event.setEventCode(TEST_EVENT_CODE);
        event.setTitle("BATbern 142 Test Event");
        event.setDescription("Test event for session import");
        event.setEventType(EventType.EVENING); // Event starts at 18:00
        event.setDate(Instant.parse("2024-12-15T18:00:00Z"));
        event.setRegistrationDeadline(Instant.parse("2024-12-10T23:59:59Z"));
        event.setVenueName("Test Venue");
        event.setVenueAddress("Test Address");
        event.setVenueCapacity(200);
        event.setOrganizerUsername("john.doe");
        event.setWorkflowState(EventWorkflowState.AGENDA_PUBLISHED);
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());

        return eventRepository.save(event);
    }

    private void mockUserApiClient() {
        // Mock getUserByUsername() for speaker assignments
        when(userApiClient.getUserByUsername(anyString()))
                .thenAnswer(invocation -> {
                    String username = invocation.getArgument(0);

                    // Return different users based on username
                    if (username.equals("thomas.goetz")) {
                        return new UserResponse()
                                .id(username)
                                .firstName("Thomas")
                                .lastName("Goetz")
                                .email("thomas.goetz@mobiliar.ch")
                                .companyId("mobiliar");
                    } else if (username.equals("john.doe")) {
                        return new UserResponse()
                                .id(username)
                                .firstName("John")
                                .lastName("Doe")
                                .email("john.doe@example.com")
                                .companyId("test-company");
                    } else {
                        // Unknown speaker - return null to test graceful handling
                        return null;
                    }
                });
    }

    @Test
    @DisplayName("Should import sessions when valid JSON provided")
    void should_importSessions_when_validJsonProvided() throws Exception {
        // Given: Valid session import payload
        String requestBody = """
                [
                    {
                        "bat": 142,
                        "title": "User Interface Design",
                        "abstract": "Discussion on modern UI patterns",
                        "pdf": "BAT142_UI_Design.pdf",
                        "authoren": "",
                        "referenten": [
                            {
                                "name": "Thomas Goetz, Die Mobiliar",
                                "bio": "Expert in UI design",
                                "company": "mobiliar",
                                "portrait": "thomas.goetz.jpg",
                                "speakerId": "thomas.goetz"
                            }
                        ]
                    },
                    {
                        "bat": 142,
                        "title": "Backend Architecture",
                        "abstract": "Modern backend patterns",
                        "pdf": "BAT142_Backend.pdf",
                        "authoren": "",
                        "referenten": []
                    }
                ]
                """;

        // When: POST batch import
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/batch-import", TEST_EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // Then: Expect 200 OK with import results
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessed").value(2))
                .andExpect(jsonPath("$.successfullyCreated").value(2))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.details", hasSize(2)))
                .andExpect(jsonPath("$.details[0].title").value("User Interface Design"))
                .andExpect(jsonPath("$.details[0].status").value("success"))
                .andExpect(jsonPath("$.details[0].sessionSlug").value(notNullValue()))
                .andExpect(jsonPath("$.details[1].title").value("Backend Architecture"))
                .andExpect(jsonPath("$.details[1].status").value("success"));

        // Verify: Sessions created in database
        List<Session> sessions = sessionRepository.findByEventId(testEvent.getId());
        assertThat(sessions).hasSize(2);

        // Verify: First session has correct data
        Session session1 = sessions.stream()
                .filter(s -> s.getTitle().equals("User Interface Design"))
                .findFirst()
                .orElseThrow();
        assertThat(session1.getDescription()).contains("Discussion on modern UI patterns");
        // PDF is no longer embedded in description - it's stored as a separate material entity
        assertThat(session1.getDescription()).doesNotContain("PDF:");
        assertThat(session1.getSessionType()).isEqualTo("presentation");
        assertThat(session1.getLanguage()).isEqualTo("de");

        // Verify: Time slots
        Session session2 = sessions.stream()
                .filter(s -> s.getTitle().equals("Backend Architecture"))
                .findFirst()
                .orElseThrow();

        // First session (with speakers): 18:00 - 18:45 (45 minutes)
        assertThat(session1.getStartTime()).isEqualTo(Instant.parse("2024-12-15T18:00:00Z"));
        assertThat(session1.getEndTime()).isEqualTo(Instant.parse("2024-12-15T18:45:00Z"));

        // Second session (no speakers): 18:00 - 18:10 (10 minutes, slot 0)
        assertThat(session2.getStartTime()).isEqualTo(Instant.parse("2024-12-15T18:00:00Z"));
        assertThat(session2.getEndTime()).isEqualTo(Instant.parse("2024-12-15T18:10:00Z"));
    }

    @Test
    @DisplayName("Should skip duplicates when session already exists")
    void should_skipDuplicates_when_sessionAlreadyExists() throws Exception {
        // Given: Existing session in database
        Session existingSession = new Session();
        existingSession.setEventId(testEvent.getId());
        existingSession.setEventCode(testEvent.getEventCode());
        existingSession.setTitle("User Interface Design");
        existingSession.setDescription("Existing session");
        existingSession.setSessionType("presentation");
        existingSession.setStartTime(Instant.parse("2024-12-15T18:00:00Z"));
        existingSession.setEndTime(Instant.parse("2024-12-15T18:45:00Z"));
        existingSession.setLanguage("de");
        existingSession.setSessionSlug("user-interface-design-abc123");
        existingSession.setCreatedAt(Instant.now());
        existingSession.setUpdatedAt(Instant.now());
        sessionRepository.save(existingSession);

        // And: Import request with same title
        String requestBody = """
                [
                    {
                        "bat": 142,
                        "title": "User Interface Design",
                        "abstract": "Discussion on modern UI patterns",
                        "pdf": "BAT142_UI_Design.pdf",
                        "referenten": []
                    }
                ]
                """;

        // When: POST batch import
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/batch-import", TEST_EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // Then: Expect session skipped
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessed").value(1))
                .andExpect(jsonPath("$.successfullyCreated").value(0))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.details[0].status").value("skipped"))
                .andExpect(jsonPath("$.details[0].message").value(containsString("already exists")));

        // Verify: Still only one session in database
        List<Session> sessions = sessionRepository.findByEventId(testEvent.getId());
        assertThat(sessions).hasSize(1);
    }

    @Test
    @DisplayName("Should assign speakers when referenten provided")
    void should_assignSpeakers_when_referentenProvided() throws Exception {
        // Given: Import request with speaker
        String requestBody = """
                [
                    {
                        "bat": 142,
                        "title": "User Interface Design",
                        "abstract": "Discussion on modern UI patterns",
                        "pdf": "BAT142_UI_Design.pdf",
                        "referenten": [
                            {
                                "name": "Thomas Goetz, Die Mobiliar",
                                "bio": "Expert in UI design",
                                "company": "mobiliar",
                                "portrait": "thomas.goetz.jpg",
                                "speakerId": "thomas.goetz"
                            }
                        ]
                    }
                ]
                """;

        // When: POST batch import
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/batch-import", TEST_EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successfullyCreated").value(1));

        // Then: Verify speaker assignment
        Session session = sessionRepository.findByEventId(testEvent.getId()).get(0);
        List<SessionUser> speakers = sessionUserRepository.findBySessionId(session.getId());

        assertThat(speakers).hasSize(1);
        assertThat(speakers.get(0).getUsername()).isEqualTo("thomas.goetz");
        assertThat(speakers.get(0).getSpeakerRole()).isEqualTo(SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        assertThat(speakers.get(0).isConfirmed()).isTrue(); // Historical data: already confirmed
    }

    @Test
    @DisplayName("Should assign moderator when no referenten")
    void should_assignModerator_when_noReferenten() throws Exception {
        // Given: Import request without speakers (empty referenten)
        String requestBody = """
                [
                    {
                        "bat": 142,
                        "title": "Programmheft",
                        "abstract": "Program booklet",
                        "pdf": "BAT142_Programm.pdf",
                        "authoren": "",
                        "referenten": []
                    }
                ]
                """;

        // When: POST batch import
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/batch-import", TEST_EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successfullyCreated").value(1));

        // Then: Verify event organizer assigned as moderator
        Session session = sessionRepository.findByEventId(testEvent.getId()).get(0);
        List<SessionUser> speakers = sessionUserRepository.findBySessionId(session.getId());

        assertThat(speakers).hasSize(1);
        assertThat(speakers.get(0).getUsername()).isEqualTo("john.doe"); // event organizer
        assertThat(speakers.get(0).getSpeakerRole()).isEqualTo(SessionUser.SpeakerRole.MODERATOR);
    }

    @Test
    @DisplayName("Should calculate sequential times when multiple sessions imported")
    void should_calculateSequentialTimes_when_multipleSessionsImported() throws Exception {
        // Given: Import 3 sessions with speakers (so they get sequential 45-min slots)
        String requestBody = """
                [
                    {
                        "bat": 142,
                        "title": "Session 1",
                        "abstract": "First session",
                        "referenten": [
                            {
                                "name": "Thomas Goetz",
                                "speakerId": "thomas.goetz"
                            }
                        ]
                    },
                    {
                        "bat": 142,
                        "title": "Session 2",
                        "abstract": "Second session",
                        "referenten": [
                            {
                                "name": "John Doe",
                                "speakerId": "john.doe"
                            }
                        ]
                    },
                    {
                        "bat": 142,
                        "title": "Session 3",
                        "abstract": "Third session",
                        "referenten": [
                            {
                                "name": "Thomas Goetz",
                                "speakerId": "thomas.goetz"
                            }
                        ]
                    }
                ]
                """;

        // When: POST batch import
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/batch-import", TEST_EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successfullyCreated").value(3));

        // Then: Verify sequential 45-minute slots
        List<Session> sessions = sessionRepository.findByEventId(testEvent.getId());
        assertThat(sessions).hasSize(3);

        // Session 1: 18:00 - 18:45 (event starts at 18:00)
        Session session1 = sessions.stream().filter(s -> s.getTitle().equals("Session 1")).findFirst().orElseThrow();
        assertThat(session1.getStartTime()).isEqualTo(Instant.parse("2024-12-15T18:00:00Z"));
        assertThat(session1.getEndTime()).isEqualTo(Instant.parse("2024-12-15T18:45:00Z"));

        // Session 2: 18:45 - 19:30
        Session session2 = sessions.stream().filter(s -> s.getTitle().equals("Session 2")).findFirst().orElseThrow();
        assertThat(session2.getStartTime()).isEqualTo(Instant.parse("2024-12-15T18:45:00Z"));
        assertThat(session2.getEndTime()).isEqualTo(Instant.parse("2024-12-15T19:30:00Z"));

        // Session 3: 19:30 - 20:15
        Session session3 = sessions.stream().filter(s -> s.getTitle().equals("Session 3")).findFirst().orElseThrow();
        assertThat(session3.getStartTime()).isEqualTo(Instant.parse("2024-12-15T19:30:00Z"));
        assertThat(session3.getEndTime()).isEqualTo(Instant.parse("2024-12-15T20:15:00Z"));
    }

    @Test
    @DisplayName("Should handle missing speaker when username not found")
    void should_handleMissingSpeaker_when_usernameNotFound() throws Exception {
        // Given: Import with unknown speaker (will return null from UserApiClient mock)
        String requestBody = """
                [
                    {
                        "bat": 142,
                        "title": "Unknown Speaker Session",
                        "abstract": "Session with unknown speaker",
                        "referenten": [
                            {
                                "name": "Unknown Person",
                                "speakerId": "unknown.person"
                            }
                        ]
                    }
                ]
                """;

        // When: POST batch import
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/batch-import", TEST_EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // Then: Session still created successfully (graceful degradation)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successfullyCreated").value(1))
                .andExpect(jsonPath("$.failed").value(0));

        // Verify: Session created but no speaker assigned (skipped unknown speaker)
        Session session = sessionRepository.findByEventId(testEvent.getId()).get(0);
        List<SessionUser> speakers = sessionUserRepository.findBySessionId(session.getId());

        // No speakers assigned since the username wasn't found
        assertThat(speakers).isEmpty();
    }

    @Test
    @DisplayName("Should return 404 when event not found")
    void should_return404_when_eventNotFound() throws Exception {
        // Given: Import request for non-existent event
        String requestBody = """
                [
                    {
                        "bat": 999,
                        "title": "Test Session",
                        "abstract": "Test",
                        "referenten": []
                    }
                ]
                """;

        // When: POST batch import with non-existent event code
        mockMvc.perform(post("/api/v1/events/{eventCode}/sessions/batch-import", "BATbern999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                // Then: Expect 404 Not Found
                .andExpect(status().isNotFound());
    }
}
