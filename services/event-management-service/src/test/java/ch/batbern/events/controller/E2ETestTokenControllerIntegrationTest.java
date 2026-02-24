package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for E2ETestTokenController.
 *
 * Tests the E2E test token generation endpoint that is used for automated testing.
 * This endpoint is only available in dev/test profiles.
 *
 * Key test scenarios:
 * - Generate tokens for speakers with sessions (any workflow status)
 * - Generate tokens for speakers without sessions
 * - Validate event code pattern (must match ^BATbern[0-9]+$)
 */
@Transactional
class E2ETestTokenControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    private Event testEvent;
    private Session testSession;
    private SpeakerPool speakerWithSession;
    private SpeakerPool speakerWithoutSession;
    private static final String VALID_EVENT_CODE = "BATbern999";

    @BeforeEach
    void setUp() {
        // Clean up
        speakerPoolRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();

        // Create test event with valid event code pattern and all required fields
        Instant eventDate = LocalDate.now().plusMonths(2).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        testEvent = new Event();
        testEvent.setEventCode(VALID_EVENT_CODE);
        testEvent.setTitle("E2E Test Event");
        testEvent.setEventNumber(999);
        testEvent.setDate(eventDate);
        testEvent.setWorkflowState(EventWorkflowState.SPEAKER_IDENTIFICATION);
        testEvent.setOrganizerUsername("test.organizer");
        testEvent.setEventType(EventType.FULL_DAY);
        testEvent.setRegistrationDeadline(eventDate.minusSeconds(7 * 24 * 3600)); // 7 days before event
        testEvent.setVenueName("Test Venue");
        testEvent.setVenueAddress("123 Test Street, Bern");
        testEvent.setVenueCapacity(200);
        testEvent = eventRepository.save(testEvent);

        // Create test session
        testSession = new Session();
        testSession.setEventId(testEvent.getId());
        testSession.setEventCode(VALID_EVENT_CODE);
        testSession.setTitle("Test Session");
        testSession.setSessionSlug("test-session");
        testSession.setSessionType("presentation");
        testSession = sessionRepository.save(testSession);

        // Create speaker with session assigned (in QUALITY_REVIEWED status - typical for speakers with sessions)
        speakerWithSession = new SpeakerPool();
        speakerWithSession.setEventId(testEvent.getId());
        speakerWithSession.setSpeakerName("Speaker With Session");
        speakerWithSession.setEmail("with.session@test.com");
        speakerWithSession.setStatus(SpeakerWorkflowState.QUALITY_REVIEWED);
        speakerWithSession.setSessionId(testSession.getId());
        speakerWithSession.setCreatedAt(Instant.now());
        speakerWithSession = speakerPoolRepository.save(speakerWithSession);

        // Create speaker without session (in ACCEPTED status)
        speakerWithoutSession = new SpeakerPool();
        speakerWithoutSession.setEventId(testEvent.getId());
        speakerWithoutSession.setSpeakerName("Speaker Without Session");
        speakerWithoutSession.setEmail("without.session@test.com");
        speakerWithoutSession.setStatus(SpeakerWorkflowState.ACCEPTED);
        speakerWithoutSession.setSessionId(null);
        speakerWithoutSession.setCreatedAt(Instant.now());
        speakerWithoutSession = speakerPoolRepository.save(speakerWithoutSession);
    }

    @Nested
    @DisplayName("POST /api/v1/e2e-test/tokens/generate-e2e-set")
    class GenerateE2ETokenSet {

        @Test
        @DisplayName("should generate content token for speaker with session regardless of workflow status")
        void should_generateContentToken_when_speakerHasSession() throws Exception {
            mockMvc.perform(post("/api/v1/e2e-test/tokens/generate-e2e-set")
                            .param("eventCode", VALID_EVENT_CODE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventCode", is(VALID_EVENT_CODE)))
                    .andExpect(jsonPath("$.tokens.E2E_SPEAKER_CONTENT_TOKEN", notNullValue()))
                    .andExpect(jsonPath("$.contentSpeaker.id", is(speakerWithSession.getId().toString())))
                    .andExpect(jsonPath("$.contentSpeaker.name", is("Speaker With Session")))
                    .andExpect(jsonPath("$.contentSpeaker.sessionId", is(testSession.getId().toString())));
        }

        @Test
        @DisplayName("should generate no-session token for speaker without session")
        void should_generateNoSessionToken_when_speakerHasNoSession() throws Exception {
            mockMvc.perform(post("/api/v1/e2e-test/tokens/generate-e2e-set")
                            .param("eventCode", VALID_EVENT_CODE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokens.E2E_SPEAKER_NO_SESSION_TOKEN", notNullValue()))
                    .andExpect(jsonPath("$.noSessionSpeaker.id", is(speakerWithoutSession.getId().toString())))
                    .andExpect(jsonPath("$.noSessionSpeaker.name", is("Speaker Without Session")));
        }

        @Test
        @DisplayName("should generate profile token preferring speaker with session")
        void should_generateProfileToken_preferringSpeakerWithSession() throws Exception {
            mockMvc.perform(post("/api/v1/e2e-test/tokens/generate-e2e-set")
                            .param("eventCode", VALID_EVENT_CODE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokens.E2E_SPEAKER_PROFILE_TOKEN", notNullValue()))
                    .andExpect(jsonPath("$.profileSpeaker.hasSession", is(true)));
        }

        @Test
        @DisplayName("should include export commands for shell usage")
        void should_includeExportCommands() throws Exception {
            mockMvc.perform(post("/api/v1/e2e-test/tokens/generate-e2e-set")
                            .param("eventCode", VALID_EVENT_CODE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.exportCommands", notNullValue()));
        }

        @Test
        @DisplayName("should find speaker with session in any workflow status")
        void should_findSpeakerWithSession_inAnyWorkflowStatus() throws Exception {
            // Update speaker to CONFIRMED status (another valid status for speakers with sessions)
            speakerWithSession.setStatus(SpeakerWorkflowState.CONFIRMED);
            speakerPoolRepository.save(speakerWithSession);

            mockMvc.perform(post("/api/v1/e2e-test/tokens/generate-e2e-set")
                            .param("eventCode", VALID_EVENT_CODE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contentSpeaker.id", is(speakerWithSession.getId().toString())))
                    .andExpect(jsonPath("$.tokens.E2E_SPEAKER_CONTENT_TOKEN", notNullValue()));
        }

        @Test
        @DisplayName("should use default event code BATbern998 when not specified")
        void should_useDefaultEventCode_whenNotSpecified() throws Exception {
            // Create event with default code and all required fields
            Instant eventDate = LocalDate.now().plusMonths(2).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
            Event defaultEvent = new Event();
            defaultEvent.setEventCode("BATbern998");
            defaultEvent.setTitle("Default E2E Event");
            defaultEvent.setEventNumber(998);
            defaultEvent.setDate(eventDate);
            defaultEvent.setWorkflowState(EventWorkflowState.SPEAKER_IDENTIFICATION);
            defaultEvent.setOrganizerUsername("test.organizer");
            defaultEvent.setEventType(EventType.FULL_DAY);
            defaultEvent.setRegistrationDeadline(eventDate.minusSeconds(7 * 24 * 3600));
            defaultEvent.setVenueName("Default Venue");
            defaultEvent.setVenueAddress("123 Default Street, Bern");
            defaultEvent.setVenueCapacity(150);
            eventRepository.save(defaultEvent);

            mockMvc.perform(post("/api/v1/e2e-test/tokens/generate-e2e-set"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventCode", is("BATbern998")));
        }

        @Test
        @DisplayName("should return empty tokens when no speakers exist for event")
        void should_returnEmptyTokens_whenNoSpeakersExist() throws Exception {
            // Create a new event with no speakers and all required fields
            Instant eventDate = LocalDate.now().plusMonths(2).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
            Event emptyEvent = new Event();
            emptyEvent.setEventCode("BATbern001");
            emptyEvent.setTitle("Empty Event");
            emptyEvent.setEventNumber(1);
            emptyEvent.setDate(eventDate);
            emptyEvent.setWorkflowState(EventWorkflowState.SPEAKER_IDENTIFICATION);
            emptyEvent.setOrganizerUsername("test.organizer");
            emptyEvent.setEventType(EventType.FULL_DAY);
            emptyEvent.setRegistrationDeadline(eventDate.minusSeconds(7 * 24 * 3600));
            emptyEvent.setVenueName("Empty Venue");
            emptyEvent.setVenueAddress("123 Empty Street, Bern");
            emptyEvent.setVenueCapacity(100);
            eventRepository.save(emptyEvent);

            mockMvc.perform(post("/api/v1/e2e-test/tokens/generate-e2e-set")
                            .param("eventCode", "BATbern001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventCode", is("BATbern001")))
                    .andExpect(jsonPath("$.tokens").isMap());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/e2e-test/tokens/generate")
    class GenerateSingleToken {

        @Test
        @DisplayName("should generate token for specific speaker")
        void should_generateToken_forSpecificSpeaker() throws Exception {
            mockMvc.perform(post("/api/v1/e2e-test/tokens/generate")
                            .param("speakerPoolId", speakerWithSession.getId().toString())
                            .param("action", "VIEW"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token", notNullValue()))
                    .andExpect(jsonPath("$.speakerPoolId", is(speakerWithSession.getId().toString())))
                    .andExpect(jsonPath("$.speakerName", is("Speaker With Session")))
                    .andExpect(jsonPath("$.action", is("VIEW")));
        }

        @Test
        @DisplayName("should return 404 for non-existent speaker")
        void should_return404_whenSpeakerNotFound() throws Exception {
            UUID nonExistentId = UUID.randomUUID();

            mockMvc.perform(post("/api/v1/e2e-test/tokens/generate")
                            .param("speakerPoolId", nonExistentId.toString())
                            .param("action", "VIEW"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("should respect custom expiry days")
        void should_respectCustomExpiryDays() throws Exception {
            mockMvc.perform(post("/api/v1/e2e-test/tokens/generate")
                            .param("speakerPoolId", speakerWithSession.getId().toString())
                            .param("action", "RESPOND")
                            .param("expiryDays", "7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token", notNullValue()))
                    .andExpect(jsonPath("$.expiryDays", is(7)));
        }
    }
}
