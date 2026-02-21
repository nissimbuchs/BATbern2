package ch.batbern.events.watch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Listens for WebSocket disconnect events and removes the organizer from all
 * events they were present in.
 * W4.1 Task 10 (AC3): Ensures presence is cleaned up even on abrupt disconnection.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WatchWebSocketDisconnectListener {

    private final WatchPresenceService presenceService;

    /**
     * Called by Spring when any WebSocket session disconnects.
     * If the session had an authenticated principal (set by JwtStompInterceptor),
     * removes that organizer from all events they were joined to.
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        if (event.getUser() == null) {
            return;
        }
        String username = event.getUser().getName();
        log.debug("WebSocket disconnect for user: {} — removing from all events", username);
        presenceService.leaveAllEvents(username);
    }
}
