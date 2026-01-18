package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.InvitationStatus;
import ch.batbern.events.domain.ResponseType;
import ch.batbern.events.domain.Speaker;
import ch.batbern.events.domain.SpeakerInvitation;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.InvitationResponse;
import ch.batbern.events.dto.RespondToInvitationRequest;
import ch.batbern.events.dto.SendInvitationRequest;
import ch.batbern.events.event.InvitationRespondedEvent;
import ch.batbern.events.event.SpeakerInvitedEvent;
import ch.batbern.events.exception.InvitationExpiredException;
import ch.batbern.events.exception.InvitationNotFoundException;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerInvitationRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.util.InvitationTokenGenerator;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InvitationService - Story 6.1.
 *
 * Tests service layer logic for invitation creation, response handling,
 * workflow state updates, and domain event publishing.
 */
@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private SpeakerInvitationRepository invitationRepository;

    @Mock
    private SpeakerPoolRepository speakerPoolRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SpeakerService speakerService;

    @Mock
    private InvitationTokenGenerator tokenGenerator;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private InvitationEmailService invitationEmailService;

    @InjectMocks
    private InvitationService invitationService;

    @Captor
    private ArgumentCaptor<SpeakerInvitation> invitationCaptor;

    @Captor
    private ArgumentCaptor<SpeakerInvitedEvent> speakerInvitedEventCaptor;

    @Captor
    private ArgumentCaptor<InvitationRespondedEvent> invitationRespondedEventCaptor;

    private static final String TEST_TOKEN = "abcd1234567890123456789012345678901234567890123456789012345678ef";
    private static final String TEST_USERNAME = "john.doe";
    private static final String TEST_EVENT_CODE = "BAT-2026-Q1";
    private static final String TEST_ORGANIZER = "organizer.smith";

    @Nested
    class SendInvitation {

        @Test
        void should_sendInvitation_when_validRequestProvided() {
            // Given
            SendInvitationRequest request = SendInvitationRequest.builder()
                    .username(TEST_USERNAME)
                    .eventCode(TEST_EVENT_CODE)
                    .expirationDays(14)
                    .build();

            Speaker speaker = Speaker.builder()
                    .username(TEST_USERNAME)
                    .workflowState(SpeakerWorkflowState.IDENTIFIED)
                    .build();

            Event event = Event.builder()
                    .id(UUID.randomUUID())
                    .eventCode(TEST_EVENT_CODE)
                    .build();

            SpeakerPool speakerPool = SpeakerPool.builder()
                    .id(UUID.randomUUID())
                    .eventId(event.getId())
                    .username(TEST_USERNAME)
                    .email("john.doe@example.com")
                    .build();

            when(speakerService.getSpeakerEntityByUsername(TEST_USERNAME)).thenReturn(speaker);
            when(eventRepository.findByEventCode(TEST_EVENT_CODE)).thenReturn(Optional.of(event));
            when(speakerPoolRepository.findByEventIdAndUsername(event.getId(), TEST_USERNAME))
                    .thenReturn(Optional.of(speakerPool));
            when(tokenGenerator.generateToken()).thenReturn(TEST_TOKEN);
            when(invitationRepository.existsActiveInvitation(TEST_USERNAME, TEST_EVENT_CODE))
                    .thenReturn(false);
            when(invitationRepository.save(any(SpeakerInvitation.class))).thenAnswer(inv -> {
                SpeakerInvitation saved = inv.getArgument(0);
                saved.setId(UUID.randomUUID());
                return saved;
            });

            // When
            InvitationResponse response = invitationService.sendInvitation(request, TEST_ORGANIZER);

            // Then
            assertThat(response).isNotNull();
            assertThat(response.getUsername()).isEqualTo(TEST_USERNAME);
            assertThat(response.getEventCode()).isEqualTo(TEST_EVENT_CODE);
            assertThat(response.getInvitationStatus()).isEqualTo(InvitationStatus.SENT);

            verify(invitationRepository).save(invitationCaptor.capture());
            SpeakerInvitation saved = invitationCaptor.getValue();
            assertThat(saved.getResponseToken()).isEqualTo(TEST_TOKEN);
            assertThat(saved.getCreatedBy()).isEqualTo(TEST_ORGANIZER);
        }

        @Test
        void should_updateSpeakerWorkflowState_when_invitationSent() {
            // Given
            SendInvitationRequest request = SendInvitationRequest.builder()
                    .username(TEST_USERNAME)
                    .eventCode(TEST_EVENT_CODE)
                    .build();

            Speaker speaker = Speaker.builder()
                    .username(TEST_USERNAME)
                    .workflowState(SpeakerWorkflowState.IDENTIFIED)
                    .build();

            Event event = Event.builder()
                    .id(UUID.randomUUID())
                    .eventCode(TEST_EVENT_CODE)
                    .build();

            SpeakerPool speakerPool = SpeakerPool.builder()
                    .id(UUID.randomUUID())
                    .eventId(event.getId())
                    .username(TEST_USERNAME)
                    .email("john.doe@example.com")
                    .build();

            when(speakerService.getSpeakerEntityByUsername(TEST_USERNAME)).thenReturn(speaker);
            when(eventRepository.findByEventCode(TEST_EVENT_CODE)).thenReturn(Optional.of(event));
            when(speakerPoolRepository.findByEventIdAndUsername(event.getId(), TEST_USERNAME))
                    .thenReturn(Optional.of(speakerPool));
            when(tokenGenerator.generateToken()).thenReturn(TEST_TOKEN);
            when(invitationRepository.existsActiveInvitation(anyString(), anyString())).thenReturn(false);
            when(invitationRepository.save(any())).thenAnswer(inv -> {
                SpeakerInvitation s = inv.getArgument(0);
                s.setId(UUID.randomUUID());
                return s;
            });

            // When
            invitationService.sendInvitation(request, TEST_ORGANIZER);

            // Then - Speaker workflow state should be updated to CONTACTED
            assertThat(speaker.getWorkflowState()).isEqualTo(SpeakerWorkflowState.CONTACTED);
        }

        @Test
        void should_publishSpeakerInvitedEvent_when_invitationSent() {
            // Given
            SendInvitationRequest request = SendInvitationRequest.builder()
                    .username(TEST_USERNAME)
                    .eventCode(TEST_EVENT_CODE)
                    .build();

            Speaker speaker = Speaker.builder()
                    .username(TEST_USERNAME)
                    .workflowState(SpeakerWorkflowState.IDENTIFIED)
                    .build();

            Event event = Event.builder()
                    .id(UUID.randomUUID())
                    .eventCode(TEST_EVENT_CODE)
                    .build();

            SpeakerPool speakerPool = SpeakerPool.builder()
                    .id(UUID.randomUUID())
                    .eventId(event.getId())
                    .username(TEST_USERNAME)
                    .email("john.doe@example.com")
                    .build();

            when(speakerService.getSpeakerEntityByUsername(TEST_USERNAME)).thenReturn(speaker);
            when(eventRepository.findByEventCode(TEST_EVENT_CODE)).thenReturn(Optional.of(event));
            when(speakerPoolRepository.findByEventIdAndUsername(event.getId(), TEST_USERNAME))
                    .thenReturn(Optional.of(speakerPool));
            when(tokenGenerator.generateToken()).thenReturn(TEST_TOKEN);
            when(invitationRepository.existsActiveInvitation(anyString(), anyString())).thenReturn(false);
            when(invitationRepository.save(any())).thenAnswer(inv -> {
                SpeakerInvitation s = inv.getArgument(0);
                s.setId(UUID.randomUUID());
                return s;
            });

            // When
            invitationService.sendInvitation(request, TEST_ORGANIZER);

            // Then
            verify(domainEventPublisher).publish(speakerInvitedEventCaptor.capture());
            SpeakerInvitedEvent publishedEvent = speakerInvitedEventCaptor.getValue();
            assertThat(publishedEvent.getUsername()).isEqualTo(TEST_USERNAME);
            assertThat(publishedEvent.getEventCode()).isEqualTo(TEST_EVENT_CODE);
        }

        @Test
        void should_rejectDuplicateInvitation_when_activeInvitationExists() {
            // Given
            SendInvitationRequest request = SendInvitationRequest.builder()
                    .username(TEST_USERNAME)
                    .eventCode(TEST_EVENT_CODE)
                    .build();

            Speaker speaker = Speaker.builder().username(TEST_USERNAME).build();
            Event event = Event.builder()
                    .id(UUID.randomUUID())
                    .eventCode(TEST_EVENT_CODE)
                    .build();

            SpeakerPool speakerPool = SpeakerPool.builder()
                    .id(UUID.randomUUID())
                    .eventId(event.getId())
                    .username(TEST_USERNAME)
                    .email("john.doe@example.com")
                    .build();

            when(speakerService.getSpeakerEntityByUsername(TEST_USERNAME)).thenReturn(speaker);
            when(eventRepository.findByEventCode(TEST_EVENT_CODE)).thenReturn(Optional.of(event));
            when(speakerPoolRepository.findByEventIdAndUsername(event.getId(), TEST_USERNAME))
                    .thenReturn(Optional.of(speakerPool));
            when(invitationRepository.existsActiveInvitation(TEST_USERNAME, TEST_EVENT_CODE)).thenReturn(true);

            // When/Then
            assertThatThrownBy(() -> invitationService.sendInvitation(request, TEST_ORGANIZER))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already exists");
        }

        @Test
        void should_rejectInvitation_when_speakerHasNoEmail() {
            // Given
            SendInvitationRequest request = SendInvitationRequest.builder()
                    .username(TEST_USERNAME)
                    .eventCode(TEST_EVENT_CODE)
                    .build();

            Speaker speaker = Speaker.builder().username(TEST_USERNAME).build();
            Event event = Event.builder()
                    .id(UUID.randomUUID())
                    .eventCode(TEST_EVENT_CODE)
                    .build();

            SpeakerPool speakerPool = SpeakerPool.builder()
                    .id(UUID.randomUUID())
                    .eventId(event.getId())
                    .username(TEST_USERNAME)
                    .email(null) // No email
                    .build();

            when(speakerService.getSpeakerEntityByUsername(TEST_USERNAME)).thenReturn(speaker);
            when(eventRepository.findByEventCode(TEST_EVENT_CODE)).thenReturn(Optional.of(event));
            when(speakerPoolRepository.findByEventIdAndUsername(event.getId(), TEST_USERNAME))
                    .thenReturn(Optional.of(speakerPool));

            // When/Then
            assertThatThrownBy(() -> invitationService.sendInvitation(request, TEST_ORGANIZER))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("does not have an email address");
        }
    }

    @Nested
    class RespondToInvitation {

        @BeforeEach
        void setUp() {
            // Reset mocks for each test
        }

        @Test
        void should_recordAcceptedResponse_when_speakerAccepts() {
            // Given
            SpeakerInvitation invitation = createTestInvitation(InvitationStatus.SENT);
            RespondToInvitationRequest request = RespondToInvitationRequest.builder()
                    .responseType(ResponseType.ACCEPTED)
                    .build();

            Speaker speaker = Speaker.builder()
                    .username(TEST_USERNAME)
                    .workflowState(SpeakerWorkflowState.CONTACTED)
                    .build();

            when(invitationRepository.findByResponseToken(TEST_TOKEN)).thenReturn(Optional.of(invitation));
            when(speakerService.getSpeakerEntityByUsername(TEST_USERNAME)).thenReturn(speaker);
            when(invitationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            InvitationResponse response = invitationService.respondToInvitation(TEST_TOKEN, request);

            // Then
            assertThat(response.getResponseType()).isEqualTo(ResponseType.ACCEPTED);
            assertThat(response.getInvitationStatus()).isEqualTo(InvitationStatus.RESPONDED);
            assertThat(speaker.getWorkflowState()).isEqualTo(SpeakerWorkflowState.ACCEPTED);
        }

        @Test
        void should_recordDeclinedResponse_when_speakerDeclines() {
            // Given
            SpeakerInvitation invitation = createTestInvitation(InvitationStatus.SENT);
            RespondToInvitationRequest request = RespondToInvitationRequest.builder()
                    .responseType(ResponseType.DECLINED)
                    .declineReason("Schedule conflict")
                    .build();

            Speaker speaker = Speaker.builder()
                    .username(TEST_USERNAME)
                    .workflowState(SpeakerWorkflowState.CONTACTED)
                    .build();

            when(invitationRepository.findByResponseToken(TEST_TOKEN)).thenReturn(Optional.of(invitation));
            when(speakerService.getSpeakerEntityByUsername(TEST_USERNAME)).thenReturn(speaker);
            when(invitationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            InvitationResponse response = invitationService.respondToInvitation(TEST_TOKEN, request);

            // Then
            assertThat(response.getResponseType()).isEqualTo(ResponseType.DECLINED);
            assertThat(response.getDeclineReason()).isEqualTo("Schedule conflict");
            assertThat(speaker.getWorkflowState()).isEqualTo(SpeakerWorkflowState.DECLINED);
        }

        @Test
        void should_publishInvitationRespondedEvent_when_speakerResponds() {
            // Given
            SpeakerInvitation invitation = createTestInvitation(InvitationStatus.SENT);
            RespondToInvitationRequest request = RespondToInvitationRequest.builder()
                    .responseType(ResponseType.ACCEPTED)
                    .build();

            Speaker speaker = Speaker.builder()
                    .username(TEST_USERNAME)
                    .workflowState(SpeakerWorkflowState.CONTACTED)
                    .build();

            when(invitationRepository.findByResponseToken(TEST_TOKEN)).thenReturn(Optional.of(invitation));
            when(speakerService.getSpeakerEntityByUsername(TEST_USERNAME)).thenReturn(speaker);
            when(invitationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            invitationService.respondToInvitation(TEST_TOKEN, request);

            // Then
            verify(domainEventPublisher).publish(invitationRespondedEventCaptor.capture());
            InvitationRespondedEvent event = invitationRespondedEventCaptor.getValue();
            assertThat(event.getUsername()).isEqualTo(TEST_USERNAME);
            assertThat(event.getResponseType()).isEqualTo("ACCEPTED");
        }

        @Test
        void should_rejectResponse_when_invitationExpired() {
            // Given
            SpeakerInvitation expiredInvitation = SpeakerInvitation.builder()
                    .id(UUID.randomUUID())
                    .username(TEST_USERNAME)
                    .eventCode(TEST_EVENT_CODE)
                    .responseToken(TEST_TOKEN)
                    .invitationStatus(InvitationStatus.SENT)
                    .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)) // Expired yesterday
                    .createdBy(TEST_ORGANIZER)
                    .build();

            RespondToInvitationRequest request = RespondToInvitationRequest.builder()
                    .responseType(ResponseType.ACCEPTED)
                    .build();

            when(invitationRepository.findByResponseToken(TEST_TOKEN)).thenReturn(Optional.of(expiredInvitation));

            // When/Then
            assertThatThrownBy(() -> invitationService.respondToInvitation(TEST_TOKEN, request))
                    .isInstanceOf(InvitationExpiredException.class);
        }

        @Test
        void should_rejectResponse_when_invalidToken() {
            // Given
            String invalidToken = "invalid_token_that_does_not_exist";
            RespondToInvitationRequest request = RespondToInvitationRequest.builder()
                    .responseType(ResponseType.ACCEPTED)
                    .build();

            when(invitationRepository.findByResponseToken(invalidToken)).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> invitationService.respondToInvitation(invalidToken, request))
                    .isInstanceOf(InvitationNotFoundException.class);
        }
    }

    @Nested
    class GetInvitationByToken {

        @Test
        void should_returnInvitation_when_validTokenProvided() {
            // Given
            SpeakerInvitation invitation = createTestInvitation(InvitationStatus.SENT);
            when(invitationRepository.findByResponseToken(TEST_TOKEN)).thenReturn(Optional.of(invitation));

            // When
            InvitationResponse response = invitationService.getInvitationByToken(TEST_TOKEN);

            // Then
            assertThat(response.getUsername()).isEqualTo(TEST_USERNAME);
            assertThat(response.getEventCode()).isEqualTo(TEST_EVENT_CODE);
        }

        @Test
        void should_throwException_when_invalidToken() {
            // Given
            when(invitationRepository.findByResponseToken(anyString())).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> invitationService.getInvitationByToken("invalid"))
                    .isInstanceOf(InvitationNotFoundException.class);
        }
    }

    // Helper methods

    private SpeakerInvitation createTestInvitation(InvitationStatus status) {
        return SpeakerInvitation.builder()
                .id(UUID.randomUUID())
                .username(TEST_USERNAME)
                .eventCode(TEST_EVENT_CODE)
                .responseToken(TEST_TOKEN)
                .invitationStatus(status)
                .expiresAt(Instant.now().plus(14, ChronoUnit.DAYS))
                .createdBy(TEST_ORGANIZER)
                .sentAt(status == InvitationStatus.SENT ? Instant.now() : null)
                .build();
    }
}
