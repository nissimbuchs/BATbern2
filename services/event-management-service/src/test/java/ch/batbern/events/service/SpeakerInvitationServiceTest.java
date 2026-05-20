package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.OutreachHistory;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.dto.InviteSpeakerRequest;
import ch.batbern.events.dto.InviteSpeakerResponse;
import ch.batbern.events.dto.SendInvitationRequest;
import ch.batbern.events.dto.SendInvitationResponse;
import ch.batbern.events.dto.generated.users.GetOrCreateUserResponse;
import ch.batbern.events.exception.EventNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.OutreachHistoryRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.workflow.TransitionPayload;
import ch.batbern.events.service.workflow.TransitionResult;
import ch.batbern.shared.events.SpeakerInvitationSentEvent;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SpeakerInvitationService} — Story 11.B.2 (ADR-009).
 *
 * <p>Verifies that {@code sendInvitation} delegates to {@link SpeakerWorkflowService#transition}
 * (the sole writer), and that the pool-entry creation flow (inviteSpeaker) is unchanged.
 * The status-history write, magic-link token generation, and invitation-email send are now
 * inside {@code transition()}'s INVITED hook — see {@link SpeakerWorkflowServiceTest} and
 * {@link SpeakerWorkflowServiceIntegrationTest} for those guarantees.
 */
@ExtendWith(MockitoExtension.class)
class SpeakerInvitationServiceTest {

    @Mock
    private SpeakerPoolRepository speakerPoolRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserApiClient userApiClient;
    @Mock
    private SecurityContextHelper securityContextHelper;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private OutreachHistoryRepository outreachHistoryRepository;
    @Mock
    private SpeakerWorkflowService speakerWorkflowService;

    @InjectMocks
    private SpeakerInvitationService speakerInvitationService;

    private Event testEvent;
    private final String testEventCode = "batbern-2026-spring";
    private final String testEmail = "speaker@example.com";
    private final String testUsername = "speaker.test";
    private final UUID testEventId = UUID.randomUUID();
    private final UUID testSpeakerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        testEvent = Event.builder()
                .id(testEventId)
                .eventCode(testEventCode)
                .eventNumber(56)
                .title("BATbern Spring 2026")
                .date(Instant.now().plus(60, ChronoUnit.DAYS))
                .build();

        lenient().when(outreachHistoryRepository.save(any(OutreachHistory.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    // -------- inviteSpeaker (pool-entry creation; unchanged) --------

    @Test
    @DisplayName("inviteSpeaker creates pool entry at IDENTIFIED and looks up/creates user")
    void should_createPoolEntryAtIdentified_when_inviteSpeakerCalled() {
        InviteSpeakerRequest request = new InviteSpeakerRequest(
                testEmail, "Test", "Speaker", "TestCorp", null, null);

        when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.of(testEvent));
        when(speakerPoolRepository.findByEventIdAndEmail(testEventId, testEmail))
                .thenReturn(Optional.empty());
        GetOrCreateUserResponse userResp = new GetOrCreateUserResponse();
        userResp.setUsername(testUsername);
        userResp.setCreated(true);
        when(userApiClient.getOrCreateUser(any())).thenReturn(userResp);
        when(speakerPoolRepository.save(any(SpeakerPool.class))).thenAnswer(inv -> {
            SpeakerPool sp = inv.getArgument(0);
            sp.setId(testSpeakerId);
            sp.setCreatedAt(Instant.now());
            return sp;
        });

        InviteSpeakerResponse response = speakerInvitationService.inviteSpeaker(testEventCode, request);

        assertThat(response.email()).isEqualTo(testEmail);
        assertThat(response.username()).isEqualTo(testUsername);

        ArgumentCaptor<SpeakerPool> poolCaptor = ArgumentCaptor.forClass(SpeakerPool.class);
        verify(speakerPoolRepository).save(poolCaptor.capture());
        assertThat(poolCaptor.getValue().getStatus()).isEqualTo(SpeakerWorkflowState.IDENTIFIED);
    }

    @Test
    @DisplayName("inviteSpeaker throws EventNotFoundException when event code is unknown")
    void should_throwEventNotFoundException_when_eventMissing() {
        when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.empty());

        InviteSpeakerRequest request = new InviteSpeakerRequest(
                testEmail, "Test", "Speaker", "TestCorp", null, null);

        assertThatThrownBy(() -> speakerInvitationService.inviteSpeaker(testEventCode, request))
                .isInstanceOf(EventNotFoundException.class);
        verify(speakerPoolRepository, never()).save(any(SpeakerPool.class));
    }

    // -------- sendInvitation delegates to SpeakerWorkflowService.transition() --------

    @Test
    @DisplayName("sendInvitation delegates to SpeakerWorkflowService.transition(INVITED, ...)")
    void should_delegateToTransition_when_sendingInvitation() {
        SpeakerPool speaker = SpeakerPool.builder()
                .id(testSpeakerId)
                .eventId(testEventId)
                .username(testUsername)
                .email(testEmail)
                .speakerName("Test Speaker")
                .status(SpeakerWorkflowState.READY)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(eventRepository.findByEventCode(testEventCode)).thenReturn(Optional.of(testEvent));
        when(speakerPoolRepository.findByEventIdAndUsername(testEventId, testUsername))
                .thenReturn(Optional.of(speaker));
        when(securityContextHelper.getCurrentUsername()).thenReturn("organizer.test");
        when(speakerWorkflowService.transition(
                eq(testSpeakerId), eq(SpeakerWorkflowState.INVITED),
                anyString(), any(TransitionPayload.class)))
                .thenAnswer(inv -> {
                    speaker.setStatus(SpeakerWorkflowState.INVITED);
                    speaker.setInvitedAt(Instant.now());
                    return new TransitionResult(speaker, new SpeakerStatusHistory());
                });
        when(speakerPoolRepository.findById(testSpeakerId)).thenReturn(Optional.of(speaker));

        LocalDate deadline = LocalDate.now().plusDays(14);
        SendInvitationRequest request = new SendInvitationRequest(deadline, null, "de", null);

        SendInvitationResponse response = speakerInvitationService.sendInvitation(
                testEventCode, testUsername, request);

        assertThat(response.status()).isEqualTo(SpeakerWorkflowState.INVITED);
        assertThat(response.invitedAt()).isNotNull();

        ArgumentCaptor<String> actor = ArgumentCaptor.forClass(String.class);
        verify(speakerWorkflowService).transition(
                eq(testSpeakerId), eq(SpeakerWorkflowState.INVITED),
                actor.capture(), any(TransitionPayload.class));
        assertThat(actor.getValue()).isEqualTo("organizer.test");

        // Outreach history is still recorded by SpeakerInvitationService (orthogonal to state).
        verify(outreachHistoryRepository).save(any(OutreachHistory.class));

        // SpeakerInvitationSentEvent is still published by SpeakerInvitationService (separate
        // from SpeakerWorkflowStateChangeEvent which transition() publishes).
        verify(eventPublisher).publishEvent(any(SpeakerInvitationSentEvent.class));
    }
}
