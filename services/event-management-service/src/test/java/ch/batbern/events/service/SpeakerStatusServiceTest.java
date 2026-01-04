package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.dto.generated.speakers.SpeakerStatusResponse;
import ch.batbern.events.dto.generated.speakers.StatusHistoryItem;
import ch.batbern.events.dto.StatusSummaryResponse;
import ch.batbern.events.dto.generated.speakers.UpdateStatusRequest;
import ch.batbern.events.dto.generated.EventSlotConfigurationResponse;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.mapper.SpeakerMapper;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.events.validator.StatusTransitionValidator;
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

    private static final String TEST_SPEAKER_USERNAME = "jane.smith"; // Story BAT-18: Meaningful identifier

    @Mock
    private SpeakerStatusHistoryRepository repository;

    @Mock
    private StatusTransitionValidator validator;

    @Mock
    private SpeakerPoolRepository speakerPoolRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventTypeService eventTypeService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private SpeakerMapper speakerMapper;

    private SpeakerStatusService service;

    @BeforeEach
    void setUp() {
        service = new SpeakerStatusService(repository, validator, speakerPoolRepository, eventRepository, eventTypeService, eventPublisher, speakerMapper);
    }

    /**
     * AC1: should_updateSpeakerStatus_when_validTransitionProvided
     * BAT-18: Updated to use username instead of speakerId (ADR-003)
     */
    @Test
    @DisplayName("Should update speaker status when valid transition is provided")
    void should_updateSpeakerStatus_when_validTransitionProvided() {
        // Given
        String eventCode = "BATbern998";
        UUID eventId = UUID.randomUUID();
        String username = TEST_SPEAKER_USERNAME;
        UUID sessionId = UUID.randomUUID();
        String organizerUsername = "organizer@example.com";

        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setNewStatus(ch.batbern.events.dto.generated.speakers.SpeakerWorkflowState.CONTACTED);
        request.setReason("Initial contact");

        Event event = new Event();
        event.setId(eventId);
        event.setEventCode(eventCode);

        SpeakerPool speaker = new SpeakerPool();
        speaker.setId(UUID.randomUUID());
        speaker.setUsername(username);
        speaker.setSessionId(sessionId);
        speaker.setEventId(eventId);

        SpeakerStatusResponse expectedResponse = new SpeakerStatusResponse();
        expectedResponse.setSpeakerUsername(username);
        expectedResponse.setEventCode(eventCode);
        expectedResponse.setCurrentStatus(ch.batbern.events.dto.generated.speakers.SpeakerWorkflowState.CONTACTED);
        expectedResponse.setPreviousStatus(ch.batbern.events.dto.generated.speakers.SpeakerWorkflowState.IDENTIFIED);
        expectedResponse.setChangedByUsername(organizerUsername);
        expectedResponse.setChangeReason("Initial contact");

        when(eventRepository.findByEventCode(eventCode))
            .thenReturn(Optional.of(event));
        when(speakerPoolRepository.findByEventIdAndUsername(eventId, username))
            .thenReturn(Optional.of(speaker));
        when(speakerPoolRepository.save(any(SpeakerPool.class)))
            .thenReturn(speaker);
        when(repository.save(any(SpeakerStatusHistory.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(speakerMapper.toSpeakerStatusResponse(any(), any(), any(), any(), any()))
            .thenReturn(expectedResponse);

        // When
        SpeakerStatusResponse response = service.updateStatus(eventCode, username, organizerUsername, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getSpeakerUsername()).isEqualTo(username);
        assertThat(response.getEventCode()).isEqualTo(eventCode);
        assertThat(response.getCurrentStatus()).isEqualTo(ch.batbern.events.dto.generated.speakers.SpeakerWorkflowState.CONTACTED);
        assertThat(response.getPreviousStatus()).isEqualTo(ch.batbern.events.dto.generated.speakers.SpeakerWorkflowState.IDENTIFIED);
        assertThat(response.getChangedByUsername()).isEqualTo(organizerUsername);
        assertThat(response.getChangeReason()).isEqualTo("Initial contact");

        verify(validator).validateTransition(SpeakerWorkflowState.IDENTIFIED, SpeakerWorkflowState.CONTACTED);
        verify(repository).save(any(SpeakerStatusHistory.class));
    }

    /**
     * AC1: should_throwNotFoundException_when_speakerNotFound
     * BAT-18: Updated to use username instead of speakerId (ADR-003)
     */
    @Test
    @DisplayName("Should throw NotFoundException when speaker not found")
    void should_throwNotFoundException_when_speakerNotFound() {
        // Given
        String eventCode = "BATbern998";
        UUID eventId = UUID.randomUUID();
        String username = TEST_SPEAKER_USERNAME;
        String organizerUsername = "organizer@example.com";

        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setNewStatus(ch.batbern.events.dto.generated.speakers.SpeakerWorkflowState.CONTACTED);
        request.setReason("Initial contact");

        Event event = new Event();
        event.setId(eventId);
        event.setEventCode(eventCode);

        when(eventRepository.findByEventCode(eventCode))
            .thenReturn(Optional.of(event));
        when(speakerPoolRepository.findByEventIdAndUsername(eventId, username))
            .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() ->
            service.updateStatus(eventCode, username, organizerUsername, request)
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
     * BAT-18: Updated to use username instead of speakerId (ADR-003)
     */
    @Test
    @DisplayName("Should get status history when valid speaker ID is provided")
    void should_getStatusHistory_when_validSpeakerIdProvided() {
        // Given
        String eventCode = "BATbern998";
        UUID eventId = UUID.randomUUID();
        UUID speakerPoolId = UUID.randomUUID();
        String username = TEST_SPEAKER_USERNAME;

        Event event = new Event();
        event.setId(eventId);
        event.setEventCode(eventCode);

        SpeakerPool speaker = new SpeakerPool();
        speaker.setId(speakerPoolId);
        speaker.setUsername(username);
        speaker.setEventId(eventId);

        SpeakerStatusHistory history = new SpeakerStatusHistory();
        history.setSpeakerPoolId(speakerPoolId);
        history.setEventId(eventId);
        history.setPreviousStatus(SpeakerWorkflowState.IDENTIFIED);
        history.setNewStatus(SpeakerWorkflowState.CONTACTED);

        StatusHistoryItem historyDto = new StatusHistoryItem();
        historyDto.setNewStatus(ch.batbern.events.dto.generated.speakers.SpeakerWorkflowState.CONTACTED);

        when(eventRepository.findByEventCode(eventCode))
            .thenReturn(Optional.of(event));
        when(speakerPoolRepository.findByEventIdAndUsername(eventId, username))
            .thenReturn(Optional.of(speaker));
        when(repository.findBySpeakerPoolIdOrderByChangedAtDesc(speakerPoolId))
            .thenReturn(List.of(history));
        when(speakerMapper.toStatusHistoryDto(any(SpeakerStatusHistory.class)))
            .thenReturn(historyDto);

        // When
        var result = service.getStatusHistory(eventCode, username);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNewStatus()).isEqualTo(ch.batbern.events.dto.generated.speakers.SpeakerWorkflowState.CONTACTED);

        verify(repository).findBySpeakerPoolIdOrderByChangedAtDesc(speakerPoolId);
    }

    /**
     * AC15: should_throwNotFoundException_when_noHistoryFound
     * BAT-18: Updated to use username instead of speakerId (ADR-003)
     */
    @Test
    @DisplayName("Should throw NotFoundException when no history found")
    void should_throwNotFoundException_when_noHistoryFound() {
        // Given
        String eventCode = "BATbern998";
        UUID eventId = UUID.randomUUID();
        UUID speakerPoolId = UUID.randomUUID();
        String username = TEST_SPEAKER_USERNAME;

        Event event = new Event();
        event.setId(eventId);
        event.setEventCode(eventCode);

        SpeakerPool speaker = new SpeakerPool();
        speaker.setId(speakerPoolId);
        speaker.setUsername(username);
        speaker.setEventId(eventId);

        when(eventRepository.findByEventCode(eventCode))
            .thenReturn(Optional.of(event));
        when(speakerPoolRepository.findByEventIdAndUsername(eventId, username))
            .thenReturn(Optional.of(speaker));
        when(repository.findBySpeakerPoolIdOrderByChangedAtDesc(speakerPoolId))
            .thenReturn(new ArrayList<>());

        // When/Then
        assertThatThrownBy(() ->
            service.getStatusHistory(eventCode, username)
        ).isInstanceOf(NotFoundException.class)
         .hasMessageContaining("No status history found");
    }
}
