package ch.batbern.events.controller;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.generated.users.ProvisionUserRequest;
import ch.batbern.events.dto.generated.users.ProvisionUserResponse;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.shared.events.SpeakerAddedToPoolEvent;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for attendee speaker self-nomination —
 * {@code POST /api/v1/events/{eventCode}/speakers/self-nominate} (Story 7.2 "I Could Speak on That").
 *
 * <p>Covers AC1–AC6, AC8: create-at-IDENTIFIED with source tagging + profile-auto-filled name,
 * the no-provisioning guarantee, the topic/published guard (409), the one-per-event rule (409),
 * auth gating (401 anonymous / 403 wrong role), and that the self-nominated row flows through the
 * unchanged triage → promote path (IDENTIFIED → CONTACTED → READY via existing endpoints).
 *
 * <p>PostgreSQL via Testcontainers for production parity. {@code UserApiClient} is mocked to avoid
 * hitting CUMS — the profile lookup (auto-fill) and the READY-path provisioning both go through it.
 */
@Transactional
@Import(SelfNominationIntegrationTest.EventCaptorConfig.class)
class SelfNominationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SpeakerAddedEventCaptor speakerAddedEventCaptor;

    /**
     * A synchronous test-scoped {@code @EventListener} that records every {@link SpeakerAddedToPoolEvent}
     * on the publishing (test) thread. This is the same {@code @EventListener} mechanism the production
     * {@code SpeakerAddedToPoolEventListener} uses, so it fires deterministically at publish time —
     * unlike that listener it is NOT {@code @Async}, so there is no thread/transaction race to assert on.
     */
    @TestConfiguration
    static class EventCaptorConfig {
        @Bean
        SpeakerAddedEventCaptor speakerAddedEventCaptor() {
            return new SpeakerAddedEventCaptor();
        }
    }

    static class SpeakerAddedEventCaptor {
        final List<SpeakerAddedToPoolEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        public void on(SpeakerAddedToPoolEvent event) {
            events.add(event);
        }
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private SpeakerStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private ch.batbern.events.repository.SessionProposalRepository sessionProposalRepository;

    @Autowired
    private ch.batbern.events.repository.SessionRepository sessionRepository;

    @Autowired
    private ch.batbern.events.repository.SessionContentHistoryRepository sessionContentHistoryRepository;

    @Autowired
    private ch.batbern.events.service.SpeakerPoolService speakerPoolService;

    @MockitoBean
    private UserApiClient userApiClient;

    private static final String EVENT_CODE = "BATbern950";
    private static final String ATTENDEE = "jane.attendee";
    private static final String ORGANIZER = "org.user";

    private static final String BODY = """
            { "sessionTitle": "Event-driven architecture in practice",
              "abstract": "A field report on migrating a monolith to an event-driven core." }
            """;

    @BeforeEach
    void setUp() {
        statusHistoryRepository.deleteAll();
        sessionProposalRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        eventRepository.deleteAll();
        speakerAddedEventCaptor.events.clear();

        // Auto-fill source: the attendee's profile (Resolved Decision #1). companyId is the
        // company slug stored on the pool row's `company` column.
        UserResponse profile = new UserResponse();
        profile.setId(ATTENDEE);
        profile.setFirstName("Jane");
        profile.setLastName("Attendee");
        profile.setCompanyId("AcmeAG");
        lenient().when(userApiClient.getUserByUsername(ATTENDEE)).thenReturn(profile);
        lenient().when(userApiClient.getCompanyDisplayName("AcmeAG")).thenReturn("Acme AG");
    }

    // ==================== AC1, AC2, AC3, AC8: happy path ====================

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("AC1-3,8: creates pool row at IDENTIFIED, source self_nomination, name auto-filled, no provisioning")
    void should_create_at_IDENTIFIED_with_source_and_autofilled_identity() throws Exception {
        Event event = saveEvent(EVENT_CODE, "cloud-native", /* publishedAt */ true, "topic");

        String json = mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/self-nominate", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("IDENTIFIED")))
                .andExpect(jsonPath("$.source", is("self_nomination")))
                .andExpect(jsonPath("$.proposedByUsername", is(ATTENDEE)))
                // Name auto-filled from profile (NOT the username), company from profile slug.
                .andExpect(jsonPath("$.speakerName", is("Jane Attendee")))
                .andExpect(jsonPath("$.company", is("AcmeAG")))
                .andExpect(jsonPath("$.proposedSessionTitle", is("Event-driven architecture in practice")))
                .andExpect(jsonPath("$.proposedAbstract", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        String speakerId = objectMapper.readTree(json).get("id").asText();
        SpeakerPool persisted = speakerPoolRepository.findById(java.util.UUID.fromString(speakerId)).orElseThrow();

        // No provisioning: no session, no status history (transition() was never used to create),
        // and the Cognito/User provisioning call never happened.
        assertThat(persisted.getStatus()).isEqualTo(SpeakerWorkflowState.IDENTIFIED);
        assertThat(persisted.getSessionId()).as("no session for an IDENTIFIED self-nomination").isNull();
        assertThat(persisted.getEventId()).isEqualTo(event.getId());
        assertThat(statusHistoryRepository.findBySpeakerPoolIdOrderByChangedAtDesc(persisted.getId()))
                .as("no status-history row — the row was created at the IDENTIFIED default, not via transition()")
                .isEmpty();
        verify(userApiClient, never()).provisionUserWithRole(any(ProvisionUserRequest.class));

        // ADR-012: the proposed talk lives in session_proposals (NOT on speaker_pool). The pool
        // row carries only the provenance flag.
        assertThat(persisted.getSource()).isEqualTo("self_nomination");
        var proposal = sessionProposalRepository.findBySpeakerPoolId(persisted.getId()).orElseThrow();
        assertThat(proposal.getProposedByUsername()).isEqualTo(ATTENDEE);
        assertThat(proposal.getProposedTitle()).isEqualTo("Event-driven architecture in practice");
        assertThat(proposal.getProposedAbstract()).isNotBlank();
        assertThat(proposal.getEventId()).isEqualTo(event.getId());
    }

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("Auto-fill falls back to the username when CUMS has no profile")
    void should_fallBackToUsername_when_profileLookupFails() throws Exception {
        saveEvent(EVENT_CODE, "cloud-native", true, "topic");
        when(userApiClient.getUserByUsername(ATTENDEE))
                .thenThrow(new ch.batbern.events.exception.UserServiceException("CUMS down"));

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/self-nominate", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.speakerName", is(ATTENDEE)));
    }

    // ==================== AC4: topic/published guard ====================

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("AC4: topic not set → 409 SELF_NOMINATION_NOT_ALLOWED, no row created")
    void should_reject_when_topicNotSet() throws Exception {
        saveEvent(EVENT_CODE, /* topicCode */ null, true, "topic");

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/self-nominate", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code", is("SELF_NOMINATION_NOT_ALLOWED")));

        assertThat(speakerPoolRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("AC4: topic set but unpublished → 409, no row created")
    void should_reject_when_notPublished() throws Exception {
        saveEvent(EVENT_CODE, "cloud-native", /* publishedAt */ false, "none");

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/self-nominate", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code", is("SELF_NOMINATION_NOT_ALLOWED")));

        assertThat(speakerPoolRepository.findAll()).isEmpty();
    }

    // ==================== AC8: one per event ====================

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("AC8: a second self-nomination for the same event → 409 DUPLICATE_SELF_NOMINATION")
    void should_reject_when_duplicate() throws Exception {
        saveEvent(EVENT_CODE, "cloud-native", true, "topic");

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/self-nominate", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/self-nominate", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.code", is("DUPLICATE_SELF_NOMINATION")));

        assertThat(speakerPoolRepository.findAll()).hasSize(1);
    }

    // ==================== AC5: auth gating ====================

    @Test
    @DisplayName("AC5: unauthenticated caller is rejected (login-gated)")
    void should_reject_anonymous() throws Exception {
        saveEvent(EVENT_CODE, "cloud-native", true, "topic");

        // AC5: the endpoint is login-gated. In this service in isolation, method security
        // rejects the unauthenticated/anonymous principal with 403 (TestSecurityConfig has no
        // JWT resource-server, so there is no 401 entry point). The 401 from AC5 is produced
        // at the api-gateway, the real public entry point, where a request with no JWT never
        // reaches this service. Either way the call is rejected and no row is created.
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/self-nominate", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        assertThat(speakerPoolRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = ORGANIZER, roles = {"ORGANIZER"})
    @DisplayName("AC5: wrong role (ORGANIZER) → 403")
    void should_reject_nonAttendeeRole() throws Exception {
        saveEvent(EVENT_CODE, "cloud-native", true, "topic");

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/self-nominate", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    // ==================== Request validation ====================

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("Unknown body field (e.g. speakerName) → 400")
    void should_reject_when_unknownField() throws Exception {
        saveEvent(EVENT_CODE, "cloud-native", true, "topic");

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/self-nominate", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "sessionTitle": "x", "abstract": "y", "speakerName": "Spoofed Name" }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(speakerPoolRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("Missing sessionTitle → 400")
    void should_reject_when_sessionTitleMissing() throws Exception {
        saveEvent(EVENT_CODE, "cloud-native", true, "topic");

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/self-nominate", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"abstract\": \"only an abstract\" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("AC4: event not found → 404")
    void should_return404_when_eventMissing() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/self-nominate", "BATbern999")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }

    // ==================== AC6: promote path still works from a self-nomination ====================

    @Test
    @DisplayName("AC6: self-nominated row flows IDENTIFIED → CONTACTED → READY via existing endpoints")
    void should_allowPromotePath_from_selfNomination() throws Exception {
        saveEvent(EVENT_CODE, "cloud-native", true, "topic");

        // 1) Attendee self-nominates (IDENTIFIED).
        String json = mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/self-nominate", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user(ATTENDEE).roles("ATTENDEE")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String speakerId = objectMapper.readTree(json).get("id").asText();

        // 2) Organizer triages it forward IDENTIFIED → CONTACTED via the existing status endpoint.
        mockMvc.perform(put("/api/v1/events/{eventCode}/speakers/{speakerId}/status", EVENT_CODE, speakerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"newStatus\": \"CONTACTED\" }")
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user(ORGANIZER).roles("ORGANIZER")))
                .andExpect(status().isOk());

        // 3) Organizer promotes CONTACTED → READY — provisioning happens here, only here.
        when(userApiClient.provisionUserWithRole(any(ProvisionUserRequest.class)))
                .thenAnswer(inv -> new ProvisionUserResponse(ATTENDEE, true));
        lenient().when(userApiClient.getUserByUsername(ATTENDEE)).thenAnswer(inv -> {
            UserResponse u = new UserResponse();
            u.setId(ATTENDEE);
            u.setEmail("jane.attendee@example.com");
            u.setFirstName("Jane");
            u.setLastName("Attendee");
            return u;
        });

        mockMvc.perform(post("/api/v1/events/{eventCode}/speakers/{speakerId}/promote", EVENT_CODE, speakerId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "email": "jane.attendee@example.com", "firstName": "Jane", "lastName": "Attendee" }
                                """)
                        .with(org.springframework.security.test.web.servlet.request
                                .SecurityMockMvcRequestPostProcessors.user(ORGANIZER).roles("ORGANIZER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("READY")));

        verify(userApiClient).provisionUserWithRole(any(ProvisionUserRequest.class));

        // AC-PROMOTE-2 / ADR-012: the pitch was carried into the canonical session — the session
        // title is the proposed title (not a placeholder), and the abstract seeded the first
        // content-history row so the self-nominee never re-enters it.
        SpeakerPool promoted = speakerPoolRepository.findById(java.util.UUID.fromString(speakerId)).orElseThrow();
        assertThat(promoted.getSessionId()).as("session provisioned at READY").isNotNull();
        var session = sessionRepository.findById(promoted.getSessionId()).orElseThrow();
        assertThat(session.getTitle()).isEqualTo("Event-driven architecture in practice");
        var contentVersion = sessionContentHistoryRepository
                .findFirstBySessionIdOrderBySubmissionVersionDesc(session.getId()).orElseThrow();
        assertThat(contentVersion.getContentAbstract())
                .isEqualTo("A field report on migrating a monolith to an event-driven core.");
        assertThat(contentVersion.getSubmissionVersion()).isEqualTo(1);
    }

    // ==================== AC6: workflow auto-transition is intentionally driven by self-nom ====================

    @Test
    @WithMockUser(username = ATTENDEE, roles = {"ATTENDEE"})
    @DisplayName("Self-nomination from TOPIC_SELECTION publishes SpeakerAddedToPoolEvent (drives the auto-transition)")
    void should_publishSpeakerAddedToPoolEvent_advancingWorkflow_from_topicSelection() throws Exception {
        // A topic-published event that has NOT yet left TOPIC_SELECTION (publishing the topic phase
        // does not change workflowState) — exactly the self-nomination-eligible state. The first
        // self-nomination reuses the organizer add-to-pool path, which publishes SpeakerAddedToPoolEvent;
        // the existing @Async SpeakerAddedToPoolEventListener advances CREATED/TOPIC_SELECTION →
        // SPEAKER_IDENTIFICATION. This coupling is intentional (review decision 2026-06-10): a speaker
        // entering the pool advances the event regardless of source. We assert the load-bearing event
        // is published (deterministic, captured by a synchronous test @EventListener), rather than
        // racing the async listener thread. The service is invoked directly so the publish happens on
        // the test thread (the @WithMockUser SecurityContext supplies the username).
        saveEvent(EVENT_CODE, "cloud-native", true, "topic", EventWorkflowState.TOPIC_SELECTION);

        speakerPoolService.selfNominate(EVENT_CODE, new ch.batbern.events.dto.SelfNominateSpeakerRequest(
                "Event-driven architecture in practice",
                "A field report on migrating a monolith to an event-driven core."));

        // Sanity: selfNominate actually ran (row created at IDENTIFIED).
        assertThat(speakerPoolRepository.count()).as("self-nomination created one pool row").isEqualTo(1);

        // The captor is cleared per-test, so exactly one SpeakerAddedToPoolEvent — for this event —
        // proves the self-nomination feeds the same auto-transition mechanism the organizer add-path
        // uses (the @Async listener advances TOPIC_SELECTION → SPEAKER_IDENTIFICATION).
        assertThat(speakerAddedEventCaptor.events)
                .as("self-nomination must publish the SpeakerAddedToPoolEvent that drives the "
                        + "TOPIC_SELECTION → SPEAKER_IDENTIFICATION auto-transition")
                .singleElement()
                .extracting(SpeakerAddedToPoolEvent::getEventCode)
                .isEqualTo(EVENT_CODE);
    }

    // ==================== Helper ====================

    private Event saveEvent(String eventCode, String topicCode, boolean published, String phase) {
        return saveEvent(eventCode, topicCode, published, phase, EventWorkflowState.SPEAKER_IDENTIFICATION);
    }

    private Event saveEvent(String eventCode, String topicCode, boolean published, String phase,
                            EventWorkflowState workflowState) {
        Event event = new Event();
        event.setEventCode(eventCode);
        event.setEventNumber(950);
        event.setTitle("Self-Nomination Test Event");
        event.setDate(Instant.now().plus(60, ChronoUnit.DAYS));
        event.setRegistrationDeadline(Instant.now().plus(50, ChronoUnit.DAYS));
        event.setVenueName("Test Venue");
        event.setVenueAddress("Test Address");
        event.setVenueCapacity(150);
        event.setOrganizerUsername(ORGANIZER);
        event.setEventType(ch.batbern.events.dto.generated.EventType.EVENING);
        event.setWorkflowState(workflowState);
        event.setTopicCode(topicCode);
        event.setCurrentPublishedPhase(phase);
        if (published) {
            event.setPublishedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        }
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        event.setCreatedBy(ORGANIZER);
        event.setUpdatedBy(ORGANIZER);
        return eventRepository.save(event);
    }
}
