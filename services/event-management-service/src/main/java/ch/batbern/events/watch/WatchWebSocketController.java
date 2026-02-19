package ch.batbern.events.watch;

import ch.batbern.events.watch.dto.WatchActionMessage;
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

import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * STOMP WebSocket controller for Watch organizer actions.
 *
 * W2.4: Speaker arrival via WebSocket (FR38 — sync to all watches within 3 seconds).
 * W4.1: Organizer presence join/leave (AC1, AC2, AC4).
 * W4.2: Session control dispatch — endSession action (AC1, AC2, AC4).
 *
 * Message flows:
 * Watch → /app/watch/events/{eventCode}/speaker-arrived  → arrival broadcast
 * Watch → /app/watch/events/{eventCode}/join             → presence join + state snapshot
 * Watch → /app/watch/events/{eventCode}/leave            → presence leave
 * Watch → /app/watch/events/{eventCode}/action           → session control dispatch
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WatchWebSocketController {

    private final WatchSpeakerArrivalService arrivalService;
    private final WatchPresenceService presenceService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WatchSessionService watchSessionService;

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
     * Dispatches session control actions from Watch organizers.
     * W4.2 Task 6.1 (AC1, AC2, AC4): Routes END_SESSION to WatchSessionService.
     * W4.3 Task 7.1 (AC2, AC4): Routes EXTEND_SESSION and DELAY_TO_PREVIOUS.
     *
     * Security (H1 fix, W4.2 code review): only organizers present in the event (i.e., who
     * already sent a JOIN) may execute session-control actions. This prevents a valid
     * ORGANIZER-role JWT from ending sessions in arbitrary events they haven't joined.
     *
     * M5 fix: sessionSlug is validated non-null/non-blank before dispatch to avoid
     * misleading SessionNotFoundException("null") for malformed STOMP frames.
     * W4.3: minutes is validated non-null and > 0 for EXTEND_SESSION and DELAY_TO_PREVIOUS.
     */
    @MessageMapping("/watch/events/{eventCode}/action")
    public void handleAction(
            @DestinationVariable String eventCode,
            @Payload WatchActionMessage action,
            Principal principal
    ) {
        String username = principal.getName();
        if (!presenceService.isOrganizerPresent(eventCode, username)) {
            log.warn("Action {} rejected: organizer {} is not present in event {}",
                    action.type(), username, eventCode);
            return;
        }
        switch (action.type()) {
            case "END_SESSION" -> {
                if (action.sessionSlug() == null || action.sessionSlug().isBlank()) {
                    log.warn("END_SESSION rejected: missing sessionSlug from organizer {} in event {}",
                            username, eventCode);
                    return;
                }
                watchSessionService.endSession(eventCode, action.sessionSlug(), username);
            }
            case "EXTEND_SESSION" -> {
                if (!isValidSessionAction(action, "EXTEND_SESSION", username, eventCode)) {
                    return;
                }
                watchSessionService.extendSession(
                        eventCode, action.sessionSlug(), action.minutes(), username);
            }
            case "DELAY_TO_PREVIOUS" -> {
                if (!isValidSessionAction(action, "DELAY_TO_PREVIOUS", username, eventCode)) {
                    return;
                }
                watchSessionService.delayToPreviousSession(
                        eventCode, action.sessionSlug(), action.minutes(), username);
            }
            default -> log.warn("Unknown action type: {}", action.type());
        }
    }

    /**
     * Validates sessionSlug and minutes for session control actions (EXTEND_SESSION, DELAY_TO_PREVIOUS).
     * W4.3 Task 7.3: Null guard to prevent misleading exceptions for malformed STOMP frames.
     */
    private boolean isValidSessionAction(WatchActionMessage action, String actionType,
            String username, String eventCode) {
        if (action.sessionSlug() == null || action.sessionSlug().isBlank()) {
            log.warn("{} rejected: missing sessionSlug from organizer {} in event {}",
                    actionType, username, eventCode);
            return false;
        }
        if (action.minutes() == null || action.minutes() <= 0) {
            log.warn("{} rejected: invalid minutes ({}) from organizer {} in event {}",
                    actionType, action.minutes(), username, eventCode);
            return false;
        }
        return true;
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
            // Cognito tokens without given_name: derive display name from email prefix
            String email = jwt.getClaimAsString("email");
            if (email != null && email.contains("@")) {
                return email.substring(0, email.indexOf('@'));
            }
        }
        return fallback;
    }

    /**
     * Cleans up presence when a Watch WebSocket session disconnects unexpectedly.
     * H3 review fix (W4.2): leaveAllEvents() was defined but had no caller, leaving
     * organizers in the presence set indefinitely after unexpected disconnect.
     *
     * @param event Spring SessionDisconnectEvent carrying the disconnected principal
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        if (event.getUser() != null && event.getUser().getName() != null) {
            String username = event.getUser().getName();
            presenceService.leaveAllEvents(username);
            log.debug("Cleaned up presence for disconnected organizer {}", username);
        }
    }

    /**
     * STOMP payload for speaker arrival action (W2.4).
     */
    record SpeakerArrivedAction(String speakerUsername) {}
}
