package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.dto.SpeakerResponseRequest;
import ch.batbern.events.dto.SpeakerResponseResult;
import ch.batbern.events.exception.AlreadyRespondedException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.workflow.SecurityPrincipal;
import ch.batbern.events.service.workflow.TransitionPayload;
import ch.batbern.events.service.workflow.TransitionResult;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.types.SpeakerResponseType;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SpeakerResponseService} — Story 11.B.2 + 11.E.3 (ADR-009).
 *
 * <p>Verifies that ACCEPT and DECLINE responses delegate to
 * {@link SpeakerWorkflowService#transition} (the sole writer) and that already-responded
 * speakers are blocked. Story 11.E.3 swaps the magic-link path for Cognito-derived
 * {@link SecurityPrincipal}s injected by the controller; the service no longer consults
 * {@code MagicLinkService}.
 *
 * <p>Provisioning (User + SPEAKER role grant) is upstream at {@code CONTACTED → READY}
 * and is verified in {@link SpeakerWorkflowServiceTest}, not here.
 */
@ExtendWith(MockitoExtension.class)
class SpeakerResponseServiceTest {

    @Mock
    private SpeakerPoolRepository speakerPoolRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SpeakerWorkflowService speakerWorkflowService;

    private SpeakerResponseService service;

    private static final UUID SPEAKER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final SecurityPrincipal SPEAKER_PRINCIPAL =
            new SecurityPrincipal("speaker.user", List.of("SPEAKER"));

    @BeforeEach
    void setUp() {
        service = new SpeakerResponseService(
                speakerPoolRepository,
                eventRepository,
                eventPublisher,
                speakerWorkflowService);
    }

    @Test
    @DisplayName("ACCEPT delegates to SpeakerWorkflowService.transition() with Cognito actor")
    void should_delegateToTransition_when_acceptResponseSubmitted() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.INVITED);
        speaker.setUsername("speaker.user");
        Event event = seedEvent();

        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(speakerWorkflowService.transition(
                eq(SPEAKER_ID), eq(SpeakerWorkflowState.ACCEPTED),
                any(SecurityPrincipal.class), any(TransitionPayload.class)))
                .thenAnswer(inv -> {
                    speaker.setStatus(SpeakerWorkflowState.ACCEPTED);
                    return new TransitionResult(speaker, new SpeakerStatusHistory());
                });

        SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                .response(SpeakerResponseType.ACCEPT)
                .build();

        SpeakerResponseResult result =
                service.processResponse(SPEAKER_PRINCIPAL, speaker, request);

        ArgumentCaptor<SecurityPrincipal> actorCaptor =
                ArgumentCaptor.forClass(SecurityPrincipal.class);
        verify(speakerWorkflowService).transition(
                eq(SPEAKER_ID), eq(SpeakerWorkflowState.ACCEPTED),
                actorCaptor.capture(), any(TransitionPayload.class));
        assertThat(actorCaptor.getValue().username()).isEqualTo("speaker.user");
        assertThat(actorCaptor.getValue().roles()).containsExactly("SPEAKER");

        assertThat(result.isSuccess()).isTrue();
        // Story 11.E.3: profile URL is now a token-less SPA route keyed by eventCode.
        assertThat(result.getProfileUrl()).isEqualTo("/speaker-portal/profile/BATbern99");
    }

    @Test
    @DisplayName("DECLINE delegates to transition() with reason payload")
    void should_delegateToTransition_when_declineResponseSubmitted() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.INVITED);
        speaker.setUsername("speaker.user");
        Event event = seedEvent();

        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(speakerWorkflowService.transition(
                eq(SPEAKER_ID), eq(SpeakerWorkflowState.DECLINED),
                any(SecurityPrincipal.class), any(TransitionPayload.class)))
                .thenAnswer(inv -> {
                    speaker.setStatus(SpeakerWorkflowState.DECLINED);
                    return new TransitionResult(speaker, new SpeakerStatusHistory());
                });

        SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                .response(SpeakerResponseType.DECLINE)
                .reason("Scheduling conflict")
                .build();

        service.processResponse(SPEAKER_PRINCIPAL, speaker, request);

        ArgumentCaptor<TransitionPayload> payloadCaptor =
                ArgumentCaptor.forClass(TransitionPayload.class);
        verify(speakerWorkflowService).transition(
                eq(SPEAKER_ID), eq(SpeakerWorkflowState.DECLINED),
                any(SecurityPrincipal.class), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().reason()).isEqualTo("Scheduling conflict");
    }

    @Test
    @DisplayName("DECLINE without reason throws ValidationException before delegating")
    void should_throwValidationException_when_declineMissingReason() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.INVITED);
        Event event = seedEvent();
        lenient().when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                .response(SpeakerResponseType.DECLINE)
                .build();

        assertThatThrownBy(() -> service.processResponse(SPEAKER_PRINCIPAL, speaker, request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Reason is required");

        verify(speakerWorkflowService, never()).transition(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Already-ACCEPTED speaker is blocked from responding again")
    void should_throwAlreadyRespondedException_when_speakerAlreadyAccepted() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.ACCEPTED);

        SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                .response(SpeakerResponseType.ACCEPT)
                .build();

        assertThatThrownBy(() -> service.processResponse(SPEAKER_PRINCIPAL, speaker, request))
                .isInstanceOf(AlreadyRespondedException.class);

        verify(speakerWorkflowService, never()).transition(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Already-DECLINED speaker is blocked from responding again")
    void should_throwAlreadyRespondedException_when_speakerAlreadyDeclined() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.DECLINED);

        SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                .response(SpeakerResponseType.ACCEPT)
                .build();

        assertThatThrownBy(() -> service.processResponse(SPEAKER_PRINCIPAL, speaker, request))
                .isInstanceOf(AlreadyRespondedException.class);
    }

    // ------ helpers ------

    private SpeakerPool seedSpeaker(SpeakerWorkflowState state) {
        SpeakerPool speaker = new SpeakerPool();
        speaker.setId(SPEAKER_ID);
        speaker.setEventId(EVENT_ID);
        speaker.setStatus(state);
        speaker.setSpeakerName("Test Speaker");
        speaker.setEmail("test@example.com");
        return speaker;
    }

    private Event seedEvent() {
        Event event = new Event();
        event.setId(EVENT_ID);
        event.setEventCode("BATbern99");
        event.setTitle("Test Event");
        return event;
    }
}
