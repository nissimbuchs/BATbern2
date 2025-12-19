package ch.batbern.events.service;

import ch.batbern.shared.types.SpeakerWorkflowState;
import ch.batbern.events.dto.UpdateStatusRequest;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.events.service.EventTypeService;
import ch.batbern.events.validator.StatusTransitionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit Tests for SpeakerStatusService
 * Story 5.4: Speaker Status Management (AC1-6, AC10-13)
 *
 * Test Scenarios:
 * - AC1-2: Status update business logic
 * - AC3-4: Status history creation
 * - AC5-6: Status summary calculation with acceptance rate
 * - AC10-13: Workflow integration and overflow detection
 *
 * TDD Workflow: RED Phase - These tests will fail until service is implemented
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
    private EventTypeService eventTypeService;

    private SpeakerStatusService service;

    @BeforeEach
    void setUp() {
        service = new SpeakerStatusService(repository, validator, speakerPoolRepository, eventRepository, eventTypeService);
    }

    /**
     * AC1: should_updateSpeakerStatus_when_validTransitionProvided
     */
    @Test
    @DisplayName("Should update speaker status when valid transition is provided")
    void should_updateSpeakerStatus_when_validTransitionProvided() {
        // Given
        String eventCode = "BATbern998";
        UUID speakerId = UUID.randomUUID();
        String organizerUsername = "organizer@example.com";
        UpdateStatusRequest request = new UpdateStatusRequest();
        request.setNewStatus(SpeakerWorkflowState.CONTACTED);
        request.setReason("Initial contact");

        // When/Then: Service method should throw since it's not implemented (RED phase)
        assertThatThrownBy(() ->
            service.updateStatus(eventCode, speakerId, organizerUsername, request)
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * AC5-6: should_calculateAcceptanceRate_when_gettingStatusSummary
     */
    @Test
    @DisplayName("Should calculate acceptance rate when getting status summary")
    void should_calculateAcceptanceRate_when_gettingStatusSummary() {
        // Given
        String eventCode = "BATbern998";

        // When/Then: Service method should throw since it's not implemented (RED phase)
        assertThatThrownBy(() ->
            service.getStatusSummary(eventCode)
        ).isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * AC15: should_getStatusHistory_when_validSpeakerIdProvided
     */
    @Test
    @DisplayName("Should get status history when valid speaker ID is provided")
    void should_getStatusHistory_when_validSpeakerIdProvided() {
        // Given
        String eventCode = "BATbern998";
        UUID speakerId = UUID.randomUUID();

        // When/Then: Service method should throw since it's not implemented (RED phase)
        assertThatThrownBy(() ->
            service.getStatusHistory(eventCode, speakerId)
        ).isInstanceOf(UnsupportedOperationException.class);
    }
}
