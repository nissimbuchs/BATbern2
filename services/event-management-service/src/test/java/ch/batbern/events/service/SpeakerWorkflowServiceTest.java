package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.dto.generated.EventSlotConfigurationResponse;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.dto.generated.users.ProvisionUserRequest;
import ch.batbern.events.dto.generated.users.ProvisionUserResponse;
import ch.batbern.events.exception.SlotCapacityReachedException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.events.service.workflow.SecurityPrincipal;
import ch.batbern.events.service.workflow.SpeakerProvisioningHook;
import ch.batbern.events.service.workflow.TransitionPayload;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.events.SpeakerAcceptedEvent;
import ch.batbern.shared.events.SpeakerPromotedToReadyEvent;
import ch.batbern.shared.events.SpeakerWorkflowStateChangeEvent;
import ch.batbern.shared.exception.InvalidStateTransitionException;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SpeakerWorkflowService#transition} — ADR-009 single-writer model.
 *
 * <p>Covers: the allow-list, same-state semantics, preconditions, side-effect hook ordering,
 * and the exception types thrown. Real DB behaviour + full integration coverage lives in
 * {@link SpeakerWorkflowServiceIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class SpeakerWorkflowServiceTest {

    @Mock
    private SpeakerPoolRepository speakerPoolRepository;
    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private SpeakerStatusHistoryRepository statusHistoryRepository;
    @Mock
    private EventTypeService eventTypeService;
    @Mock
    private UserApiClient userApiClient;
    @Mock
    private SpeakerProvisioningHook speakerProvisioningHook;
    @Mock
    private SpeakerInvitationEmailService invitationEmailService;
    @Mock
    private SpeakerAcceptanceEmailService acceptanceEmailService;
    @Mock
    private OrganizerNotificationService organizerNotificationService;
    @Mock
    private MagicLinkService magicLinkService;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private DomainEventPublisher domainEventPublisher;

    private SpeakerWorkflowService service;

    private static final UUID SPEAKER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final SecurityPrincipal ORGANIZER =
            new SecurityPrincipal("organizer.user", List.of("ORGANIZER"));

    @BeforeEach
    void setUp() {
        service = new SpeakerWorkflowService(
                speakerPoolRepository,
                sessionRepository,
                eventRepository,
                statusHistoryRepository,
                eventTypeService,
                userApiClient,
                speakerProvisioningHook,
                invitationEmailService,
                acceptanceEmailService,
                organizerNotificationService,
                magicLinkService,
                applicationEventPublisher,
                domainEventPublisher
        );
    }

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

    static Stream<Arguments> someIllegalPairs() {
        return Stream.of(
                Arguments.of(SpeakerWorkflowState.IDENTIFIED, SpeakerWorkflowState.ACCEPTED),
                Arguments.of(SpeakerWorkflowState.CONTACTED, SpeakerWorkflowState.ACCEPTED),
                Arguments.of(SpeakerWorkflowState.READY, SpeakerWorkflowState.CONTENT_SUBMITTED),
                Arguments.of(SpeakerWorkflowState.INVITED, SpeakerWorkflowState.CONTENT_SUBMITTED),
                Arguments.of(SpeakerWorkflowState.ACCEPTED, SpeakerWorkflowState.QUALITY_REVIEWED),
                Arguments.of(SpeakerWorkflowState.QUALITY_REVIEWED, SpeakerWorkflowState.ACCEPTED),
                Arguments.of(SpeakerWorkflowState.DECLINED, SpeakerWorkflowState.CONTACTED)
        );
    }

    static Stream<Arguments> sameStateNonTerminal() {
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

    @ParameterizedTest(name = "allow {0} -> {1}")
    @MethodSource("legalForwardEdges")
    @DisplayName("Legal forward transitions are allowed")
    void should_allow_legalForwardTransitions(SpeakerWorkflowState from, SpeakerWorkflowState to) {
        SpeakerPool speaker = seedSpeaker(from);
        Event event = seedEvent();
        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));
        lenient().when(speakerPoolRepository.save(any(SpeakerPool.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(statusHistoryRepository.save(any(SpeakerStatusHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        lenient().when(eventTypeService.getEventType(any())).thenReturn(slotConfig(8));
        lenient().when(speakerPoolRepository.countByEventIdAndStatus(any(), any())).thenReturn(0L);
        lenient().when(userApiClient.provisionUserWithRole(any())).thenReturn(stubUser());
        lenient().when(magicLinkService.generateToken(any(), any())).thenReturn("respond-token");
        lenient().when(magicLinkService.generateToken(any(), any(), anyLong())).thenReturn("view-token");

        TransitionPayload payload = TransitionPayload.builder()
                .email("speaker@example.com")
                .firstName("Test")
                .lastName("Speaker")
                .build();

        service.transition(SPEAKER_ID, to, ORGANIZER, payload);

        ArgumentCaptor<SpeakerStatusHistory> historyCaptor = ArgumentCaptor.forClass(SpeakerStatusHistory.class);
        verify(statusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getPreviousStatus()).isEqualTo(from);
        assertThat(historyCaptor.getValue().getNewStatus()).isEqualTo(to);
        assertThat(historyCaptor.getValue().getChangedByUsername()).isEqualTo("organizer.user");
        assertThat(speaker.getStatus()).isEqualTo(to);
    }

    @ParameterizedTest(name = "reject {0} -> {1}")
    @MethodSource("someIllegalPairs")
    @DisplayName("Illegal transitions throw InvalidStateTransitionException")
    void should_throwInvalidStateTransitionException_when_illegalPair(
            SpeakerWorkflowState from, SpeakerWorkflowState to) {
        SpeakerPool speaker = seedSpeaker(from);
        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));

        TransitionPayload payload = TransitionPayload.builder().reason("test").build();
        assertThatThrownBy(() -> service.transition(SPEAKER_ID, to, ORGANIZER, payload))
                .isInstanceOf(InvalidStateTransitionException.class);

        verify(speakerPoolRepository, never()).save(any(SpeakerPool.class));
        verify(statusHistoryRepository, never()).save(any(SpeakerStatusHistory.class));
    }

    @ParameterizedTest(name = "same-state {0}")
    @MethodSource("sameStateNonTerminal")
    @DisplayName("Same-state writes a self-transition history row and skips hooks/events")
    void should_writeSelfTransitionHistoryRow_when_sameStateTransition(SpeakerWorkflowState state) {
        SpeakerPool speaker = seedSpeaker(state);
        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));
        when(statusHistoryRepository.save(any(SpeakerStatusHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TransitionPayload payload = TransitionPayload.builder().reason("re-affirmed").build();
        service.transition(SPEAKER_ID, state, ORGANIZER, payload);

        ArgumentCaptor<SpeakerStatusHistory> historyCaptor = ArgumentCaptor.forClass(SpeakerStatusHistory.class);
        verify(statusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getPreviousStatus()).isEqualTo(state);
        assertThat(historyCaptor.getValue().getNewStatus()).isEqualTo(state);
        assertThat(historyCaptor.getValue().getChangeReason()).isEqualTo("re-affirmed");

        verify(speakerPoolRepository, never()).save(any(SpeakerPool.class));
        verify(speakerProvisioningHook, never()).grantSpeakerRole(any(), any());
        verify(invitationEmailService, never()).sendInvitationEmail(any(), any(), any(), any(), any());
        verify(organizerNotificationService, never()).notifyOrganizerOfResponse(any(), any(), any());

        ArgumentCaptor<SpeakerWorkflowStateChangeEvent> stateChangeCaptor =
                ArgumentCaptor.forClass(SpeakerWorkflowStateChangeEvent.class);
        verify(domainEventPublisher).publish(stateChangeCaptor.capture());
        assertThat(stateChangeCaptor.getValue().getFromState()).isEqualTo(state);
        assertThat(stateChangeCaptor.getValue().getToState()).isEqualTo(state);

        verify(applicationEventPublisher, never()).publishEvent(any(SpeakerPromotedToReadyEvent.class));
        verify(applicationEventPublisher, never()).publishEvent(any(SpeakerAcceptedEvent.class));
    }

    @Test
    @DisplayName("suppressHistoryRow=true skips status_history write but still transitions + publishes event")
    void should_skipStatusHistoryWrite_when_suppressHistoryRowTrue() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.IDENTIFIED);
        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));
        when(speakerPoolRepository.save(any(SpeakerPool.class))).thenAnswer(inv -> inv.getArgument(0));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(seedEvent()));

        TransitionPayload payload = TransitionPayload.builder().suppressHistoryRow(true).build();

        service.transition(SPEAKER_ID, SpeakerWorkflowState.CONTACTED, ORGANIZER, payload);

        // State persisted on the speaker pool entry
        assertThat(speaker.getStatus()).isEqualTo(SpeakerWorkflowState.CONTACTED);
        verify(speakerPoolRepository).save(speaker);

        // No status_history row written — the caller (e.g. SpeakerOutreachService) owns the audit
        verify(statusHistoryRepository, never()).save(any(SpeakerStatusHistory.class));

        // Domain event still published so downstream subscribers see the transition
        verify(domainEventPublisher).publish(any(SpeakerWorkflowStateChangeEvent.class));
    }

    @Test
    @DisplayName("READY precondition: email is required to promote from CONTACTED to READY")
    void should_throwValidationException_when_promotingToReadyWithoutEmail() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.CONTACTED);
        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(seedEvent()));

        TransitionPayload payload = TransitionPayload.builder().build();

        assertThatThrownBy(() -> service.transition(SPEAKER_ID, SpeakerWorkflowState.READY, ORGANIZER, payload))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("email is required");

        verify(speakerPoolRepository, never()).save(any(SpeakerPool.class));
        verify(statusHistoryRepository, never()).save(any(SpeakerStatusHistory.class));
        verify(userApiClient, never()).provisionUserWithRole(any());
    }

    @Test
    @DisplayName("INVITED precondition: slot-capacity gate rejects when accepted+invited >= maxSlots")
    void should_throwSlotCapacityReachedException_when_capacityReached() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.READY);
        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(seedEvent()));
        when(eventTypeService.getEventType(any())).thenReturn(slotConfig(3));
        when(speakerPoolRepository.countByEventIdAndStatus(EVENT_ID, SpeakerWorkflowState.ACCEPTED))
                .thenReturn(2L);
        when(speakerPoolRepository.countByEventIdAndStatus(EVENT_ID, SpeakerWorkflowState.INVITED))
                .thenReturn(1L);

        TransitionPayload payload = TransitionPayload.builder().build();

        assertThatThrownBy(() -> service.transition(SPEAKER_ID, SpeakerWorkflowState.INVITED, ORGANIZER, payload))
                .isInstanceOf(SlotCapacityReachedException.class);

        verify(invitationEmailService, never()).sendInvitationEmail(any(), any(), any(), any(), any());
        verify(speakerPoolRepository, never()).save(any(SpeakerPool.class));
    }

    @Test
    @DisplayName("DECLINED precondition: reason is required for INVITED+ source")
    void should_throwValidationException_when_decliningFromInvitedWithoutReason() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.INVITED);
        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(seedEvent()));

        TransitionPayload payload = TransitionPayload.builder().build();

        assertThatThrownBy(() -> service.transition(SPEAKER_ID, SpeakerWorkflowState.DECLINED, ORGANIZER, payload))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("decline reason is required");
    }

    @Test
    @DisplayName("CONTACTED -> READY runs provisioning hook + publishes SpeakerPromotedToReadyEvent")
    void should_runProvisioningAndPublishPromotedEvent_when_transitioningContactedToReady() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.CONTACTED);
        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));
        when(speakerPoolRepository.save(any(SpeakerPool.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statusHistoryRepository.save(any(SpeakerStatusHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(seedEvent()));
        when(userApiClient.provisionUserWithRole(any())).thenReturn(stubUser());

        TransitionPayload payload = TransitionPayload.builder()
                .email("speaker@example.com")
                .firstName("Test")
                .lastName("Speaker")
                .build();

        InOrder inOrder = inOrder(userApiClient,
                speakerPoolRepository, statusHistoryRepository, applicationEventPublisher);

        service.transition(SPEAKER_ID, SpeakerWorkflowState.READY, ORGANIZER, payload);

        // Story 11.D.1: provisionUserWithRole is the single canonical call (replaces
        // the legacy getOrCreateUser + speakerProvisioningHook.grantSpeakerRole pair).
        ArgumentCaptor<ProvisionUserRequest> provisionCaptor =
                ArgumentCaptor.forClass(ProvisionUserRequest.class);
        inOrder.verify(userApiClient).provisionUserWithRole(provisionCaptor.capture());
        inOrder.verify(speakerPoolRepository).save(any(SpeakerPool.class));
        inOrder.verify(statusHistoryRepository).save(any(SpeakerStatusHistory.class));

        ProvisionUserRequest captured = provisionCaptor.getValue();
        assertThat(captured.getEmail()).isEqualTo("speaker@example.com");
        assertThat(captured.getFirstName()).isEqualTo("Test");
        assertThat(captured.getLastName()).isEqualTo("Speaker");
        assertThat(captured.getRole()).isEqualTo(ProvisionUserRequest.RoleEnum.SPEAKER);

        // The legacy SpeakerProvisioningHook is no longer called from the READY hook —
        // provisioning is now a single call to UserApiClient.provisionUserWithRole.
        verify(speakerProvisioningHook, never()).grantSpeakerRole(any(), any());

        ArgumentCaptor<SpeakerPromotedToReadyEvent> promoted =
                ArgumentCaptor.forClass(SpeakerPromotedToReadyEvent.class);
        verify(applicationEventPublisher).publishEvent(promoted.capture());
        assertThat(promoted.getValue().getUsername()).isEqualTo("speaker.user");
        assertThat(promoted.getValue().getEmail()).isEqualTo("speaker@example.com");
        assertThat(promoted.getValue().getPromotedByUsername()).isEqualTo("organizer.user");

        assertThat(speaker.getUsername()).isEqualTo("speaker.user");
    }

    @Test
    @DisplayName("Story 11.E.4: identity-rebind guard rejects when provisionUserWithRole returns a different username")
    void should_throwValidationException_when_provisionReturnsDifferentUsername() {
        // Deferred from Story 11.D.1 review (item 4 in deferred-work.md):
        // the rebind-guard reject path was not exercised because the existing stubUser()
        // always returned the same username. This test stubs a divergent return and
        // asserts ValidationException is thrown with the documented message naming both
        // usernames.
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.CONTACTED);
        speaker.setUsername("previously.bound.user"); // already bound to a different identity
        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(seedEvent()));
        when(userApiClient.provisionUserWithRole(any()))
                .thenReturn(new ProvisionUserResponse("different.user", false));

        TransitionPayload payload = TransitionPayload.builder()
                .email("speaker@example.com")
                .firstName("Test")
                .lastName("Speaker")
                .build();

        assertThatThrownBy(() ->
                service.transition(SPEAKER_ID, SpeakerWorkflowState.READY, ORGANIZER, payload))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("previously.bound.user")
                .hasMessageContaining("different.user");

        verify(speakerPoolRepository, never()).save(any(SpeakerPool.class));
        verify(statusHistoryRepository, never()).save(any(SpeakerStatusHistory.class));
        verify(applicationEventPublisher, never()).publishEvent(any(SpeakerPromotedToReadyEvent.class));
    }

    @Test
    @DisplayName("INVITED -> ACCEPTED publishes SpeakerAcceptedEvent with acceptedBy = actor.username")
    void should_publishSpeakerAcceptedEvent_when_transitioningInvitedToAccepted() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.INVITED);
        speaker.setSpeakerName("Test Speaker");
        speaker.setCompany("Tech Corp");
        speaker.setExpertise("Architecture");
        Event event = seedEvent();

        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));
        when(speakerPoolRepository.save(any(SpeakerPool.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statusHistoryRepository.save(any(SpeakerStatusHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(magicLinkService.generateToken(eq(SPEAKER_ID), any(), anyLong())).thenReturn("view-token");

        TransitionPayload payload = TransitionPayload.builder().build();
        service.transition(SPEAKER_ID, SpeakerWorkflowState.ACCEPTED, ORGANIZER, payload);

        ArgumentCaptor<SpeakerAcceptedEvent> accepted = ArgumentCaptor.forClass(SpeakerAcceptedEvent.class);
        verify(applicationEventPublisher).publishEvent(accepted.capture());
        assertThat(accepted.getValue().getSpeakerPoolId()).isEqualTo(SPEAKER_ID);
        assertThat(accepted.getValue().getAcceptedBy()).isEqualTo("organizer.user");
        assertThat(speaker.getAcceptedAt()).isNotNull();
    }

    @Test
    @DisplayName("DECLINED from INVITED notifies organizer and clears assigned session")
    void should_notifyOrganizerAndClearSession_when_decliningPostInvitation() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.ACCEPTED);
        UUID sessionId = UUID.randomUUID();
        speaker.setSessionId(sessionId);

        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));
        when(speakerPoolRepository.save(any(SpeakerPool.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statusHistoryRepository.save(any(SpeakerStatusHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(seedEvent()));

        TransitionPayload payload = TransitionPayload.builder().reason("Speaker withdrew").build();
        service.transition(SPEAKER_ID, SpeakerWorkflowState.DECLINED, ORGANIZER, payload);

        verify(organizerNotificationService).notifyOrganizerOfResponse(any(), any(), any());
        verify(sessionRepository).deleteById(sessionId);
        assertThat(speaker.getSessionId()).isNull();
        assertThat(speaker.getDeclinedAt()).isNotNull();
        assertThat(speaker.getDeclineReason()).isEqualTo("Speaker withdrew");
    }

    @Test
    @DisplayName("DECLINED from IDENTIFIED does NOT notify organizer (no real outreach yet)")
    void should_notNotifyOrganizer_when_decliningFromBrainstormState() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.IDENTIFIED);
        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));
        when(speakerPoolRepository.save(any(SpeakerPool.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statusHistoryRepository.save(any(SpeakerStatusHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(seedEvent()));

        TransitionPayload payload = TransitionPayload.builder().build();
        service.transition(SPEAKER_ID, SpeakerWorkflowState.DECLINED, ORGANIZER, payload);

        verify(organizerNotificationService, never()).notifyOrganizerOfResponse(any(), any(), any());
    }

    @Test
    @DisplayName("Speaker pool not found yields NotFoundException")
    void should_throwNotFoundException_when_speakerPoolMissing() {
        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.empty());

        TransitionPayload payload = TransitionPayload.builder().build();
        assertThatThrownBy(() -> service.transition(SPEAKER_ID, SpeakerWorkflowState.CONTACTED, ORGANIZER, payload))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Speaker pool entry not found");
    }

    @Test
    @DisplayName("Publishing failure for SpeakerWorkflowStateChangeEvent does NOT roll back")
    void should_notRollBack_when_eventPublishingFails() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.IDENTIFIED);
        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));
        when(speakerPoolRepository.save(any(SpeakerPool.class))).thenAnswer(inv -> inv.getArgument(0));
        when(statusHistoryRepository.save(any(SpeakerStatusHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(seedEvent()));

        org.mockito.Mockito.doThrow(new RuntimeException("EventBridge unavailable"))
                .when(domainEventPublisher).publish(any(SpeakerWorkflowStateChangeEvent.class));

        TransitionPayload payload = TransitionPayload.builder().build();
        // Should NOT throw — the warning is logged and the transaction continues.
        service.transition(SPEAKER_ID, SpeakerWorkflowState.CONTACTED, ORGANIZER, payload);

        verify(speakerPoolRepository).save(any(SpeakerPool.class));
        verify(statusHistoryRepository).save(any(SpeakerStatusHistory.class));
    }

    // ------ helpers ------

    private SpeakerPool seedSpeaker(SpeakerWorkflowState state) {
        SpeakerPool speaker = new SpeakerPool();
        speaker.setId(SPEAKER_ID);
        speaker.setEventId(EVENT_ID);
        speaker.setStatus(state);
        speaker.setSpeakerName("Existing Name");
        speaker.setEmail("existing@example.com");
        // Story 11.E.2 invariant: speakers in READY+ already have a canonical username
        // (set by the CONTACTED → READY hook). Pre-READY states leave it null so the
        // provisioning hook test can assert the post-transition value.
        if (state != SpeakerWorkflowState.IDENTIFIED
                && state != SpeakerWorkflowState.CONTACTED) {
            speaker.setUsername("speaker.user");
        }
        return speaker;
    }

    private Event seedEvent() {
        Event event = new Event();
        event.setId(EVENT_ID);
        event.setEventCode("BATbern99");
        event.setTitle("Test Event");
        event.setEventType(EventType.EVENING);
        return event;
    }

    private EventSlotConfigurationResponse slotConfig(int max) {
        EventSlotConfigurationResponse cfg = new EventSlotConfigurationResponse();
        cfg.setMinSlots(1);
        cfg.setMaxSlots(max);
        return cfg;
    }

    private ProvisionUserResponse stubUser() {
        ProvisionUserResponse resp = new ProvisionUserResponse("speaker.user", true);
        return resp;
    }

    private static long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }
}
