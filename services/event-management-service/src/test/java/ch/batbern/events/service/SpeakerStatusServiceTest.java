package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionContentVersion;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.dto.SpeakerStatusResponse;
import ch.batbern.events.dto.StatusHistoryItem;
import ch.batbern.events.dto.StatusSummaryResponse;
import ch.batbern.events.dto.UpdateStatusRequest;
import ch.batbern.events.dto.generated.EventSlotConfigurationResponse;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionContentHistoryRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
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
    private SessionContentHistoryRepository sessionContentHistoryRepository;

    private SpeakerStatusService service;

    @BeforeEach
    void setUp() {
        service = new SpeakerStatusService(
                repository,
                speakerPoolRepository,
                eventRepository,
                eventTypeService,
                speakerWorkflowService,
                sessionContentHistoryRepository
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

        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.of(speaker));
        when(speakerWorkflowService.transition(
                eq(speakerId),
                eq(SpeakerWorkflowState.CONTACTED),
                anyString(),
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

        ArgumentCaptor<String> actorCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TransitionPayload> payloadCaptor = ArgumentCaptor.forClass(TransitionPayload.class);
        verify(speakerWorkflowService).transition(
                eq(speakerId), eq(SpeakerWorkflowState.CONTACTED),
                actorCaptor.capture(), payloadCaptor.capture());
        assertThat(actorCaptor.getValue()).isEqualTo(organizerUsername);
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

        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(eventCode, speakerId, organizerUsername, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Speaker not found");
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
    @DisplayName("Should interleave content-rejection events from session_content_history")
    void should_interleaveContentRejections_when_buildingHistory() {
        // Repro: organizer rejects an abstract via /review (ADR-009: no state transition →
        // nothing in speaker_status_history); the rejection feedback must still appear in
        // the drawer's History tab. Sources from session_content_history.reviewer_feedback.
        String eventCode = "BATbern59";
        UUID speakerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        java.time.Instant earlier = java.time.Instant.parse("2026-05-26T18:00:00Z");
        java.time.Instant later = java.time.Instant.parse("2026-05-26T18:21:24Z");

        SpeakerStatusHistory statusRow = new SpeakerStatusHistory();
        statusRow.setSpeakerPoolId(speakerId);
        statusRow.setPreviousStatus(SpeakerWorkflowState.ACCEPTED);
        statusRow.setNewStatus(SpeakerWorkflowState.CONTENT_SUBMITTED);
        statusRow.setChangedByUsername("speaker.jane");
        statusRow.setChangedAt(earlier);

        SpeakerPool pool = new SpeakerPool();
        pool.setId(speakerId);
        pool.setSessionId(sessionId);

        SessionContentVersion rejection = new SessionContentVersion();
        rejection.setId(UUID.randomUUID());
        Session session = new Session();
        session.setId(sessionId);
        rejection.setSession(session);
        rejection.setReviewerFeedback("Please remove vendor pitch from intro");
        rejection.setReviewedBy("nissim.buchs");
        rejection.setReviewedAt(later);

        when(repository.findBySpeakerPoolIdOrderByChangedAtDesc(speakerId)).thenReturn(List.of(statusRow));
        when(speakerPoolRepository.findById(speakerId)).thenReturn(Optional.of(pool));
        when(sessionContentHistoryRepository.findRejectedBySessionIdOrderByReviewedAtDesc(sessionId))
                .thenReturn(List.of(rejection));

        var result = service.getStatusHistory(eventCode, speakerId);

        assertThat(result).hasSize(2);
        // Newest first → rejection at 18:21, status change at 18:00
        assertThat(result.get(0).getKind()).isEqualTo(StatusHistoryItem.Kind.CONTENT_REJECTED);
        assertThat(result.get(0).getChangeReason()).isEqualTo("Please remove vendor pitch from intro");
        assertThat(result.get(0).getChangedByUsername()).isEqualTo("nissim.buchs");
        assertThat(result.get(0).getPreviousStatus()).isNull();
        assertThat(result.get(0).getNewStatus()).isNull();
        assertThat(result.get(1).getKind()).isEqualTo(StatusHistoryItem.Kind.STATUS_CHANGE);
        assertThat(result.get(1).getNewStatus()).isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);
    }

    @Test
    @DisplayName("Should return empty list when no history found (Epic 11 bug fix 2026-05-19)")
    void should_returnEmptyList_when_noHistoryFound() {
        // Empty history is a legitimate state for any speaker that has not yet
        // transitioned — e.g. fresh IDENTIFIED brainstorm entries, or transitions
        // written with TransitionPayload.suppressHistoryRow=true. The drawer's
        // unified history feed interprets 404 as "session expired" and shows a
        // generic load-error toast; an empty list renders the "no entries yet"
        // empty state instead.
        String eventCode = "BATbern998";
        UUID speakerId = UUID.randomUUID();

        when(repository.findBySpeakerPoolIdOrderByChangedAtDesc(speakerId))
                .thenReturn(new ArrayList<>());

        var result = service.getStatusHistory(eventCode, speakerId);

        assertThat(result).isEmpty();
    }
}
