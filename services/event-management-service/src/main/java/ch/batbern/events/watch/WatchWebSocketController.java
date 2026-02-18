package ch.batbern.events.watch;

import ch.batbern.events.watch.dto.WatchStateUpdateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP WebSocket controller for Watch organizer actions.
 *
 * W2.4: Speaker arrival via WebSocket (FR38 — sync to all watches within 3 seconds).
 * W4.1: Organizer presence join/leave + action stub (AC1, AC2, AC4).
 *
 * Message flows:
 * Watch → /app/watch/events/{eventCode}/speaker-arrived  → arrival broadcast
 * Watch → /app/watch/events/{eventCode}/join             → presence join + state snapshot
 * Watch → /app/watch/events/{eventCode}/leave            → presence leave
 * Watch → /app/watch/events/{eventCode}/action           → stub (W4.2 implements dispatch)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WatchWebSocketController {

    private final WatchSpeakerArrivalService arrivalService;
    private final WatchPresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handles speaker arrival action sent by organizer Watch.
     * Delegates to WatchSpeakerArrivalService which broadcasts to all subscribers.
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
    }

    /**
     * Handles organizer joining an event's real-time session.
     * Registers presence and sends full state snapshot to the joining Watch.
     * W4.1 Task 9.1 (AC1, AC4).
     */
    @MessageMapping("/watch/events/{eventCode}/join")
    public void handleJoin(
            @DestinationVariable String eventCode,
            Principal principal
    ) {
        String username = principal.getName();
        String firstName = extractFirstName(principal, username);
        presenceService.joinEvent(eventCode, username, firstName);
        WatchStateUpdateMessage stateSnapshot = presenceService.buildStateUpdate(eventCode);
        messagingTemplate.convertAndSendToUser(username, "/queue/watch/state", stateSnapshot);
        log.debug("Organizer {} joined event {} — sent state snapshot", username, eventCode);
    }

    /**
     * Handles organizer leaving an event's real-time session.
     * W4.1 Task 9.2.
     */
    @MessageMapping("/watch/events/{eventCode}/leave")
    public void handleLeave(
            @DestinationVariable String eventCode,
            Principal principal
    ) {
        presenceService.leaveEvent(eventCode, principal.getName());
    }

    /**
     * Action stub — logs and echoes ACK.
     * W4.1 Task 9.3: No state mutation in W4.1; W4.2+ implements actual dispatch.
     */
    @MessageMapping("/watch/events/{eventCode}/action")
    public void handleAction(
            @DestinationVariable String eventCode,
            @Payload Object action,
            Principal principal
    ) {
        log.info("Watch action received on event {} from {}: {}", eventCode, principal.getName(), action);
        // W4.2+ will dispatch to session control service
    }

    /**
     * Extracts given_name from the JWT stored in principal details (Cognito ID token).
     * Falls back to Watch JWT firstName claim, then to username.
     */
    private String extractFirstName(Principal principal, String fallback) {
        if (principal instanceof UsernamePasswordAuthenticationToken authToken
                && authToken.getDetails() instanceof Jwt jwt) {
            String givenName = jwt.getClaimAsString("given_name");
            if (givenName != null && !givenName.isBlank()) {
                return givenName;
            }
            String firstName = jwt.getClaimAsString("firstName");
            if (firstName != null && !firstName.isBlank()) {
                return firstName;
            }
        }
        return fallback;
    }

    /**
     * STOMP payload for speaker arrival action (W2.4).
     */
    record SpeakerArrivedAction(String speakerUsername) {}
}
