package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.speakers.dto.generated.StatusSummaryResponse;
import ch.batbern.events.entity.EventAgendaConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionContentHistoryRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit Tests for SpeakerStatusService (Story 11.B.2 — delegates to SpeakerWorkflowService.transition).
 *
 * <p>The status summary. The status update and the history moved to the production setup in
 * Story 4.10 (SpeakerStatusControllerIntegrationTest, SpeakerCrossEventScopingIntegrationTest).
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
    private AgendaConfigResolver agendaConfigResolver;

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
                agendaConfigResolver,
                speakerWorkflowService,
                sessionContentHistoryRepository,
                new SpeakerPoolLookup(eventRepository, speakerPoolRepository)
        );
    }

    @Test
    @DisplayName("Should calculate acceptance rate when getting status summary")
    void should_calculateAcceptanceRate_when_gettingStatusSummary() {
        String eventCode = "BATbern998";
        Event event = new Event();
        event.setEventCode(eventCode);
        event.setEventType(EventType.FULL_DAY);

        EventAgendaConfig slotConfig = EventAgendaConfig.builder().minSlots(6).maxSlots(8).build();

        when(eventRepository.findByEventCode(eventCode)).thenReturn(Optional.of(event));
        when(agendaConfigResolver.resolve(event)).thenReturn(slotConfig);
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
}
