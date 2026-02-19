package ch.batbern.events.watch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WatchWebSocketDisconnectListener.
 * W4.1 Task 10.2: Simulate disconnect → verify presence updated + state broadcast sent.
 */
@ExtendWith(MockitoExtension.class)
class WatchWebSocketDisconnectListenerTest {

    @Mock
    private WatchPresenceService presenceService;

    private WatchWebSocketDisconnectListener listener;

    @BeforeEach
    void setUp() {
        listener = new WatchWebSocketDisconnectListener(presenceService);
    }

    @Test
    @DisplayName("should_removePresenceForAllEvents_when_organizerDisconnects")
    void should_removePresenceForAllEvents_when_organizerDisconnects() {
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn("marco.organizer");

        // Simulate organizer joined two events
        presenceService.joinEvent("BATbern56", "marco.organizer", "Marco");
        presenceService.joinEvent("BATbern57", "marco.organizer", "Marco");

        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getUser()).thenReturn(principal);

        listener.handleDisconnect(event);

        verify(presenceService).leaveAllEvents("marco.organizer");
    }

    @Test
    @DisplayName("should_doNothing_when_noPrincipalOnDisconnect")
    void should_doNothing_when_noPrincipalOnDisconnect() {
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getUser()).thenReturn(null);

        listener.handleDisconnect(event);

        verify(presenceService, never()).leaveAllEvents(any());
    }

    // Helper for "never called with any arg"
    private static String any() {
        return org.mockito.ArgumentMatchers.any();
    }
}
