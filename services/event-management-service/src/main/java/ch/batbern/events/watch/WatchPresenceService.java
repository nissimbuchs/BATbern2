package ch.batbern.events.watch;

import ch.batbern.events.domain.Session;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.watch.dto.ConnectedOrganizerDto;
import ch.batbern.events.watch.dto.WatchStateUpdateMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages organizer presence for Watch real-time session control.
 * W4.1 Task 8 (AC4, FR20): Tracks connected organizers per event and broadcasts full state.
 *
 * Thread-safety: ConcurrentHashMap with synchronized Set ensures join/leave are race-free.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchPresenceService {

    private final SessionRepository sessionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private record OrganizerPresence(String username, String firstName) {}

    /** In-memory presence map: eventCode → Set of connected organizers. */
    private final ConcurrentHashMap<String, Set<OrganizerPresence>> presenceByEvent =
            new ConcurrentHashMap<>();

    /**
     * Registers an organizer as connected to an event.
     * Broadcasts updated state to all subscribers on the event's state topic.
     *
     * @param eventCode  event identifier
     * @param username   authenticated organizer's username (from JWT sub)
     * @param firstName  organizer's first name (for presence display)
     */
    public void joinEvent(String eventCode, String username, String firstName) {
        Set<OrganizerPresence> presence = presenceByEvent.computeIfAbsent(
                eventCode,
                k -> Collections.newSetFromMap(new ConcurrentHashMap<>())
        );
        presence.removeIf(p -> p.username().equals(username)); // idempotent re-join
        presence.add(new OrganizerPresence(username, firstName));
        log.debug("Organizer {} joined event {} ({} connected)", username, eventCode, presence.size());
        broadcastState(eventCode, "ORGANIZER_JOINED");
    }

    /**
     * Removes an organizer from the event's presence set.
     * Broadcasts updated state to remaining subscribers.
     *
     * @param eventCode  event identifier
     * @param username   departing organizer's username
     */
    public void leaveEvent(String eventCode, String username) {
        Set<OrganizerPresence> presence = presenceByEvent.get(eventCode);
        if (presence != null) {
            boolean removed = presence.removeIf(p -> p.username().equals(username));
            if (removed) {
                log.debug("Organizer {} left event {} ({} remaining)", username, eventCode, presence.size());
                broadcastState(eventCode, "ORGANIZER_LEFT");
            }
        }
    }

    /**
     * Builds the full STATE_UPDATE message for a given event.
     * Includes session list (with W4 fields) and connected organizers.
     */
    public WatchStateUpdateMessage buildStateUpdate(String eventCode) {
        return buildStateUpdate(eventCode, "STATE_UPDATE");
    }

    /**
     * Broadcasts SESSION_ENDED state update to all Watch clients on the event's state topic.
     * W4.2 Task 7.3: Includes sessionSlug and initiatedBy so the Watch can identify
     * which session was completed and who triggered it.
     */
    public void broadcastSessionEnded(String eventCode, String sessionSlug, String completedByUsername) {
        WatchStateUpdateMessage message = buildStateUpdate(
                eventCode, "SESSION_ENDED", sessionSlug, completedByUsername);
        messagingTemplate.convertAndSend("/topic/events/" + eventCode + "/state", message);
        log.debug("Broadcast SESSION_ENDED for session {} by {} on event {}",
                sessionSlug, completedByUsername, eventCode);
    }

    private WatchStateUpdateMessage buildStateUpdate(String eventCode, String trigger) {
        return buildStateUpdate(eventCode, trigger, null, null);
    }

    private WatchStateUpdateMessage buildStateUpdate(
            String eventCode, String trigger, String sessionSlug, String initiatedBy) {
        List<Session> sessions = sessionRepository.findByEventCode(eventCode);
        List<WatchStateUpdateMessage.SessionStateDto> sessionDtos = sessions.stream()
                .map(this::toSessionStateDto)
                .toList();

        Set<OrganizerPresence> presence = presenceByEvent.getOrDefault(eventCode, Set.of());
        List<ConnectedOrganizerDto> organizers = presence.stream()
                .map(p -> new ConnectedOrganizerDto(p.username(), p.firstName(), true))
                .toList();

        return new WatchStateUpdateMessage(
                "STATE_UPDATE",
                trigger,
                eventCode,
                sessionDtos,
                new ArrayList<>(organizers),
                Instant.now().toString(),
                sessionSlug,
                initiatedBy
        );
    }

    /**
     * Returns true if the organizer is currently present (joined) in the event.
     * Used by WatchWebSocketController to gate session-control actions to joined organizers only.
     * H1 fix (W4.2 code review): prevents cross-event action injection by ORGANIZER-role tokens.
     *
     * @param eventCode event identifier
     * @param username  organizer's username
     */
    public boolean isOrganizerPresent(String eventCode, String username) {
        Set<OrganizerPresence> presence = presenceByEvent.get(eventCode);
        return presence != null && presence.stream().anyMatch(p -> p.username().equals(username));
    }

    /**
     * Removes an organizer from ALL events they were present in.
     * Called on WebSocket disconnect to clean up presence across all active events.
     *
     * @param username departing organizer's username
     */
    public void leaveAllEvents(String username) {
        presenceByEvent.forEach((eventCode, presence) -> {
            boolean removed = presence.removeIf(p -> p.username().equals(username));
            if (removed) {
                log.debug("Organizer {} removed from event {} on disconnect", username, eventCode);
                broadcastState(eventCode, "ORGANIZER_LEFT");
            }
        });
    }

    private void broadcastState(String eventCode, String trigger) {
        WatchStateUpdateMessage message = buildStateUpdate(eventCode, trigger);
        messagingTemplate.convertAndSend("/topic/events/" + eventCode + "/state", message);
    }

    private WatchStateUpdateMessage.SessionStateDto toSessionStateDto(Session session) {
        return new WatchStateUpdateMessage.SessionStateDto(
                session.getSessionSlug(),
                session.getTitle(),
                session.getSessionType(),
                session.getStartTime() != null ? session.getStartTime().toString() : null,
                session.getEndTime() != null ? session.getEndTime().toString() : null,
                deriveStatus(session),
                session.getActualStartTime() != null ? session.getActualStartTime().toString() : null,
                session.getActualEndTime() != null ? session.getActualEndTime().toString() : null,
                session.getOverrunMinutes() != null ? session.getOverrunMinutes() : 0,
                session.getCompletedByUsername()
        );
    }

    private String deriveStatus(Session session) {
        if (session.getActualEndTime() != null) {
            return "COMPLETED";
        }
        if (session.getActualStartTime() != null) {
            return "ACTIVE";
        }
        return "SCHEDULED";
    }
}
