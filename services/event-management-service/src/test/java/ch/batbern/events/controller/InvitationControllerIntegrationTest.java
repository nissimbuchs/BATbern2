package ch.batbern.events.controller;

import ch.batbern.events.AbstractIntegrationTest;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.InvitationStatus;
import ch.batbern.events.domain.ResponseType;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.domain.SpeakerInvitation;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerInvitationRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerRepository;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.EventWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for InvitationController - Story 6.1.
 *
 * Tests REST endpoints for speaker invitation management with:
 * - Role-based access control (ORGANIZER)
 * - Public token-based response endpoints
 * - Input validation
 * - Status transitions
 */
@Transactional
class InvitationControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SpeakerInvitationRepository invitationRepository;

    @Autowired
    private SpeakerRepository speakerRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    private static final String TEST_EVENT_CODE = "TEST-2026-Q1";
    private static final String TEST_SPEAKER_USERNAME = "test.speaker";
    private static final String TEST_ORGANIZER_USERNAME = "test.organizer";

    private Speaker testSpeaker;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        // Create test speaker
        testSpeaker = speakerRepository.save(Speaker.builder()
                .username(TEST_SPEAKER_USERNAME)
                .build());

        // Create test event
        testEvent = eventRepository.save(Event.builder()
                .eventCode(TEST_EVENT_CODE)
                .title("Test Event")
                .eventNumber(999)
                .date(Instant.now().plus(60, ChronoUnit.DAYS))
                .eventType(EventType.EVENING)
                .registrationDeadline(Instant.now().plus(30, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address, Bern")
                .venueCapacity(100)
                .organizerUsername(TEST_ORGANIZER_USERNAME)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .build());
    }

    @Nested
    class SendInvitation {

        @Test
        @WithMockUser(username = TEST_ORGANIZER_USERNAME, roles = "ORGANIZER")
        void should_sendInvitation_when_organizerRequest() throws Exception {
            // Given
            String requestBody = """
                {
                    "username": "%s",
                    "expirationDays": 14
                }
                """.formatted(TEST_SPEAKER_USERNAME);

            // When/Then
            mockMvc.perform(post("/api/v1/events/{eventCode}/invitations", TEST_EVENT_CODE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value(TEST_SPEAKER_USERNAME))
                    .andExpect(jsonPath("$.eventCode").value(TEST_EVENT_CODE))
                    .andExpect(jsonPath("$.invitationStatus").value("SENT"));

            // Verify invitation created
            assertThat(invitationRepository.findByUsernameAndEventCode(TEST_SPEAKER_USERNAME, TEST_EVENT_CODE))
                    .isPresent();
        }

        @Test
        @WithMockUser(username = "other.user", roles = "SPEAKER")
        void should_rejectInvitation_when_notOrganizer() throws Exception {
            // Given
            String requestBody = """
                {
                    "username": "%s",
                    "expirationDays": 14
                }
                """.formatted(TEST_SPEAKER_USERNAME);

            // When/Then
            mockMvc.perform(post("/api/v1/events/{eventCode}/invitations", TEST_EVENT_CODE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden());
        }

        @Test
        void should_rejectInvitation_when_notAuthenticated() throws Exception {
            // Given
            String requestBody = """
                {
                    "username": "%s",
                    "expirationDays": 14
                }
                """.formatted(TEST_SPEAKER_USERNAME);

            // When/Then
            // Spring Security returns 403 Forbidden for anonymous users when method security is applied
            mockMvc.perform(post("/api/v1/events/{eventCode}/invitations", TEST_EVENT_CODE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class BulkSendInvitation {

        /**
         * Helper to set up unique test data for bulk tests.
         * Bulk invitation uses Propagation.NOT_SUPPORTED, so we must:
         * 1. Rollback parent @BeforeEach data (not committed)
         * 2. Create unique test data
         * 3. Commit it to the database
         */
        private record BulkTestData(String speakerUsername, String eventCode) {}

        private BulkTestData setupBulkTestData(String testSuffix) {
            // Rollback parent @BeforeEach data (not needed for bulk tests)
            TestTransaction.flagForRollback();
            TestTransaction.end();

            // Start new transaction with unique test data
            TestTransaction.start();

            String uniqueSpeaker = "bulk.speaker." + testSuffix;
            String uniqueEventCode = "BULK-" + testSuffix;

            speakerRepository.save(Speaker.builder()
                    .username(uniqueSpeaker)
                    .build());

            eventRepository.save(Event.builder()
                    .eventCode(uniqueEventCode)
                    .title("Bulk Test Event")
                    .eventNumber(800 + testSuffix.hashCode() % 100)
                    .date(Instant.now().plus(60, ChronoUnit.DAYS))
                    .eventType(EventType.EVENING)
                    .registrationDeadline(Instant.now().plus(30, ChronoUnit.DAYS))
                    .venueName("Test Venue")
                    .venueAddress("Test Address, Bern")
                    .venueCapacity(100)
                    .organizerUsername("organizer")
                    .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                    .build());

            // Commit test data so it's visible to bulk method
            TestTransaction.flagForCommit();
            TestTransaction.end();

            return new BulkTestData(uniqueSpeaker, uniqueEventCode);
        }

        @Test
        @WithMockUser(username = "organizer", roles = "ORGANIZER")
        void should_sendBulkInvitations_when_organizerRequest() throws Exception {
            BulkTestData data = setupBulkTestData("send1");

            // Given
            String requestBody = """
                {
                    "usernames": ["%s"],
                    "expirationDays": 14
                }
                """.formatted(data.speakerUsername());

            // When/Then
            mockMvc.perform(post("/api/v1/events/{eventCode}/invitations/bulk", data.eventCode())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalRequested").value(1))
                    .andExpect(jsonPath("$.successCount").value(1))
                    .andExpect(jsonPath("$.failureCount").value(0))
                    .andExpect(jsonPath("$.successful").isArray())
                    .andExpect(jsonPath("$.successful[0].username").value(data.speakerUsername()));
        }

        @Test
        @WithMockUser(username = "organizer", roles = "ORGANIZER")
        void should_returnPartialSuccess_when_someSpeakersNotFound() throws Exception {
            BulkTestData data = setupBulkTestData("partial1");

            // Given - one valid speaker and one invalid
            String requestBody = """
                {
                    "usernames": ["%s", "non.existent.speaker"],
                    "expirationDays": 14
                }
                """.formatted(data.speakerUsername());

            // When/Then
            mockMvc.perform(post("/api/v1/events/{eventCode}/invitations/bulk", data.eventCode())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalRequested").value(2))
                    .andExpect(jsonPath("$.successCount").value(1))
                    .andExpect(jsonPath("$.failureCount").value(1))
                    .andExpect(jsonPath("$.failures[0].username").value("non.existent.speaker"));
        }

        @Test
        @WithMockUser(username = "user", roles = "ATTENDEE")
        void should_rejectBulkInvitation_when_notOrganizer() throws Exception {
            // Given - uses parent @BeforeEach data (no commit needed for auth check)
            String requestBody = """
                {
                    "usernames": ["%s"],
                    "expirationDays": 14
                }
                """.formatted(TEST_SPEAKER_USERNAME);

            // When/Then
            mockMvc.perform(post("/api/v1/events/{eventCode}/invitations/bulk", TEST_EVENT_CODE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class RespondToInvitation {

        private String validToken;

        @BeforeEach
        void setUpInvitation() {
            validToken = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "");

            invitationRepository.save(SpeakerInvitation.builder()
                    .username(TEST_SPEAKER_USERNAME)
                    .eventCode(TEST_EVENT_CODE)
                    .responseToken(validToken)
                    .invitationStatus(InvitationStatus.SENT)
                    .sentAt(Instant.now())
                    .expiresAt(Instant.now().plus(14, ChronoUnit.DAYS))
                    .createdBy(TEST_ORGANIZER_USERNAME)
                    .build());
        }

        @Test
        void should_acceptInvitation_when_validTokenAndResponse() throws Exception {
            // Given
            String requestBody = """
                {
                    "responseType": "ACCEPTED"
                }
                """;

            // When/Then
            mockMvc.perform(post("/api/v1/invitations/respond/{token}", validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.responseType").value("ACCEPTED"))
                    .andExpect(jsonPath("$.invitationStatus").value("RESPONDED"));
        }

        @Test
        void should_declineInvitation_when_validTokenWithReason() throws Exception {
            // Given
            String requestBody = """
                {
                    "responseType": "DECLINED",
                    "declineReason": "Schedule conflict"
                }
                """;

            // When/Then
            mockMvc.perform(post("/api/v1/invitations/respond/{token}", validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.responseType").value("DECLINED"))
                    .andExpect(jsonPath("$.declineReason").value("Schedule conflict"));
        }

        @Test
        void should_returnNotFound_when_invalidToken() throws Exception {
            // Given
            String requestBody = """
                {
                    "responseType": "ACCEPTED"
                }
                """;

            // When/Then
            mockMvc.perform(post("/api/v1/invitations/respond/{token}", "invalid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isNotFound());
        }

        @Test
        void should_getInvitationDetails_when_validToken() throws Exception {
            // When/Then
            mockMvc.perform(get("/api/v1/invitations/respond/{token}", validToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(TEST_SPEAKER_USERNAME))
                    .andExpect(jsonPath("$.eventCode").value(TEST_EVENT_CODE));
        }

        @Test
        void should_copyPreferencesToSpeakerPool_when_acceptedWithTitle() throws Exception {
            // Given - Create speaker pool entry and invitation with speakerPoolId
            SpeakerPool poolEntry = speakerPoolRepository.save(SpeakerPool.builder()
                    .eventId(testEvent.getId())
                    .speakerName("Pool Speaker")
                    .email("pool.speaker@example.com")
                    .status(SpeakerWorkflowState.CONTACTED)
                    .build());

            String poolToken = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "");

            invitationRepository.save(SpeakerInvitation.builder()
                    .speakerPoolId(poolEntry.getId())
                    .speakerEmail("pool.speaker@example.com")
                    .speakerName("Pool Speaker")
                    .eventCode(TEST_EVENT_CODE)
                    .responseToken(poolToken)
                    .invitationStatus(InvitationStatus.SENT)
                    .sentAt(Instant.now())
                    .expiresAt(Instant.now().plus(14, ChronoUnit.DAYS))
                    .createdBy(TEST_ORGANIZER_USERNAME)
                    .build());

            String requestBody = """
                {
                    "responseType": "ACCEPTED",
                    "preferences": {
                        "initialPresentationTitle": "My Awesome Talk",
                        "commentsForOrganizer": "Looking forward to it!"
                    }
                }
                """;

            // When
            mockMvc.perform(post("/api/v1/invitations/respond/{token}", poolToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.responseType").value("ACCEPTED"));

            // Then - Verify presentation title was copied to speaker pool
            SpeakerPool updatedPoolEntry = speakerPoolRepository.findById(poolEntry.getId()).orElseThrow();
            assertThat(updatedPoolEntry.getProposedPresentationTitle()).isEqualTo("My Awesome Talk");
            assertThat(updatedPoolEntry.getCommentsForOrganizer()).isEqualTo("Looking forward to it!");
            assertThat(updatedPoolEntry.getStatus()).isEqualTo(SpeakerWorkflowState.ACCEPTED);
        }
    }

    @Nested
    class ListInvitations {

        @BeforeEach
        void setUpInvitations() {
            String token1 = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "");
            String token2 = UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", "");

            invitationRepository.save(SpeakerInvitation.builder()
                    .username(TEST_SPEAKER_USERNAME)
                    .eventCode(TEST_EVENT_CODE)
                    .responseToken(token1)
                    .invitationStatus(InvitationStatus.SENT)
                    .sentAt(Instant.now())
                    .expiresAt(Instant.now().plus(14, ChronoUnit.DAYS))
                    .createdBy(TEST_ORGANIZER_USERNAME)
                    .build());

            // Create second speaker and invitation
            speakerRepository.save(Speaker.builder()
                    .username("second.speaker")
                    .build());

            invitationRepository.save(SpeakerInvitation.builder()
                    .username("second.speaker")
                    .eventCode(TEST_EVENT_CODE)
                    .responseToken(token2)
                    .invitationStatus(InvitationStatus.RESPONDED)
                    .responseType(ResponseType.ACCEPTED)
                    .sentAt(Instant.now())
                    .respondedAt(Instant.now())
                    .expiresAt(Instant.now().plus(14, ChronoUnit.DAYS))
                    .createdBy(TEST_ORGANIZER_USERNAME)
                    .build());
        }

        @Test
        @WithMockUser(username = TEST_ORGANIZER_USERNAME, roles = "ORGANIZER")
        void should_listEventInvitations_when_organizer() throws Exception {
            // When/Then
            mockMvc.perform(get("/api/v1/events/{eventCode}/invitations", TEST_EVENT_CODE))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)));
        }

        @Test
        @WithMockUser(username = TEST_ORGANIZER_USERNAME, roles = "ORGANIZER")
        void should_listSpeakerInvitations_when_organizer() throws Exception {
            // When/Then
            mockMvc.perform(get("/api/v1/speakers/{username}/invitations", TEST_SPEAKER_USERNAME))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].username").value(TEST_SPEAKER_USERNAME));
        }
    }
}
