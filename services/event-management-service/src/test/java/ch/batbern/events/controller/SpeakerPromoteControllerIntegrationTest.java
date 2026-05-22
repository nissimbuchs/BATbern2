package ch.batbern.events.controller;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.dto.generated.users.ProvisionUserRequest;
import ch.batbern.events.dto.generated.users.ProvisionUserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@code POST /api/v1/events/{eventCode}/speakers/{speakerId}/promote}
 * (Story 11.D.1, AC1–AC3, AC9 item 1).
 *
 * <p>Covers the contract on the new endpoint: happy path provisions a User + transitions
 * CONTACTED → READY (with a Mockito spy on {@code UserApiClient.provisionUserWithRole}),
 * Bean Validation rejections (missing/malformed email, unknown field), the controller
 * pre-check 409 paths for IDENTIFIED/INVITED/DECLINED, the 409 for same-state READY (per
 * READY, plus 403 (non-organizer) and 404 (speaker not found).
 *
 * <p>Uses real PostgreSQL via Testcontainers per project-context.md (never H2). Mocks
 * {@code UserApiClient} to avoid hitting CUMS in test.
 */
@Transactional
class SpeakerPromoteControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private SpeakerStatusHistoryRepository statusHistoryRepository;

    @MockitoBean
    private UserApiClient userApiClient;

    private static final String EVENT_CODE = "BATbern888";
    private static final String ORGANIZER = "organizer.user";
    private static final String SPEAKER_USERNAME = "jane.smith";
    private static final String SPEAKER_EMAIL = "jane.smith@example.com";

    private Event testEvent;

    @BeforeEach
    void setUp() {
        statusHistoryRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        eventRepository.deleteAll();

        testEvent = eventRepository.save(Event.builder()
                .eventCode(EVENT_CODE)
                .eventNumber(888)
                .title("Promote Endpoint Test Event")
                .date(Instant.now().plus(60, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(50, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .venueCapacity(150)
                .eventType(ch.batbern.events.dto.generated.EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .organizerUsername(ORGANIZER)
                .build());

        when(userApiClient.provisionUserWithRole(any(ProvisionUserRequest.class)))
                .thenAnswer(inv -> new ProvisionUserResponse(SPEAKER_USERNAME, true));
        // Story 11.E.9: PROMOTE_TO_READY responses apply the resolver overlay, which calls
        // UserApiClient.getUserByUsername to populate the live email + name. Stub the
        // response so the overlay returns the expected SPEAKER_EMAIL. The generated
        // UserResponse uses `id` for the username (Story 1.16.2).
        org.mockito.Mockito.lenient().when(userApiClient.getUserByUsername(SPEAKER_USERNAME))
                .thenAnswer(inv -> {
                    ch.batbern.events.dto.generated.users.UserResponse u =
                            new ch.batbern.events.dto.generated.users.UserResponse();
                    u.setId(SPEAKER_USERNAME);
                    u.setEmail(SPEAKER_EMAIL);
                    u.setFirstName("Jane");
                    u.setLastName("Smith");
                    return u;
                });
    }

    // -------- AC1: happy path --------

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("AC1: CONTACTED → READY happy path returns 200 with provisioned identity")
    void should_returnReady_when_promoteCalledOnContactedSpeaker() throws Exception {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.CONTACTED);
        String body = """
                { "email": "%s", "firstName": "Jane", "lastName": "Smith" }
                """.formatted(SPEAKER_EMAIL);

        mockMvc.perform(post("/api/v1/events/{code}/speakers/{id}/promote",
                        EVENT_CODE, speaker.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(speaker.getId().toString())))
                .andExpect(jsonPath("$.status", is("READY")))
                .andExpect(jsonPath("$.username", is(SPEAKER_USERNAME)))
                .andExpect(jsonPath("$.email", is(SPEAKER_EMAIL)));

        // Verify the canonical provisioning call (AC1)
        org.mockito.ArgumentCaptor<ProvisionUserRequest> captor =
                org.mockito.ArgumentCaptor.forClass(ProvisionUserRequest.class);
        verify(userApiClient, times(1)).provisionUserWithRole(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo(SPEAKER_EMAIL);
        assertThat(captor.getValue().getFirstName()).isEqualTo("Jane");
        assertThat(captor.getValue().getLastName()).isEqualTo("Smith");
        assertThat(captor.getValue().getRole()).isEqualTo(ProvisionUserRequest.RoleEnum.SPEAKER);

        SpeakerPool persisted = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(SpeakerWorkflowState.READY);
        // Story 11.E.9: canonical identity moved off speaker_pool. The username now lives
        // on the PRIMARY_SPEAKER session_users row provisioned at CONTACTED → READY; the
        // response-body asserts above already cover that the JSON carries the live values
        // via the overlay.
        assertThat(persisted.getSessionId()).as("session provisioned at READY").isNotNull();

        List<SpeakerStatusHistory> history = statusHistoryRepository
                .findBySpeakerPoolIdOrderByChangedAtDesc(speaker.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getPreviousStatus()).isEqualTo(SpeakerWorkflowState.CONTACTED);
        assertThat(history.get(0).getNewStatus()).isEqualTo(SpeakerWorkflowState.READY);
        assertThat(history.get(0).getChangedByUsername()).isEqualTo(ORGANIZER);
    }

    // -------- AC2: 400 paths --------

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("AC2: empty body returns 400 and does not modify speaker")
    void should_return400_when_emailMissing() throws Exception {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.CONTACTED);

        mockMvc.perform(post("/api/v1/events/{code}/speakers/{id}/promote",
                        EVENT_CODE, speaker.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        assertNoSideEffects(speaker);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("AC2: malformed email returns 400")
    void should_return400_when_emailMalformed() throws Exception {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.CONTACTED);

        mockMvc.perform(post("/api/v1/events/{code}/speakers/{id}/promote",
                        EVENT_CODE, speaker.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "not-an-email", "firstName": "Jane", "lastName": "Smith" }
                                """))
                .andExpect(status().isBadRequest());

        assertNoSideEffects(speaker);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("AC2: unknown field returns 400 (JsonIgnoreProperties strict)")
    void should_return400_when_unknownFieldPresent() throws Exception {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.CONTACTED);

        mockMvc.perform(post("/api/v1/events/{code}/speakers/{id}/promote",
                        EVENT_CODE, speaker.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "firstName": "Jane", "lastName": "Smith", "username": "stale.from.frontend" }
                                """.formatted(SPEAKER_EMAIL)))
                .andExpect(status().isBadRequest());

        assertNoSideEffects(speaker);
    }

    // -------- AC2 / Story 11.E.4 AC4: required-field validation on firstName + lastName --------

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("11.E.4 AC4: missing firstName returns 400")
    void should_return400_when_firstNameMissing() throws Exception {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.CONTACTED);

        mockMvc.perform(post("/api/v1/events/{code}/speakers/{id}/promote",
                        EVENT_CODE, speaker.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "lastName": "Smith" }
                                """.formatted(SPEAKER_EMAIL)))
                .andExpect(status().isBadRequest());

        assertNoSideEffects(speaker);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("11.E.4 AC4: missing lastName returns 400")
    void should_return400_when_lastNameMissing() throws Exception {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.CONTACTED);

        mockMvc.perform(post("/api/v1/events/{code}/speakers/{id}/promote",
                        EVENT_CODE, speaker.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "firstName": "Jane" }
                                """.formatted(SPEAKER_EMAIL)))
                .andExpect(status().isBadRequest());

        assertNoSideEffects(speaker);
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("11.E.4 AC4: blank firstName / lastName returns 400 (whitespace-only)")
    void should_return400_when_firstNameAndLastNameBlank() throws Exception {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.CONTACTED);

        mockMvc.perform(post("/api/v1/events/{code}/speakers/{id}/promote",
                        EVENT_CODE, speaker.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "firstName": "   ", "lastName": "" }
                                """.formatted(SPEAKER_EMAIL)))
                .andExpect(status().isBadRequest());

        assertNoSideEffects(speaker);
    }

    // -------- AC3: 409 pre-check paths --------

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("AC3: speaker already in READY returns 409 (re-promote is not idempotent)")
    void should_return409_when_promoteCalledOnReadySpeaker() throws Exception {
        // Story 11.D.1 Resolved Decision (DN1, 2026-05-16): re-promoting an already-READY
        // speaker is a 409 INVALID_PROMOTION_STATE — not a silent no-op. Same-state would
        // route through handleSameStateTransition which SKIPS the READY side-effect hook,
        // silently dropping the email payload. Rejecting at the controller surface keeps
        // the contract honest.
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.READY);
        speakerPoolRepository.save(speaker);

        mockMvc.perform(post("/api/v1/events/{code}/speakers/{id}/promote",
                        EVENT_CODE, speaker.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "firstName": "Jane", "lastName": "Smith" }
                                """.formatted(SPEAKER_EMAIL)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code", is("INVALID_PROMOTION_STATE")))
                .andExpect(jsonPath("$.details.currentState", is("READY")));

        verify(userApiClient, never()).provisionUserWithRole(any(ProvisionUserRequest.class));

        SpeakerPool persisted = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(SpeakerWorkflowState.READY);
        assertThat(statusHistoryRepository
                .findBySpeakerPoolIdOrderByChangedAtDesc(speaker.getId()))
                .isEmpty();
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("AC3: speaker in INVITED returns 409 with INVALID_PROMOTION_STATE")
    void should_return409_when_speakerInInvited() throws Exception {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.INVITED);
        assertPreCheck409(speaker, "INVITED");
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("AC3: speaker in IDENTIFIED returns 409 (must contact first)")
    void should_return409_when_speakerInIdentified() throws Exception {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.IDENTIFIED);
        assertPreCheck409(speaker, "IDENTIFIED");
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("AC3: speaker in DECLINED returns 409 (terminal state)")
    void should_return409_when_speakerInDeclined() throws Exception {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.DECLINED);
        assertPreCheck409(speaker, "DECLINED");
    }

    // -------- 404 --------

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("404: speaker not found")
    void should_return404_when_speakerNotFound() throws Exception {
        UUID missing = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/events/{code}/speakers/{id}/promote",
                        EVENT_CODE, missing)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "firstName": "Jane", "lastName": "Smith" }
                                """.formatted(SPEAKER_EMAIL)))
                .andExpect(status().isNotFound());

        verify(userApiClient, never()).provisionUserWithRole(any(ProvisionUserRequest.class));
    }

    // -------- 403 --------

    @Test
    @WithMockUser(username = "joe.attendee", roles = {"ATTENDEE"})
    @DisplayName("403: non-ORGANIZER caller is forbidden")
    void should_return403_when_callerIsNotOrganizer() throws Exception {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.CONTACTED);
        mockMvc.perform(post("/api/v1/events/{code}/speakers/{id}/promote",
                        EVENT_CODE, speaker.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "firstName": "Jane", "lastName": "Smith" }
                                """.formatted(SPEAKER_EMAIL)))
                .andExpect(status().isForbidden());

        verify(userApiClient, never()).provisionUserWithRole(any(ProvisionUserRequest.class));
    }

    // -------- helpers --------

    private SpeakerPool createSpeaker(SpeakerWorkflowState status) {
        SpeakerPool speaker = new SpeakerPool();
        speaker.setEventId(testEvent.getId());
        speaker.setSpeakerName("Promote Test Speaker");
        speaker.setCompany("Tech Corp");
        speaker.setExpertise("Architecture");
        speaker.setStatus(status);
        if (status == SpeakerWorkflowState.DECLINED) {
            speaker.setDeclineReason("test");
            speaker.setDeclinedAt(Instant.now());
        }
        return speakerPoolRepository.save(speaker);
    }

    private void assertPreCheck409(SpeakerPool speaker, String expectedStateName) throws Exception {
        mockMvc.perform(post("/api/v1/events/{code}/speakers/{id}/promote",
                        EVENT_CODE, speaker.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "%s", "firstName": "Jane", "lastName": "Smith" }
                                """.formatted(SPEAKER_EMAIL)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code", is("INVALID_PROMOTION_STATE")))
                .andExpect(jsonPath("$.details.currentState", is(expectedStateName)))
                .andExpect(jsonPath("$.message", notNullValue()));

        assertNoSideEffects(speaker);
    }

    private void assertNoSideEffects(SpeakerPool speaker) {
        SpeakerPool persisted = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(speaker.getStatus());
        assertThat(statusHistoryRepository
                .findBySpeakerPoolIdOrderByChangedAtDesc(speaker.getId()))
                .isEmpty();
        verify(userApiClient, never()).provisionUserWithRole(any(ProvisionUserRequest.class));
    }
}
