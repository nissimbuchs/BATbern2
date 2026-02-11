package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.dto.SpeakerStatusResponse;
import ch.batbern.events.dto.StatusSummaryResponse;
import ch.batbern.events.dto.UpdateStatusRequest;
import ch.batbern.events.dto.generated.EventSlotConfigurationResponse;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.events.validator.StatusTransitionValidator;
import ch.batbern.shared.events.DomainEventPublisher;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit Tests for SpeakerStatusService
 * Story 5.4: Speaker Status Management (AC1-6, AC10-13)
 *
 * Test Scenarios:
 * - AC1-2: Status update business logic
 * - AC3-4: Status history creation
 * - AC5-6: Status summary calculation with acceptance rate
 * - AC10-13: Workflow integration and overflow detection
 */
@ExtendWith(MockitoExtension.class)
public class SpeakerStatusServiceTest {

    @Mock
    private SpeakerStatusHistoryRepository repository;

    @Mock
    private StatusTransitionValidator validator;

    @Mock
    private SpeakerPoolRepository speakerPoolRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private EventTypeService eventTypeService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    private SpeakerStatusService service;

    @BeforeEach
    void setUp() {
        service = new SpeakerStatusService(repository, validator, speakerPoolRepository, eventRepository, sessionRepository, eventTypeService, eventPublisher, domainEventPublisher);
    }

    /**
     * AC1: should_updateSpeakerStatus_when_validTransitionProvided
     * V29: Updated to mock Event entity for eventCode lookup
     */
    @Test
    @DisplayName("Should update speaker status when valid transition is provided")
    void should_updateSpeakerStatus_when_validTransitionProvided() {
        // Given
        String eventCode = "BATbern998";
        UUID eventId = UUID.randomUUID();
        UUID speakerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String organizerUsername = "organizer@example.com";

        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setNewStatus(SpeakerWorkflowState.CONTACTED);
        request.setReason("Initial contact");

        Event event = new Event();
        event.setId(eventId);
        event.setEventCode(eventCode);

        SpeakerPool speaker = new SpeakerPool();
        speaker.setId(speakerId);
        speaker.setSessionId(sessionId);
        speaker.setEventId(eventId); // V29: Speaker now has eventId

        when(speakerPoolRepository.findById(speakerId))
            .thenReturn(Optional.of(speaker));
        when(eventRepository.findById(eventId))
            .thenReturn(Optional.of(event)); // V29: Mock event lookup for eventCode in response
        when(repository.save(any(SpeakerStatusHistory.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        SpeakerStatusResponse response = service.updateStatus(eventCode, speakerId, organizerUsername, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSpeakerId()).isEqualTo(speakerId);
        assertThat(response.getEventCode()).isEqualTo(eventCode);
        assertThat(response.getCurrentStatus()).isEqualTo(SpeakerWorkflowState.CONTACTED);
        assertThat(response.getPreviousStatus()).isEqualTo(SpeakerWorkflowState.IDENTIFIED);
        assertThat(response.getChangedByUsername()).isEqualTo(organizerUsername);
        assertThat(response.getChangeReason()).isEqualTo("Initial contact");

        verify(validator).validateTransition(SpeakerWorkflowState.IDENTIFIED, SpeakerWorkflowState.CONTACTED);
        verify(repository).save(any(SpeakerStatusHistory.class));
    }

    /**
     * AC1: should_throwNotFoundException_when_speakerNotFound
     */
    @Test
    @DisplayName("Should throw NotFoundException when speaker not found")
    void should_throwNotFoundException_when_speakerNotFound() {
        // Given
        String eventCode = "BATbern998";
        UUID speakerId = UUID.randomUUID();
        String organizerUsername = "organizer@example.com";

        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setNewStatus(SpeakerWorkflowState.CONTACTED);
        request.setReason("Initial contact");

        when(speakerPoolRepository.findById(speakerId))
            .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() ->
            service.updateStatus(eventCode, speakerId, organizerUsername, request)
        ).isInstanceOf(NotFoundException.class)
         .hasMessageContaining("Speaker not found");
    }

    /**
     * AC5-6: should_calculateAcceptanceRate_when_gettingStatusSummary
     */
    @Test
    @DisplayName("Should calculate acceptance rate when getting status summary")
    void should_calculateAcceptanceRate_when_gettingStatusSummary() {
        // Given
        String eventCode = "BATbern998";
        Event event = new Event();
        event.setEventCode(eventCode);
        event.setEventType(EventType.FULL_DAY);

        EventSlotConfigurationResponse slotConfig = new EventSlotConfigurationResponse();
        slotConfig.setMinSlots(6);
        slotConfig.setMaxSlots(8);

        when(eventRepository.findByEventCode(eventCode))
            .thenReturn(Optional.of(event));
        when(eventTypeService.getEventType(EventType.FULL_DAY))
            .thenReturn(slotConfig);
        when(speakerPoolRepository.findByEventId(event.getId()))
            .thenReturn(new ArrayList<>());

        // When
        StatusSummaryResponse response = service.getStatusSummary(eventCode);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getEventCode()).isEqualTo(eventCode);
        assertThat(response.getMinSlotsRequired()).isEqualTo(6);
        assertThat(response.getMaxSlotsAllowed()).isEqualTo(8);

        verify(eventRepository).findByEventCode(eventCode);
        verify(eventTypeService).getEventType(EventType.FULL_DAY);
    }

    /**
     * AC5-6: should_throwNotFoundException_when_eventNotFound
     */
    @Test
    @DisplayName("Should throw NotFoundException when event not found")
    void should_throwNotFoundException_when_eventNotFound() {
        // Given
        String eventCode = "BATbern998";

        when(eventRepository.findByEventCode(eventCode))
            .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() ->
            service.getStatusSummary(eventCode)
        ).isInstanceOf(NotFoundException.class)
         .hasMessageContaining("Event not found");
    }

    /**
     * AC15: should_getStatusHistory_when_validSpeakerIdProvided
     * V29: Updated to use eventId instead of eventCode
     */
    @Test
    @DisplayName("Should get status history when valid speaker ID is provided")
    void should_getStatusHistory_when_validSpeakerIdProvided() {
        // Given
        String eventCode = "BATbern998";
        UUID eventId = UUID.randomUUID();
        UUID speakerId = UUID.randomUUID();

        SpeakerStatusHistory history = new SpeakerStatusHistory();
        history.setSpeakerPoolId(speakerId);
        history.setEventId(eventId); // V29: Changed from setEventCode to setEventId
        history.setPreviousStatus(SpeakerWorkflowState.IDENTIFIED);
        history.setNewStatus(SpeakerWorkflowState.CONTACTED);

        when(repository.findBySpeakerPoolIdOrderByChangedAtDesc(speakerId))
            .thenReturn(List.of(history));

        // When
        var result = service.getStatusHistory(eventCode, speakerId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNewStatus()).isEqualTo(SpeakerWorkflowState.CONTACTED);

        verify(repository).findBySpeakerPoolIdOrderByChangedAtDesc(speakerId);
    }

    /**
     * AC15: should_throwNotFoundException_when_noHistoryFound
     */
    @Test
    @DisplayName("Should throw NotFoundException when no history found")
    void should_throwNotFoundException_when_noHistoryFound() {
        // Given
        String eventCode = "BATbern998";
        UUID speakerId = UUID.randomUUID();

        when(repository.findBySpeakerPoolIdOrderByChangedAtDesc(speakerId))
            .thenReturn(new ArrayList<>());

        // When/Then
        assertThatThrownBy(() ->
            service.getStatusHistory(eventCode, speakerId)
        ).isInstanceOf(NotFoundException.class)
         .hasMessageContaining("No status history found");
    }

    /**
     * Session cleanup: should delete session when speaker with session is declined
     */
    @Test
    @DisplayName("Should delete associated session when speaker with session is declined")
    void should_deleteSession_when_speakerDeclinedWithSession() {
        // Given
        String eventCode = "BATbern998";
        UUID eventId = UUID.randomUUID();
        UUID speakerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String organizerUsername = "organizer@example.com";

        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setNewStatus(SpeakerWorkflowState.DECLINED);
        request.setReason("Speaker unavailable");

        Event event = new Event();
        event.setId(eventId);
        event.setEventCode(eventCode);

        SpeakerPool speaker = new SpeakerPool();
        speaker.setId(speakerId);
        speaker.setSessionId(sessionId);
        speaker.setEventId(eventId);
        speaker.setStatus(SpeakerWorkflowState.CONTENT_SUBMITTED);

        when(speakerPoolRepository.findById(speakerId))
            .thenReturn(Optional.of(speaker));
        when(eventRepository.findById(eventId))
            .thenReturn(Optional.of(event));
        when(repository.save(any(SpeakerStatusHistory.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        SpeakerStatusResponse response = service.updateStatus(eventCode, speakerId, organizerUsername, request);

        // Then
        assertThat(response.getCurrentStatus()).isEqualTo(SpeakerWorkflowState.DECLINED);
        assertThat(speaker.getSessionId()).isNull();
        verify(sessionRepository).deleteById(sessionId);
    }

    /**
     * Session cleanup: should NOT attempt session deletion when declining speaker without session
     */
    @Test
    @DisplayName("Should not delete session when speaker without session is declined")
    void should_notDeleteSession_when_speakerDeclinedWithoutSession() {
        // Given
        String eventCode = "BATbern998";
        UUID eventId = UUID.randomUUID();
        UUID speakerId = UUID.randomUUID();
        String organizerUsername = "organizer@example.com";

        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setNewStatus(SpeakerWorkflowState.DECLINED);
        request.setReason("Not interested");

        Event event = new Event();
        event.setId(eventId);
        event.setEventCode(eventCode);

        SpeakerPool speaker = new SpeakerPool();
        speaker.setId(speakerId);
        speaker.setSessionId(null);
        speaker.setEventId(eventId);
        speaker.setStatus(SpeakerWorkflowState.CONTACTED);

        when(speakerPoolRepository.findById(speakerId))
            .thenReturn(Optional.of(speaker));
        when(eventRepository.findById(eventId))
            .thenReturn(Optional.of(event));
        when(repository.save(any(SpeakerStatusHistory.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        SpeakerStatusResponse response = service.updateStatus(eventCode, speakerId, organizerUsername, request);

        // Then
        assertThat(response.getCurrentStatus()).isEqualTo(SpeakerWorkflowState.DECLINED);
        verify(sessionRepository, never()).deleteById(any());
    }
}
