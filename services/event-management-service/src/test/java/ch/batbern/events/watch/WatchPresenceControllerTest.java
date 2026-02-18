package ch.batbern.events.watch;

import ch.batbern.events.watch.dto.WatchActionMessage;
import ch.batbern.events.watch.dto.WatchStateUpdateMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
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
 * W4.1 Task 9 (AC1, AC2, AC4): Presence join/leave.
 * W4.2 Task 6 (AC1, AC2, AC4): Action dispatch to WatchSessionService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WatchPresenceControllerTest {

    @Mock
    private WatchSpeakerArrivalService arrivalService;

    @Mock
    private WatchPresenceService presenceService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private WatchSessionService watchSessionService;

    @Mock
    private Principal principal;

    private WatchWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new WatchWebSocketController(
                arrivalService, presenceService, messagingTemplate, watchSessionService);
        when(principal.getName()).thenReturn("marco.organizer");
    }

    @Test
    @DisplayName("should_joinPresence_when_handleJoin")
    void should_joinPresence_when_handleJoin() {
        WatchStateUpdateMessage snapshot = new WatchStateUpdateMessage(
                "STATE_UPDATE", "ORGANIZER_JOINED", "BATbern56", List.of(), List.of(), "now", null, null);
        when(presenceService.buildStateUpdate("BATbern56")).thenReturn(snapshot);

        controller.handleJoin("BATbern56", principal);

        verify(presenceService).joinEvent(eq("BATbern56"), eq("marco.organizer"), any());
    }

    @Test
    @DisplayName("should_sendStateSnapshotToJoiningOrganizer_when_handleJoin")
    void should_sendStateSnapshotToJoiningOrganizer_when_handleJoin() {
        WatchStateUpdateMessage snapshot = new WatchStateUpdateMessage(
                "STATE_UPDATE", "ORGANIZER_JOINED", "BATbern56", List.of(), List.of(), "now", null, null);
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
    @DisplayName("should_delegateToWatchSessionService_when_endSessionAction")
    void should_delegateToWatchSessionService_when_endSessionAction() {
        when(presenceService.isOrganizerPresent("BATbern56", "marco.organizer")).thenReturn(true);
        WatchActionMessage action = new WatchActionMessage("END_SESSION", "cloud-native-pitfalls", null);

        controller.handleAction("BATbern56", action, principal);

        verify(watchSessionService).endSession("BATbern56", "cloud-native-pitfalls", "marco.organizer");
    }

    @Test
    @DisplayName("should_notCallSessionService_when_unknownActionType")
    void should_notCallSessionService_when_unknownActionType() {
        when(presenceService.isOrganizerPresent("BATbern56", "marco.organizer")).thenReturn(true);
        WatchActionMessage action = new WatchActionMessage("UNKNOWN_TYPE", "cloud-native-pitfalls", null);

        controller.handleAction("BATbern56", action, principal);

        verify(watchSessionService, org.mockito.Mockito.never()).endSession(any(), any(), any());
    }

    @Test
    @DisplayName("should_rejectAction_when_organizerNotPresentInEvent")
    void should_rejectAction_when_organizerNotPresentInEvent() {
        // H1 fix: organizer not in event's presence set → action rejected silently
        when(presenceService.isOrganizerPresent("BATbern99", "marco.organizer")).thenReturn(false);
        WatchActionMessage action = new WatchActionMessage("END_SESSION", "cloud-native-pitfalls", null);

        controller.handleAction("BATbern99", action, principal);

        verify(watchSessionService, org.mockito.Mockito.never()).endSession(any(), any(), any());
    }

    @Test
    @DisplayName("should_rejectEndSession_when_sessionSlugIsNull")
    void should_rejectEndSession_when_sessionSlugIsNull() {
        // M5 fix: null sessionSlug → rejected before reaching WatchSessionService
        when(presenceService.isOrganizerPresent("BATbern56", "marco.organizer")).thenReturn(true);
        WatchActionMessage action = new WatchActionMessage("END_SESSION", null, null);

        controller.handleAction("BATbern56", action, principal);

        verify(watchSessionService, org.mockito.Mockito.never()).endSession(any(), any(), any());
    }
}
