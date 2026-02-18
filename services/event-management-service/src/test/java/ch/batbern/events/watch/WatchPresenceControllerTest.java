package ch.batbern.events.watch;

import ch.batbern.events.watch.dto.WatchStateUpdateMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.security.Principal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WatchWebSocketController join/leave/action handlers.
 * W4.1 Task 9 (AC1, AC2, AC4): Presence join/leave and action stub.
 */
@ExtendWith(MockitoExtension.class)
class WatchPresenceControllerTest {

    @Mock
    private WatchSpeakerArrivalService arrivalService;

    @Mock
    private WatchPresenceService presenceService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private Principal principal;

    private WatchWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new WatchWebSocketController(arrivalService, presenceService, messagingTemplate);
        when(principal.getName()).thenReturn("marco.organizer");
    }

    @Test
    @DisplayName("should_joinPresence_when_handleJoin")
    void should_joinPresence_when_handleJoin() {
        WatchStateUpdateMessage snapshot = new WatchStateUpdateMessage(
                "STATE_UPDATE", "ORGANIZER_JOINED", "BATbern56", List.of(), List.of(), "now");
        when(presenceService.buildStateUpdate("BATbern56")).thenReturn(snapshot);

        controller.handleJoin("BATbern56", principal);

        verify(presenceService).joinEvent(eq("BATbern56"), eq("marco.organizer"), any());
    }

    @Test
    @DisplayName("should_sendStateSnapshotToJoiningOrganizer_when_handleJoin")
    void should_sendStateSnapshotToJoiningOrganizer_when_handleJoin() {
        WatchStateUpdateMessage snapshot = new WatchStateUpdateMessage(
                "STATE_UPDATE", "ORGANIZER_JOINED", "BATbern56", List.of(), List.of(), "now");
        when(presenceService.buildStateUpdate("BATbern56")).thenReturn(snapshot);

        controller.handleJoin("BATbern56", principal);

        verify(messagingTemplate).convertAndSendToUser(
                eq("marco.organizer"),
                contains("/queue/watch/state"),
                eq(snapshot)
        );
    }

    @Test
    @DisplayName("should_leavePresence_when_handleLeave")
    void should_leavePresence_when_handleLeave() {
        controller.handleLeave("BATbern56", principal);

        verify(presenceService).leaveEvent("BATbern56", "marco.organizer");
    }

    @Test
    @DisplayName("should_notThrow_when_handleAction")
    void should_notThrow_when_handleAction() {
        WatchActionStub action = new WatchActionStub("ADVANCE_SESSION", "cloud-native-pitfalls", null);

        controller.handleAction("BATbern56", action, principal);
        // W4.1: stub — no assertion beyond no exception
    }

    /** Minimal stand-in for WatchActionMessage to test the handler stub. */
    record WatchActionStub(String type, String sessionSlug, Integer minutes) {}
}
