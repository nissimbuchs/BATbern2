package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.dto.SpeakerResponseRequest;
import ch.batbern.events.dto.SpeakerResponseResult;
import ch.batbern.events.dto.TokenValidationResult;
import ch.batbern.events.exception.AlreadyRespondedException;
import ch.batbern.events.exception.InvalidTokenException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.workflow.SecurityPrincipal;
import ch.batbern.events.service.workflow.TransitionPayload;
import ch.batbern.events.service.workflow.TransitionResult;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.types.SpeakerResponseType;
import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.shared.types.TokenAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SpeakerResponseService} — Story 11.B.2 (ADR-009).
 *
 * <p>Verifies that ACCEPT and DECLINE responses delegate to
 * {@link SpeakerWorkflowService#transition} (the sole writer), that magic-link
 * tokens are consumed, and that already-responded speakers are blocked.
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
    private MagicLinkService magicLinkService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private SpeakerWorkflowService speakerWorkflowService;

    private SpeakerResponseService service;

    private static final UUID SPEAKER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final String TOKEN = "magic-link-token";

    @BeforeEach
    void setUp() {
        service = new SpeakerResponseService(
                speakerPoolRepository,
                eventRepository,
                magicLinkService,
                eventPublisher,
                speakerWorkflowService);
        ReflectionTestUtils.setField(service, "appBaseUrl", "http://localhost:8100");
    }

    @Test
    @DisplayName("ACCEPT delegates to SpeakerWorkflowService.transition() and marks token used")
    void should_delegateToTransition_when_acceptResponseSubmitted() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.INVITED);
        speaker.setUsername("speaker.user");
        Event event = seedEvent();

        when(magicLinkService.validateToken(TOKEN)).thenReturn(validTokenFor(SPEAKER_ID));
        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(speakerWorkflowService.transition(
                eq(SPEAKER_ID), eq(SpeakerWorkflowState.ACCEPTED),
                any(SecurityPrincipal.class), any(TransitionPayload.class)))
                .thenAnswer(inv -> {
                    speaker.setStatus(SpeakerWorkflowState.ACCEPTED);
                    return new TransitionResult(speaker, new SpeakerStatusHistory());
                });
        when(magicLinkService.generateToken(eq(SPEAKER_ID), eq(TokenAction.VIEW), anyLong()))
                .thenReturn("profile-view-token");

        SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                .token(TOKEN)
                .response(SpeakerResponseType.ACCEPT)
                .build();

        SpeakerResponseResult result = service.processResponse(request);

        ArgumentCaptor<SecurityPrincipal> actor = ArgumentCaptor.forClass(SecurityPrincipal.class);
        verify(speakerWorkflowService).transition(
                eq(SPEAKER_ID), eq(SpeakerWorkflowState.ACCEPTED),
                actor.capture(), any(TransitionPayload.class));
        assertThat(actor.getValue().username()).isEqualTo("speaker.user");
        assertThat(actor.getValue().roles()).containsExactly("SPEAKER");

        verify(magicLinkService).markTokenAsUsed(TOKEN);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProfileUrl()).isNotNull();
    }

    @Test
    @DisplayName("ACCEPT falls back to speakerName when username is null")
    void should_fallBackToSpeakerName_when_usernameIsNull() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.INVITED);
        speaker.setUsername(null);
        speaker.setSpeakerName("Jane Doe");
        Event event = seedEvent();

        when(magicLinkService.validateToken(TOKEN)).thenReturn(validTokenFor(SPEAKER_ID));
        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
        when(speakerWorkflowService.transition(
                eq(SPEAKER_ID), eq(SpeakerWorkflowState.ACCEPTED),
                any(SecurityPrincipal.class), any(TransitionPayload.class)))
                .thenAnswer(inv -> {
                    speaker.setStatus(SpeakerWorkflowState.ACCEPTED);
                    return new TransitionResult(speaker, new SpeakerStatusHistory());
                });
        lenient().when(magicLinkService.generateToken(eq(SPEAKER_ID), eq(TokenAction.VIEW), anyLong()))
                .thenReturn("profile-view-token");

        SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                .token(TOKEN)
                .response(SpeakerResponseType.ACCEPT)
                .build();

        service.processResponse(request);

        ArgumentCaptor<SecurityPrincipal> actor = ArgumentCaptor.forClass(SecurityPrincipal.class);
        verify(speakerWorkflowService).transition(
                eq(SPEAKER_ID), eq(SpeakerWorkflowState.ACCEPTED),
                actor.capture(), any(TransitionPayload.class));
        assertThat(actor.getValue().username()).isEqualTo("Jane Doe");
    }

    @Test
    @DisplayName("DECLINE delegates to transition() with reason payload and marks token used")
    void should_delegateToTransition_when_declineResponseSubmitted() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.INVITED);
        speaker.setUsername("speaker.user");
        Event event = seedEvent();

        when(magicLinkService.validateToken(TOKEN)).thenReturn(validTokenFor(SPEAKER_ID));
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
                .token(TOKEN)
                .response(SpeakerResponseType.DECLINE)
                .reason("Scheduling conflict")
                .build();

        service.processResponse(request);

        ArgumentCaptor<TransitionPayload> payloadCaptor = ArgumentCaptor.forClass(TransitionPayload.class);
        verify(speakerWorkflowService).transition(
                eq(SPEAKER_ID), eq(SpeakerWorkflowState.DECLINED),
                any(SecurityPrincipal.class), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().reason()).isEqualTo("Scheduling conflict");

        verify(magicLinkService).markTokenAsUsed(TOKEN);
    }

    @Test
    @DisplayName("DECLINE without reason throws ValidationException before delegating")
    void should_throwValidationException_when_declineMissingReason() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.INVITED);
        Event event = seedEvent();

        when(magicLinkService.validateToken(TOKEN)).thenReturn(validTokenFor(SPEAKER_ID));
        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));
        lenient().when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));

        SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                .token(TOKEN)
                .response(SpeakerResponseType.DECLINE)
                .build();

        assertThatThrownBy(() -> service.processResponse(request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Reason is required");

        verify(speakerWorkflowService, never()).transition(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Already-ACCEPTED speaker is blocked from responding again")
    void should_throwAlreadyRespondedException_when_speakerAlreadyAccepted() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.ACCEPTED);

        when(magicLinkService.validateToken(TOKEN)).thenReturn(validTokenFor(SPEAKER_ID));
        when(speakerPoolRepository.findById(SPEAKER_ID)).thenReturn(Optional.of(speaker));

        SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                .token(TOKEN)
                .response(SpeakerResponseType.ACCEPT)
                .build();

        assertThatThrownBy(() -> service.processResponse(request))
                .isInstanceOf(AlreadyRespondedException.class);

        verify(speakerWorkflowService, never()).transition(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Invalid token throws InvalidTokenException with NOT_FOUND")
    void should_throwInvalidTokenException_when_tokenInvalid() {
        when(magicLinkService.validateToken(TOKEN)).thenReturn(TokenValidationResult.notFound());

        SpeakerResponseRequest request = SpeakerResponseRequest.builder()
                .token(TOKEN)
                .response(SpeakerResponseType.ACCEPT)
                .build();

        assertThatThrownBy(() -> service.processResponse(request))
                .isInstanceOf(InvalidTokenException.class);
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

    private TokenValidationResult validTokenFor(UUID speakerPoolId) {
        return TokenValidationResult.valid(speakerPoolId, "speaker.user", "BATbern99", TokenAction.RESPOND);
    }
}
