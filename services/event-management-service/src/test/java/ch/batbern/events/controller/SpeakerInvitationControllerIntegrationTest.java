package ch.batbern.events.controller;

import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.shared.test.contract.ContractServer.Reply;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.OutreachHistoryRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for SpeakerInvitationController
 * Story 6.1b: Speaker Invitation System
 *
 * Tests REST endpoint:
 * - POST /api/v1/events/{eventCode}/speakers/{speakerId}/send-invitation (Story 4.10: keyed by the
 *   pool entry id; it took a username or a UUID. The test-only POST /speakers/invite is gone, its
 *   cases live on as pool add + promote in SpeakerPoolWorkflowIntegrationTest.)
 *
 * Uses real PostgreSQL (Testcontainers) and the real UserApiClient against CUMS at the wire
 * ({@link CumsContractHarness}; Epic 17 entry 6 replaced the client mock); mail goes to the test SES
 * recorder.
 */
@Transactional
class SpeakerInvitationControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ch.batbern.events.repository.SessionRepository sessionRepository;

    @Autowired
    private ch.batbern.events.repository.SessionUserRepository sessionUserRepository;

    @Autowired
    private CumsContractHarness cums;

    @Autowired
    private SpeakerStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private OutreachHistoryRepository outreachHistoryRepository;

    private Event testEvent;
    private final String testEventCode = "batbern-2026-spring";
    private final String testEmail = "success+speaker@simulator.amazonses.com";
    private final String testUsername = "speaker.test";

    private static final String GET_OR_CREATE = "/internal/users/get-or-create";
    private static final String CREDENTIALS = "/internal/users/{username}/issue-invitation-credentials";

    /** CUMS's get-or-create answer for {@link #testUsername}. */
    private Reply getOrCreateAnswer(boolean created) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", testUsername);
        body.put("created", created);
        body.put("companyUpdated", false);
        body.put("cognitoUserId", null);
        body.put("user", objectMapper.readValue(CumsContractHarness.userJson(
                testUsername, "Test", "Speaker", testEmail, null), Object.class));
        return Reply.ok(objectMapper.writeValueAsString(body));
    }

    @AfterEach
    void resetCums() {
        cums.reset();
    }

    @BeforeEach
    void setUp() throws Exception {
        // Clean up in correct order (FK constraints)
        speakerPoolRepository.deleteAll();
        eventRepository.deleteAll();

        // Create test event
        testEvent = Event.builder()
                .eventCode(testEventCode)
                .eventNumber(56)
                .title("BATbern Spring 2026")
                .date(Instant.now().plus(60, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(50, ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .eventType(ch.batbern.events.core.dto.generated.EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .organizerUsername("organizer.test")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        testEvent = eventRepository.save(testEvent);

        // CUMS answers get-or-create with the test speaker. Story 11.E.9: sendInvitation resolves the
        // recipient email via PrimarySpeakerResolver -> UserApiClient.getUserByUsername; the harness
        // answers the test speaker with its address and any other (filler) username with a default.
        cums.reset();
        Reply created = getOrCreateAnswer(true);
        cums.answer(POST, GET_OR_CREATE, call -> created);
        cums.user(testUsername, "Test", "Speaker", testEmail);
    }

    /**
     * Story 11.E.9 helper: provision a Session + PRIMARY_SPEAKER session_users row for
     * a speaker pool entry that was seeded directly at READY+. Without this, the
     * {@code findByEventIdAndUsername} JOIN query returns empty and {@code sendInvitation}
     * 404s before reaching its business logic.
     */
    private void seedPrimarySessionUser(SpeakerPool speaker, String username) {
        ch.batbern.events.domain.Session session = ch.batbern.events.domain.Session.builder()
                .eventId(speaker.getEventId())
                .eventCode(testEventCode)
                .title("Seeded — " + username)
                .sessionSlug("seeded-" + java.util.UUID.randomUUID().toString().substring(0, 8))
                .sessionType("presentation")
                .speakerPoolId(speaker.getId())
                .build();
        session = sessionRepository.save(session);
        ch.batbern.events.domain.SessionUser su = ch.batbern.events.domain.SessionUser.builder()
                .session(session)
                .username(username)
                .speakerRole(ch.batbern.events.domain.SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(false)
                .build();
        sessionUserRepository.save(su);
        speaker.setSessionId(session.getId());
        speakerPoolRepository.save(speaker);
    }

    // ==================== AC3: Send Invitation Tests ====================

    /**
     * Test 3.1: Should send invitation and update status to INVITED.
     *
     * <p>Story 11.E.2 (AC7): invitations embed a Cognito login URL + temporary password
     * (issued via the CUMS {@code /issue-invitation-credentials} sibling endpoint).
     */
    @Test
    @WithMockUser(username = "organizer.test", roles = {"ORGANIZER"})
    void should_sendInvitation_when_validRequest() throws Exception {
        // Given - Create speaker pool entry at READY (ADR-009: only READY -> INVITED is allowed,
        // because provisioning runs at CONTACTED -> READY first).
        SpeakerPool speaker = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .speakerName("Test Speaker")
                .status(SpeakerWorkflowState.READY)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        speaker = speakerPoolRepository.save(speaker);
        // Story 11.E.9: findByEventIdAndUsername joins via session_users — provision a
        // primary session_users row so the lookup resolves.
        seedPrimarySessionUser(speaker, testUsername);

        // Story 11.E.2: the INVITED hook asks CUMS for the invitation credentials
        cums.answer(POST, CREDENTIALS, call -> Reply.ok(
                "{\"temporaryPassword\": \"TestTemp123!abcde\", \"action\": \"FRESH_TEMP_PASSWORD\"}"));

        LocalDate responseDeadline = LocalDate.now().plusDays(14);

        // When/Then
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/{speakerId}/send-invitation",
                        testEventCode, speaker.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "responseDeadline": "%s",
                                "locale": "de"
                            }
                            """.formatted(responseDeadline)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("INVITED")))
                .andExpect(jsonPath("$.username", is(testUsername)))
                .andExpect(jsonPath("$.invitedAt").exists())
                .andExpect(jsonPath("$.responseDeadline", is(responseDeadline.toString())));

        // Story 11.E.2 review patch (P8 / B10 / E12): the pre-11.E.2 magic-link token
        // assertion is gone, but the test must still verify the new Cognito-flow wiring
        // happened — the CUMS sibling endpoint was called exactly once with the expected
        // username, so a future no-op'd runInvitedHook fails this test.
        assertThat(cums.calls(POST, CREDENTIALS)).singleElement()
                .satisfies(call -> assertThat(call.pathVariables()).containsEntry("username", testUsername));

        // Story 4.10 (moved from the deleted SpeakerInvitationServiceTest, off its mocks): the
        // status history and the outreach row name the organizer, the response the speaker's
        // username (resolved from session_users, not the path, which is the pool id now).
        assertThat(statusHistoryRepository.findBySpeakerPoolIdOrderByChangedAtDesc(speaker.getId()))
                .extracting(h -> h.getNewStatus(), h -> h.getChangedByUsername())
                .contains(tuple(SpeakerWorkflowState.INVITED, "organizer.test"));
        assertThat(outreachHistoryRepository.findBySpeakerPoolId(speaker.getId()))
                .extracting(o -> o.getOrganizerUsername())
                .containsExactly("organizer.test");

        // The invitation mail itself is sent @Async and resolves the speaker from session_users on
        // its own thread, which cannot see this class's uncommitted test transaction. Its content
        // is pinned by MailGoldenTest (speaker-invitation-*) against the real EmailService.
    }

    /**
     * Test 3.2: Should return 404 when speaker not found
     */
    @Test
    @WithMockUser(username = "organizer.test", roles = {"ORGANIZER"})
    void should_return404_when_speakerNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/{speakerId}/send-invitation",
                        testEventCode, java.util.UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "responseDeadline": "%s"
                            }
                            """.formatted(LocalDate.now().plusDays(14))))
                .andExpect(status().isNotFound());
    }

    /**
     * Test 3.3: Should return 400 when response deadline in past
     */
    @Test
    @WithMockUser(username = "organizer.test", roles = {"ORGANIZER"})
    void should_return400_when_responseDeadlineInPast() throws Exception {
        // Given - Create speaker pool entry
        SpeakerPool speaker = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .speakerName("Test Speaker")
                .status(SpeakerWorkflowState.IDENTIFIED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        speakerPoolRepository.save(speaker);

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/{speakerId}/send-invitation",
                        testEventCode, speaker.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "responseDeadline": "%s"
                            }
                            """.formatted(LocalDate.now().minusDays(1))))
                .andExpect(status().isBadRequest());
    }

    /**
     * Story 11.D.1 (AC4): {@code POST /send-invitation} returns 409 when the slot-capacity
     * gate fires (count(ACCEPTED) + count(INVITED) >= maxSlots). Verifies the HTTP-layer
     * surfaces {@code SLOT_CAPACITY_REACHED} with the documented {@code details} map and
     * leaves the speaker in READY (no state change, no email sent).
     */
    @Test
    @WithMockUser(username = "organizer.test", roles = {"ORGANIZER"})
    void should_return409_when_sendInvitationCalledAndSlotCapacityReached() throws Exception {
        // Saturate the event with ACCEPTED speakers up to the EVENING event-type maxSlots.
        int saturate = 24; // safe upper bound for any event-type maxSlots
        for (int i = 0; i < saturate; i++) {
            SpeakerPool filler = SpeakerPool.builder()
                    .eventId(testEvent.getId())
                    .speakerName("Filler " + i)
                    .status(SpeakerWorkflowState.ACCEPTED)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();
            speakerPoolRepository.save(filler);
        }

        SpeakerPool candidate = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .speakerName("Slot Capacity Candidate")
                .status(SpeakerWorkflowState.READY)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        candidate = speakerPoolRepository.save(candidate);
        // Story 11.E.9: findByEventIdAndUsername joins via session_users.
        seedPrimarySessionUser(candidate, testUsername);

        LocalDate responseDeadline = LocalDate.now().plusDays(14);

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/{speakerId}/send-invitation",
                        testEventCode, candidate.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "responseDeadline": "%s"
                                }
                                """.formatted(responseDeadline)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code", is("SLOT_CAPACITY_REACHED")))
                .andExpect(jsonPath("$.details.eventId", is(testEvent.getId().toString())))
                .andExpect(jsonPath("$.details.maxSlots").exists())
                .andExpect(jsonPath("$.details.acceptedCount").exists())
                .andExpect(jsonPath("$.details.invitedCount").exists());

        SpeakerPool unchanged = speakerPoolRepository.findById(candidate.getId()).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(unchanged.getStatus())
                .isEqualTo(SpeakerWorkflowState.READY);
    }

    /**
     * Story 11.D.1 (AC4): {@code enforceSlotCapacity} counts {@code ACCEPTED + INVITED} —
     * this case saturates with a 50/50 mix so a regression that drops the {@code INVITED}
     * arm from the gate would surface here (the ACCEPTED-only test above would still pass).
     */
    @Test
    @WithMockUser(username = "organizer.test", roles = {"ORGANIZER"})
    void should_return409_when_sendInvitationCalledAndSlotCapacityReachedByMix() throws Exception {
        int half = 12;
        for (int i = 0; i < half; i++) {
            speakerPoolRepository.save(SpeakerPool.builder()
                    .eventId(testEvent.getId())
                    .speakerName("Accepted " + i)
                    .status(SpeakerWorkflowState.ACCEPTED)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());
            speakerPoolRepository.save(SpeakerPool.builder()
                    .eventId(testEvent.getId())
                    .speakerName("Invited " + i)
                    .status(SpeakerWorkflowState.INVITED)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build());
        }

        SpeakerPool candidate = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .speakerName("Mix Saturation Candidate")
                .status(SpeakerWorkflowState.READY)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        candidate = speakerPoolRepository.save(candidate);
        // Story 11.E.9: findByEventIdAndUsername joins via session_users.
        seedPrimarySessionUser(candidate, testUsername);

        LocalDate responseDeadline = LocalDate.now().plusDays(14);

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/{speakerId}/send-invitation",
                        testEventCode, candidate.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "responseDeadline": "%s"
                                }
                                """.formatted(responseDeadline)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code", is("SLOT_CAPACITY_REACHED")))
                .andExpect(jsonPath("$.details.acceptedCount", is(half)))
                .andExpect(jsonPath("$.details.invitedCount", is(half)));
    }
}
