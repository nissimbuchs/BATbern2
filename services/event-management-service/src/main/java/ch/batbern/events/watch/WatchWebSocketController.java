package ch.batbern.events.watch;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP WebSocket controller for Watch organizer actions.
 * W2.4: Speaker arrival via WebSocket (FR38 — sync to all watches within 3 seconds).
 *
 * Message flow:
 * Watch → /app/watch/events/{eventCode}/speaker-arrived
 * → WatchSpeakerArrivalService.confirmArrival()
 * → broadcast → /topic/events/{eventCode}/arrivals
 */
@Controller
@RequiredArgsConstructor
public class WatchWebSocketController {

    private final WatchSpeakerArrivalService arrivalService;

    /**
     * Handles speaker arrival action sent by organizer Watch.
     * Delegates to WatchSpeakerArrivalService which broadcasts to all subscribers.
     *
     * @param eventCode    event code from destination variable
     * @param action       payload containing speakerUsername
     * @param principal    authenticated organizer (from STOMP CONNECT JWT)
     */
    @MessageMapping("/watch/events/{eventCode}/speaker-arrived")
    public void handleSpeakerArrived(
            @DestinationVariable String eventCode,
            @Payload SpeakerArrivedAction action,
            Principal principal
    ) {
        arrivalService.confirmArrival(
                eventCode,
                action.speakerUsername(),
                principal.getName()
        );
        // arrivalService.confirmArrival broadcasts to /topic/events/{eventCode}/arrivals
    }

    /**
     * STOMP payload for speaker arrival action.
     */
    record SpeakerArrivedAction(String speakerUsername) {}
}
