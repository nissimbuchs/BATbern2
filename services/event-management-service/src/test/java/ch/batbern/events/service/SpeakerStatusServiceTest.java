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
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.events.service.workflow.SecurityPrincipal;
import ch.batbern.events.service.workflow.TransitionPayload;
import ch.batbern.events.service.workflow.TransitionResult;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit Tests for SpeakerStatusService (Story 11.B.2 — delegates to SpeakerWorkflowService.transition).
 *
 * <p>The status-mutation tests assert delegation; the history/summary tests cover the
 * read paths which remain in this service.
 */
@ExtendWith(MockitoExtension.class)
public class SpeakerStatusServiceTest {

    @Mock
    private SpeakerStatusHistoryRepository repository;

    @Mock
    private SpeakerPoolRepository speakerPoolRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventTypeService eventTypeService;

    @Mock
    private SpeakerWorkflowService speakerWorkflowService;

    @Mock
    private SecurityContextHelper securityContextHelper;

    private SpeakerStatusService service;

    @BeforeEach
    void setUp() {
        service = new SpeakerStatusService(
                repository,
                speakerPoolRepository,
                eventRepository,
                eventTypeService,
                speakerWorkflowService,
                securityContextHelper
        );
    }

    @Test
    @DisplayName("Should delegate to SpeakerWorkflowService.transition() when updating status")
    void should_delegateToTransition_when_updatingStatus() {
        String eventCode = "BATbern998";
        UUID eventId = UUID.randomUUID();
        UUID speakerId = UUID.randomUUID();
        String organizerUsername = "organizer";

        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setNewStatus(SpeakerWorkflowState.CONTACTED);
        request.setReason("Initial contact");

        Event event = new Event();
        event.setId(eventId);
        event.setEventCode(eventCode);

        SpeakerStatusHistory historyRow = new SpeakerStatusHistory();
        historyRow.setSpeakerPoolId(speakerId);
        historyRow.setEventId(eventId);
        historyRow.setPreviousStatus(SpeakerWorkflowState.IDENTIFIED);
        historyRow.setNewStatus(SpeakerWorkflowState.CONTACTED);
        historyRow.setChangedByUsername(organizerUsername);
        historyRow.setChangeReason("Initial contact");

        SpeakerPool speaker = new SpeakerPool();
        speaker.setId(speakerId);
        speaker.setEventId(eventId);

        when(speakerPoolRepository.existsById(speakerId)).thenReturn(true);
        when(securityContextHelper.getCurrentUserRoles()).thenReturn(List.of("ORGANIZER"));
        when(speakerWorkflowService.transition(
                eq(speakerId),
                eq(SpeakerWorkflowState.CONTACTED),
                any(SecurityPrincipal.class),
                any(TransitionPayload.class)))
                .thenReturn(new TransitionResult(speaker, historyRow));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        SpeakerStatusResponse response = service.updateStatus(eventCode, speakerId, organizerUsername, request);

        assertThat(response).isNotNull();
        assertThat(response.getSpeakerId()).isEqualTo(speakerId);
        assertThat(response.getEventCode()).isEqualTo(eventCode);
        assertThat(response.getCurrentStatus()).isEqualTo(SpeakerWorkflowState.CONTACTED);
        assertThat(response.getPreviousStatus()).isEqualTo(SpeakerWorkflowState.IDENTIFIED);
        assertThat(response.getChangedByUsername()).isEqualTo(organizerUsername);

        ArgumentCaptor<SecurityPrincipal> actorCaptor = ArgumentCaptor.forClass(SecurityPrincipal.class);
        ArgumentCaptor<TransitionPayload> payloadCaptor = ArgumentCaptor.forClass(TransitionPayload.class);
        verify(speakerWorkflowService).transition(
                eq(speakerId), eq(SpeakerWorkflowState.CONTACTED),
                actorCaptor.capture(), payloadCaptor.capture());
        assertThat(actorCaptor.getValue().username()).isEqualTo(organizerUsername);
        assertThat(actorCaptor.getValue().roles()).containsExactly("ORGANIZER");
        assertThat(payloadCaptor.getValue().reason()).isEqualTo("Initial contact");
    }

    @Test
    @DisplayName("Should throw NotFoundException when speaker not found")
    void should_throwNotFoundException_when_speakerNotFound() {
        String eventCode = "BATbern998";
        UUID speakerId = UUID.randomUUID();
        String organizerUsername = "organizer";

        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setNewStatus(SpeakerWorkflowState.CONTACTED);
        request.setReason("Initial contact");

        when(speakerPoolRepository.existsById(speakerId)).thenReturn(false);

        assertThatThrownBy(() -> service.updateStatus(eventCode, speakerId, organizerUsername, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Speaker not found");
    }

    @Test
    @DisplayName("Should fall back to empty roles when SecurityContext throws (no auth in flow)")
    void should_fallBackToEmptyRoles_when_securityContextUnavailable() {
        String eventCode = "BATbern998";
        UUID eventId = UUID.randomUUID();
        UUID speakerId = UUID.randomUUID();

        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setNewStatus(SpeakerWorkflowState.CONTACTED);
        request.setReason("Initial contact");

        Event event = new Event();
        event.setId(eventId);
        event.setEventCode(eventCode);

        SpeakerStatusHistory historyRow = new SpeakerStatusHistory();
        historyRow.setSpeakerPoolId(speakerId);
        historyRow.setEventId(eventId);
        historyRow.setPreviousStatus(SpeakerWorkflowState.IDENTIFIED);
        historyRow.setNewStatus(SpeakerWorkflowState.CONTACTED);

        SpeakerPool speaker = new SpeakerPool();
        speaker.setId(speakerId);
        speaker.setEventId(eventId);

        when(speakerPoolRepository.existsById(speakerId)).thenReturn(true);
        when(securityContextHelper.getCurrentUserRoles()).thenThrow(new SecurityException("No auth"));
        when(speakerWorkflowService.transition(
                eq(speakerId), eq(SpeakerWorkflowState.CONTACTED),
                any(SecurityPrincipal.class), any(TransitionPayload.class)))
                .thenReturn(new TransitionResult(speaker, historyRow));
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        SpeakerStatusResponse response = service.updateStatus(eventCode, speakerId, "system", request);
        assertThat(response).isNotNull();

        ArgumentCaptor<SecurityPrincipal> actorCaptor = ArgumentCaptor.forClass(SecurityPrincipal.class);
        verify(speakerWorkflowService).transition(
                eq(speakerId), eq(SpeakerWorkflowState.CONTACTED),
                actorCaptor.capture(), any(TransitionPayload.class));
        assertThat(actorCaptor.getValue().roles()).isEmpty();
    }

    @Test
    @DisplayName("Should calculate acceptance rate when getting status summary")
    void should_calculateAcceptanceRate_when_gettingStatusSummary() {
        String eventCode = "BATbern998";
        Event event = new Event();
        event.setEventCode(eventCode);
        event.setEventType(EventType.FULL_DAY);

        EventSlotConfigurationResponse slotConfig = new EventSlotConfigurationResponse();
        slotConfig.setMinSlots(6);
        slotConfig.setMaxSlots(8);

        when(eventRepository.findByEventCode(eventCode)).thenReturn(Optional.of(event));
        when(eventTypeService.getEventType(EventType.FULL_DAY)).thenReturn(slotConfig);
        when(speakerPoolRepository.findByEventId(event.getId())).thenReturn(new ArrayList<>());

        StatusSummaryResponse response = service.getStatusSummary(eventCode);

        assertThat(response).isNotNull();
        assertThat(response.getEventCode()).isEqualTo(eventCode);
        assertThat(response.getMinSlotsRequired()).isEqualTo(6);
        assertThat(response.getMaxSlotsAllowed()).isEqualTo(8);
    }

    @Test
    @DisplayName("Should throw NotFoundException when event not found for status summary")
    void should_throwNotFoundException_when_eventNotFoundForSummary() {
        String eventCode = "BATbern998";
        when(eventRepository.findByEventCode(eventCode)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStatusSummary(eventCode))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Event not found");
    }

    @Test
    @DisplayName("Should get status history when valid speaker ID provided")
    void should_getStatusHistory_when_validSpeakerIdProvided() {
        String eventCode = "BATbern998";
        UUID eventId = UUID.randomUUID();
        UUID speakerId = UUID.randomUUID();

        SpeakerStatusHistory history = new SpeakerStatusHistory();
        history.setSpeakerPoolId(speakerId);
        history.setEventId(eventId);
        history.setPreviousStatus(SpeakerWorkflowState.IDENTIFIED);
        history.setNewStatus(SpeakerWorkflowState.CONTACTED);

        when(repository.findBySpeakerPoolIdOrderByChangedAtDesc(speakerId))
                .thenReturn(List.of(history));

        var result = service.getStatusHistory(eventCode, speakerId);

        assertThat(result).isNotNull().hasSize(1);
        assertThat(result.get(0).getNewStatus()).isEqualTo(SpeakerWorkflowState.CONTACTED);
    }

    @Test
    @DisplayName("Should throw NotFoundException when no history found")
    void should_throwNotFoundException_when_noHistoryFound() {
        String eventCode = "BATbern998";
        UUID speakerId = UUID.randomUUID();

        when(repository.findBySpeakerPoolIdOrderByChangedAtDesc(speakerId))
                .thenReturn(new ArrayList<>());

        assertThatThrownBy(() -> service.getStatusHistory(eventCode, speakerId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No status history found");
    }
}
