package ch.batbern.events.watch;

import ch.batbern.events.domain.Session;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.watch.dto.WatchStateUpdateMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WatchPresenceService.
 * W4.1 Task 8.6: join/leave updates presence map; broadcast message shape is correct.
 */
@ExtendWith(MockitoExtension.class)
class WatchPresenceServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private WatchPresenceService service;

    @BeforeEach
    void setUp() {
        service = new WatchPresenceService(sessionRepository, messagingTemplate);
    }

    @Test
    @DisplayName("should_addOrganizerToPresenceMap_when_joinEvent")
    void should_addOrganizerToPresenceMap_when_joinEvent() {
        when(sessionRepository.findByEventCode("BATbern56")).thenReturn(List.of());

        service.joinEvent("BATbern56", "marco.organizer", "Marco");

        WatchStateUpdateMessage state = service.buildStateUpdate("BATbern56");
        assertThat(state.connectedOrganizers()).hasSize(1);
        assertThat(state.connectedOrganizers().get(0).username()).isEqualTo("marco.organizer");
        assertThat(state.connectedOrganizers().get(0).firstName()).isEqualTo("Marco");
        assertThat(state.connectedOrganizers().get(0).connected()).isTrue();
    }

    @Test
    @DisplayName("should_removeOrganizerFromPresenceMap_when_leaveEvent")
    void should_removeOrganizerFromPresenceMap_when_leaveEvent() {
        when(sessionRepository.findByEventCode("BATbern56")).thenReturn(List.of());

        service.joinEvent("BATbern56", "marco.organizer", "Marco");
        service.joinEvent("BATbern56", "lisa.organizer", "Lisa");
        service.leaveEvent("BATbern56", "marco.organizer");

        WatchStateUpdateMessage state = service.buildStateUpdate("BATbern56");
        assertThat(state.connectedOrganizers()).hasSize(1);
        assertThat(state.connectedOrganizers().get(0).username()).isEqualTo("lisa.organizer");
    }

    @Test
    @DisplayName("should_broadcastStateUpdate_when_organizerJoins")
    void should_broadcastStateUpdate_when_organizerJoins() {
        when(sessionRepository.findByEventCode("BATbern56")).thenReturn(List.of());

        service.joinEvent("BATbern56", "marco.organizer", "Marco");

        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/events/BATbern56/state"), any(WatchStateUpdateMessage.class));
    }

    @Test
    @DisplayName("should_broadcastStateUpdate_when_organizerLeaves")
    void should_broadcastStateUpdate_when_organizerLeaves() {
        when(sessionRepository.findByEventCode("BATbern56")).thenReturn(List.of());

        service.joinEvent("BATbern56", "marco.organizer", "Marco");
        service.leaveEvent("BATbern56", "marco.organizer");

        verify(messagingTemplate, times(2))
                .convertAndSend(eq("/topic/events/BATbern56/state"), any(WatchStateUpdateMessage.class));
    }

    @Test
    @DisplayName("should_includeSessions_when_buildStateUpdate")
    void should_includeSessions_when_buildStateUpdate() {
        Session session = Session.builder()
                .sessionSlug("cloud-native-pitfalls")
                .title("Cloud-Native Pitfalls")
                .sessionType("presentation")
                .eventCode("BATbern56")
                .build();
        when(sessionRepository.findByEventCode("BATbern56")).thenReturn(List.of(session));

        WatchStateUpdateMessage state = service.buildStateUpdate("BATbern56");

        assertThat(state.sessions()).hasSize(1);
        assertThat(state.sessions().get(0).sessionSlug()).isEqualTo("cloud-native-pitfalls");
        assertThat(state.sessions().get(0).title()).isEqualTo("Cloud-Native Pitfalls");
    }

    @Test
    @DisplayName("should_includeW4Fields_when_buildStateUpdate")
    void should_includeW4Fields_when_buildStateUpdate() {
        Session session = Session.builder()
                .sessionSlug("cloud-native-pitfalls")
                .title("Cloud-Native Pitfalls")
                .sessionType("presentation")
                .eventCode("BATbern56")
                .overrunMinutes(5)
                .completedByUsername("marco.organizer")
                .build();
        when(sessionRepository.findByEventCode("BATbern56")).thenReturn(List.of(session));

        WatchStateUpdateMessage state = service.buildStateUpdate("BATbern56");

        assertThat(state.sessions().get(0).overrunMinutes()).isEqualTo(5);
        assertThat(state.sessions().get(0).completedBy()).isEqualTo("marco.organizer");
    }

    @Test
    @DisplayName("should_haveCorrectMessageType_when_organizerJoins")
    void should_haveCorrectMessageType_when_organizerJoins() {
        when(sessionRepository.findByEventCode("BATbern56")).thenReturn(List.of());

        ArgumentCaptor<WatchStateUpdateMessage> captor = ArgumentCaptor.forClass(WatchStateUpdateMessage.class);
        service.joinEvent("BATbern56", "marco.organizer", "Marco");

        verify(messagingTemplate).convertAndSend(
                eq("/topic/events/BATbern56/state"), captor.capture());
        WatchStateUpdateMessage broadcast = captor.getValue();
        assertThat(broadcast.type()).isEqualTo("STATE_UPDATE");
        assertThat(broadcast.trigger()).isEqualTo("ORGANIZER_JOINED");
        assertThat(broadcast.eventCode()).isEqualTo("BATbern56");
    }
}
