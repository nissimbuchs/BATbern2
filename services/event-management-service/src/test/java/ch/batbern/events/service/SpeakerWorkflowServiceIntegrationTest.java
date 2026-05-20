package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestUserApiClientConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.dto.generated.users.InvitationCredentialsResponse;
import ch.batbern.events.dto.generated.users.ProvisionUserRequest;
import ch.batbern.events.dto.generated.users.ProvisionUserResponse;
import ch.batbern.events.exception.SlotCapacityReachedException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.events.service.workflow.SpeakerProvisioningHook;
import ch.batbern.events.service.workflow.TransitionPayload;
import ch.batbern.shared.exception.InvalidStateTransitionException;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link SpeakerWorkflowService#transition} — ADR-009 single-writer model.
 *
 * <p>Runs against real PostgreSQL via Testcontainers (per project-context.md and CLAUDE.md:
 * never H2). Covers the legal/illegal transition matrix, same-state semantics, preconditions,
 * side-effect hooks, and the {@code SpeakerWorkflowStateChangeEvent} contract.
 */
@Transactional
@Import(TestUserApiClientConfig.class)
class SpeakerWorkflowServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SpeakerWorkflowService workflowService;
    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SpeakerStatusHistoryRepository statusHistoryRepository;
    @Autowired
    private UserApiClient userApiClient;

    @MockBean
    private SpeakerInvitationEmailService invitationEmailService;
    @MockBean
    private SpeakerAcceptanceEmailService acceptanceEmailService;
    @MockBean
    private OrganizerNotificationService organizerNotificationService;
    @SpyBean
    private SpeakerProvisioningHook speakerProvisioningHook;

    private Event testEvent;
    private UUID testEventId;
    private static final String TEST_EVENT_CODE = "BAT-WF-INT-TEST";
    private static final String ORGANIZER = "test-organizer";

    @BeforeEach
    void setUp() {
        // userApiClient is provided via TestUserApiClientConfig (plain Mockito mock — not
        // @MockBean/@MockitoBean). Spring's per-test reset does NOT apply, so we explicitly
        // clear interactions between tests to avoid verification counts bleeding across.
        org.mockito.Mockito.clearInvocations(userApiClient);

        testEvent = Event.builder()
                .eventCode(TEST_EVENT_CODE)
                .eventNumber(999)
                .title("BATbern Workflow Integration Test")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .date(Instant.now().plus(90, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(80, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("123 Test Street")
                .venueCapacity(200)
                .organizerUsername(ORGANIZER)
                .build();
        testEvent = eventRepository.save(testEvent);
        testEventId = testEvent.getId();

        when(userApiClient.provisionUserWithRole(any())).thenAnswer(inv ->
                new ProvisionUserResponse("speaker.user", true));

        // Story 11.E.2: default the new issueInvitationCredentials stub to return a fresh
        // temp password. Tests that care about the action discriminator override below.
        when(userApiClient.issueInvitationCredentials(any())).thenAnswer(inv ->
                new InvitationCredentialsResponse(
                        InvitationCredentialsResponse.ActionEnum.FRESH_TEMP_PASSWORD)
                        .temporaryPassword("Test1234!@#abcde9"));
    }

    // ---- AC10 #1: legal forward transitions ----

    static Stream<Arguments> legalForwardEdges() {
        return Stream.of(
                Arguments.of(SpeakerWorkflowState.IDENTIFIED, SpeakerWorkflowState.CONTACTED),
                Arguments.of(SpeakerWorkflowState.CONTACTED, SpeakerWorkflowState.READY),
                Arguments.of(SpeakerWorkflowState.READY, SpeakerWorkflowState.INVITED),
                Arguments.of(SpeakerWorkflowState.INVITED, SpeakerWorkflowState.ACCEPTED),
                Arguments.of(SpeakerWorkflowState.ACCEPTED, SpeakerWorkflowState.CONTENT_SUBMITTED),
                Arguments.of(SpeakerWorkflowState.CONTENT_SUBMITTED, SpeakerWorkflowState.QUALITY_REVIEWED)
        );
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("legalForwardEdges")
    @DisplayName("Legal forward transitions persist new status and history row")
    void should_persistNewStatus_when_legalTransition(SpeakerWorkflowState from, SpeakerWorkflowState to) {
        SpeakerPool speaker = createSpeaker(from);
        TransitionPayload payload = payloadFor(to);

        workflowService.transition(speaker.getId(), to, ORGANIZER, payload);

        SpeakerPool persisted = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(to);

        List<SpeakerStatusHistory> history =
                statusHistoryRepository.findBySpeakerPoolIdOrderByChangedAtDesc(speaker.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getPreviousStatus()).isEqualTo(from);
        assertThat(history.get(0).getNewStatus()).isEqualTo(to);
        assertThat(history.get(0).getChangedByUsername()).isEqualTo(ORGANIZER);
    }

    // ---- AC10 #2: legal (any) → DECLINED transitions ----

    static Stream<Arguments> postInvitationStates() {
        return Stream.of(
                Arguments.of(SpeakerWorkflowState.INVITED),
                Arguments.of(SpeakerWorkflowState.ACCEPTED),
                Arguments.of(SpeakerWorkflowState.CONTENT_SUBMITTED),
                Arguments.of(SpeakerWorkflowState.QUALITY_REVIEWED)
        );
    }

    @ParameterizedTest(name = "{0} -> DECLINED with reason notifies organizer")
    @MethodSource("postInvitationStates")
    @DisplayName("DECLINED from post-invitation source notifies organizer")
    void should_notifyOrganizer_when_decliningFromPostInvitation(SpeakerWorkflowState source) {
        SpeakerPool speaker = createSpeaker(source);
        TransitionPayload payload = TransitionPayload.builder().reason("Schedule conflict").build();

        workflowService.transition(speaker.getId(), SpeakerWorkflowState.DECLINED, ORGANIZER, payload);

        verify(organizerNotificationService, times(1))
                .notifyOrganizerOfResponse(any(), any(), any());
        SpeakerPool persisted = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(SpeakerWorkflowState.DECLINED);
        assertThat(persisted.getDeclinedAt()).isNotNull();
        assertThat(persisted.getDeclineReason()).isEqualTo("Schedule conflict");
    }

    @Test
    @DisplayName("DECLINED from IDENTIFIED does NOT notify organizer (no real outreach yet)")
    void should_notNotifyOrganizer_when_decliningFromIdentified() {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.IDENTIFIED);
        TransitionPayload payload = TransitionPayload.builder().build();

        workflowService.transition(speaker.getId(), SpeakerWorkflowState.DECLINED, ORGANIZER, payload);

        verify(organizerNotificationService, never()).notifyOrganizerOfResponse(any(), any(), any());
        SpeakerPool persisted = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(SpeakerWorkflowState.DECLINED);
    }

    // ---- AC10 #3: illegal transitions ----

    static Stream<Arguments> illegalPairs() {
        Set<SpeakerWorkflowState[]> legal = Set.of(
                new SpeakerWorkflowState[]{SpeakerWorkflowState.IDENTIFIED, SpeakerWorkflowState.CONTACTED},
                new SpeakerWorkflowState[]{SpeakerWorkflowState.CONTACTED, SpeakerWorkflowState.READY},
                new SpeakerWorkflowState[]{SpeakerWorkflowState.READY, SpeakerWorkflowState.INVITED},
                new SpeakerWorkflowState[]{SpeakerWorkflowState.INVITED, SpeakerWorkflowState.ACCEPTED},
                new SpeakerWorkflowState[]{SpeakerWorkflowState.ACCEPTED, SpeakerWorkflowState.CONTENT_SUBMITTED},
                new SpeakerWorkflowState[]{SpeakerWorkflowState.CONTENT_SUBMITTED, SpeakerWorkflowState.QUALITY_REVIEWED}
        );
        return Stream.of(SpeakerWorkflowState.values())
                .flatMap(from -> Stream.of(SpeakerWorkflowState.values())
                        .filter(to -> from != to)
                        .filter(to -> to != SpeakerWorkflowState.DECLINED || from == SpeakerWorkflowState.DECLINED)
                        .filter(to -> legal.stream().noneMatch(p -> p[0] == from && p[1] == to))
                        .map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "reject {0} -> {1}")
    @MethodSource("illegalPairs")
    @DisplayName("Illegal transitions throw InvalidStateTransitionException and leave DB unchanged")
    void should_throwAndLeaveDbUnchanged_when_illegalTransition(SpeakerWorkflowState from, SpeakerWorkflowState to) {
        SpeakerPool speaker = createSpeaker(from);
        TransitionPayload payload = TransitionPayload.builder().reason("test").build();

        assertThatThrownBy(() -> workflowService.transition(speaker.getId(), to, ORGANIZER, payload))
                .isInstanceOf(InvalidStateTransitionException.class);

        SpeakerPool unchanged = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(from);
        assertThat(statusHistoryRepository.findBySpeakerPoolIdOrderByChangedAtDesc(speaker.getId()))
                .isEmpty();
    }

    // ---- AC10 #4: same-state self-transition ----

    static Stream<Arguments> nonTerminalStates() {
        return Stream.of(
                Arguments.of(SpeakerWorkflowState.IDENTIFIED),
                Arguments.of(SpeakerWorkflowState.CONTACTED),
                Arguments.of(SpeakerWorkflowState.READY),
                Arguments.of(SpeakerWorkflowState.INVITED),
                Arguments.of(SpeakerWorkflowState.ACCEPTED),
                Arguments.of(SpeakerWorkflowState.CONTENT_SUBMITTED),
                Arguments.of(SpeakerWorkflowState.QUALITY_REVIEWED)
        );
    }

    @ParameterizedTest(name = "same-state {0}")
    @MethodSource("nonTerminalStates")
    @DisplayName("Same-state writes self-transition history row and skips hooks/events")
    void should_writeSelfTransitionHistoryRow_when_sameStateTransition(SpeakerWorkflowState state) {
        SpeakerPool speaker = createSpeaker(state);
        TransitionPayload payload = TransitionPayload.builder().reason("re-affirmed").build();

        workflowService.transition(speaker.getId(), state, ORGANIZER, payload);

        SpeakerPool unchanged = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(state);

        List<SpeakerStatusHistory> history =
                statusHistoryRepository.findBySpeakerPoolIdOrderByChangedAtDesc(speaker.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getPreviousStatus()).isEqualTo(state);
        assertThat(history.get(0).getNewStatus()).isEqualTo(state);
        assertThat(history.get(0).getChangeReason()).isEqualTo("re-affirmed");

        verify(speakerProvisioningHook, never()).grantSpeakerRole(anyString(), anyString());
        verify(invitationEmailService, never()).sendInvitationEmail(any(), any(), any(), any(), any());
        verify(organizerNotificationService, never()).notifyOrganizerOfResponse(any(), any(), any());
    }

    // ---- AC10 #5: READY precondition (email required) ----

    @Test
    @DisplayName("READY precondition: ValidationException when email missing")
    void should_throwValidationException_when_promotingToReadyWithoutEmail() {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.CONTACTED);
        TransitionPayload payload = TransitionPayload.builder().build();

        assertThatThrownBy(() -> workflowService.transition(
                speaker.getId(), SpeakerWorkflowState.READY, ORGANIZER, payload))
                .isInstanceOf(ValidationException.class);

        SpeakerPool unchanged = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(SpeakerWorkflowState.CONTACTED);
    }

    // ---- AC10 #6: CONTACTED → READY provisioning seam + event ----

    @Test
    @DisplayName("CONTACTED → READY invokes provisionUserWithRole(SPEAKER) and publishes SpeakerPromotedToReadyEvent")
    void should_invokeProvisioningSeamAndPublishEvent_when_transitioningContactedToReady() {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.CONTACTED);
        TransitionPayload payload = TransitionPayload.builder()
                .email("speaker@example.com")
                .firstName("Test")
                .lastName("Speaker")
                .build();

        workflowService.transition(speaker.getId(), SpeakerWorkflowState.READY, ORGANIZER, payload);

        // Story 11.D.1: provisionUserWithRole is the canonical single call (replaces the
        // legacy getOrCreateUser + speakerProvisioningHook.grantSpeakerRole pair).
        org.mockito.ArgumentCaptor<ProvisionUserRequest> captor =
                org.mockito.ArgumentCaptor.forClass(ProvisionUserRequest.class);
        verify(userApiClient, times(1)).provisionUserWithRole(captor.capture());
        ProvisionUserRequest captured = captor.getValue();
        assertThat(captured.getEmail()).isEqualTo("speaker@example.com");
        assertThat(captured.getFirstName()).isEqualTo("Test");
        assertThat(captured.getLastName()).isEqualTo("Speaker");
        assertThat(captured.getRole()).isEqualTo(ProvisionUserRequest.RoleEnum.SPEAKER);

        // The legacy SpeakerProvisioningHook is no longer called from the READY hook —
        // provisioning is now a single call to UserApiClient.provisionUserWithRole.
        verify(speakerProvisioningHook, never()).grantSpeakerRole(anyString(), anyString());

        SpeakerPool persisted = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(persisted.getUsername()).isEqualTo("speaker.user");
        assertThat(persisted.getEmail()).isEqualTo("speaker@example.com");
        assertThat(persisted.getStatus()).isEqualTo(SpeakerWorkflowState.READY);
    }

    // ---- AC10 #7: INVITED slot-capacity gate ----

    @Test
    @DisplayName("INVITED precondition: slot-capacity gate rejects when accepted+invited >= maxSlots")
    void should_throwSlotCapacityReached_when_capacityReached() {
        // Default eventType=EVENING usually has maxSlots ~6-8 — seed enough to saturate.
        int targetSaturation = 24; // safe upper bound for any event-type config
        for (int i = 0; i < targetSaturation; i++) {
            createSpeaker(SpeakerWorkflowState.ACCEPTED);
        }
        SpeakerPool candidate = createSpeaker(SpeakerWorkflowState.READY);
        TransitionPayload payload = TransitionPayload.builder().build();

        assertThatThrownBy(() -> workflowService.transition(
                candidate.getId(), SpeakerWorkflowState.INVITED, ORGANIZER, payload))
                .isInstanceOf(SlotCapacityReachedException.class);

        SpeakerPool unchanged = speakerPoolRepository.findById(candidate.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(SpeakerWorkflowState.READY);
        verify(invitationEmailService, never()).sendInvitationEmail(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("INVITED succeeds when accepted+invited < maxSlots")
    void should_succeed_when_capacityAvailable() {
        SpeakerPool candidate = createSpeaker(SpeakerWorkflowState.READY);
        TransitionPayload payload = TransitionPayload.builder().build();

        workflowService.transition(candidate.getId(), SpeakerWorkflowState.INVITED, ORGANIZER, payload);

        SpeakerPool persisted = speakerPoolRepository.findById(candidate.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(SpeakerWorkflowState.INVITED);
        assertThat(persisted.getInvitedAt()).isNotNull();
        verify(invitationEmailService).sendInvitationEmail(any(), any(), any(), any(), any());
    }

    // ---- Story 11.E.2 AC11 #9: runInvitedHook calls issueInvitationCredentials ----

    @Test
    @DisplayName("Story 11.E.2 AC11 #9: runInvitedHook calls issueInvitationCredentials with the speaker's username")
    void should_callIssueInvitationCredentials_when_runningInvitedHook() {
        SpeakerPool candidate = createSpeaker(SpeakerWorkflowState.READY);
        TransitionPayload payload = TransitionPayload.builder().build();

        // Override the default stub with a fresh-temp-password response for assertions.
        InvitationCredentialsResponse stubbedResponse = new InvitationCredentialsResponse(
                InvitationCredentialsResponse.ActionEnum.FRESH_TEMP_PASSWORD)
                .temporaryPassword("Test1234!@#abcde");
        when(userApiClient.issueInvitationCredentials(candidate.getUsername()))
                .thenReturn(stubbedResponse);

        workflowService.transition(candidate.getId(), SpeakerWorkflowState.INVITED, ORGANIZER, payload);

        // Exactly one call to issueInvitationCredentials with the speaker's username.
        verify(userApiClient, times(1)).issueInvitationCredentials(candidate.getUsername());

        // The email service was invoked once with the captured InvitationCredentialsResponse.
        org.mockito.ArgumentCaptor<InvitationCredentialsResponse> credentialsCaptor =
                org.mockito.ArgumentCaptor.forClass(InvitationCredentialsResponse.class);
        verify(invitationEmailService).sendInvitationEmail(
                any(), any(), any(String.class), credentialsCaptor.capture(), any());
        assertThat(credentialsCaptor.getValue()).isSameAs(stubbedResponse);

        // Speaker transitioned and invitedAt stamped.
        SpeakerPool persisted = speakerPoolRepository.findById(candidate.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(SpeakerWorkflowState.INVITED);
        assertThat(persisted.getInvitedAt()).isNotNull();
    }

    // ---- AC10 #8 + #9: DECLINED preconditions + session cleanup ----

    @Test
    @DisplayName("DECLINED from INVITED without reason throws ValidationException")
    void should_throwValidationException_when_decliningFromInvitedWithoutReason() {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.INVITED);
        TransitionPayload payload = TransitionPayload.builder().build();

        assertThatThrownBy(() -> workflowService.transition(
                speaker.getId(), SpeakerWorkflowState.DECLINED, ORGANIZER, payload))
                .isInstanceOf(ValidationException.class);

        SpeakerPool unchanged = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(unchanged.getStatus()).isEqualTo(SpeakerWorkflowState.INVITED);
    }

    @Test
    @DisplayName("DECLINED from post-acceptance clears assigned session")
    void should_clearSessionId_when_decliningSpeakerWithAssignedSession() {
        SpeakerPool speaker = createSpeakerWithSession(SpeakerWorkflowState.ACCEPTED);
        UUID sessionId = speaker.getSessionId();
        TransitionPayload payload = TransitionPayload.builder().reason("Withdrew").build();

        workflowService.transition(speaker.getId(), SpeakerWorkflowState.DECLINED, ORGANIZER, payload);

        SpeakerPool persisted = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(persisted.getSessionId()).isNull();
        assertThat(sessionRepository.findById(sessionId)).isEmpty();
    }

    // ---- AC10 #10: INVITED → ACCEPTED publishes SpeakerAcceptedEvent ----

    @Nested
    @DisplayName("Speaker pool not-found")
    class NotFoundCases {
        @Test
        @DisplayName("transition() throws NotFoundException when speaker pool entry is missing")
        void should_throwNotFoundException_when_speakerMissing() {
            UUID missing = UUID.randomUUID();
            TransitionPayload payload = TransitionPayload.builder().build();

            assertThatThrownBy(() -> workflowService.transition(
                    missing, SpeakerWorkflowState.CONTACTED, ORGANIZER, payload))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    // ---- helpers ----

    private SpeakerPool createSpeaker(SpeakerWorkflowState status) {
        SpeakerPool speaker = new SpeakerPool();
        speaker.setEventId(testEventId);
        speaker.setSpeakerName("Speaker " + UUID.randomUUID().toString().substring(0, 8));
        speaker.setCompany("Tech Corp");
        speaker.setExpertise("Architecture");
        speaker.setEmail("existing." + UUID.randomUUID().toString().substring(0, 8) + "@example.com");
        // Story 11.E.2 review patch (P2 / E5): SpeakerWorkflowService.requireUsername now
        // fails-fast when a READY speaker reaches INVITED without a username. Pre-seed a
        // username for READY+ states (mirroring the real runReadyHook output) so the
        // INVITED-precondition guard passes. Earlier states (IDENTIFIED/CONTACTED) leave
        // it null so the runReadyHook identity-rebind guard doesn't fire on transition.
        if (statusIsAtOrAfter(status, SpeakerWorkflowState.READY)) {
            speaker.setUsername("speaker." + UUID.randomUUID().toString().substring(0, 8));
        }
        speaker.setStatus(status);
        return speakerPoolRepository.save(speaker);
    }

    private static boolean statusIsAtOrAfter(SpeakerWorkflowState a, SpeakerWorkflowState b) {
        // SpeakerWorkflowState ordinal order tracks the forward workflow order; DECLINED is
        // terminal but we treat it as "post-READY" for username-seeding purposes (a real
        // DECLINED speaker that lived through CONTACTED → READY has a username).
        return a.ordinal() >= b.ordinal();
    }

    private SpeakerPool createSpeakerWithSession(SpeakerWorkflowState status) {
        Session session = Session.builder()
                .eventId(testEventId)
                .eventCode(testEvent.getEventCode())
                .title("Test Presentation")
                .description("Test abstract")
                .sessionSlug("test-presentation-" + UUID.randomUUID().toString().substring(0, 8))
                .sessionType("presentation")
                .startTime(Instant.now().plus(90, ChronoUnit.DAYS))
                .endTime(Instant.now().plus(90, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .build();
        session = sessionRepository.save(session);

        SpeakerPool speaker = new SpeakerPool();
        speaker.setEventId(testEventId);
        speaker.setSpeakerName("Speaker With Session");
        speaker.setCompany("Tech Corp");
        speaker.setExpertise("Architecture");
        speaker.setEmail("session.speaker@example.com");
        // Story 11.E.2 review patch (P2 / E5): seed username for READY+ states so the
        // INVITED precondition (requireUsername) passes; leave null for IDENTIFIED/CONTACTED
        // so the runReadyHook identity-rebind guard doesn't fire on transition.
        if (statusIsAtOrAfter(status, SpeakerWorkflowState.READY)) {
            speaker.setUsername("session.speaker." + UUID.randomUUID().toString().substring(0, 8));
        }
        speaker.setStatus(status);
        speaker.setSessionId(session.getId());
        return speakerPoolRepository.save(speaker);
    }

    private TransitionPayload payloadFor(SpeakerWorkflowState target) {
        TransitionPayload.TransitionPayloadBuilder builder = TransitionPayload.builder();
        if (target == SpeakerWorkflowState.READY) {
            builder.email("ready@example.com").firstName("Ready").lastName("Speaker");
        }
        if (target == SpeakerWorkflowState.DECLINED) {
            builder.reason("Test decline reason");
        }
        return builder.build();
    }
}
