package ch.batbern.events.watch.dto;

import java.util.List;

/**
 * Full state broadcast message sent to all Watch clients on any state change.
 * W4.1 Task 8 (AC2, AC4): Contains session list + presence count.
 *
 * JSON shape:
 * {
 *   "type": "STATE_UPDATE",
 *   "trigger": "SESSION_ENDED",
 *   "eventCode": "BATbern56",
 *   "sessions": [...],
 *   "connectedOrganizers": [...],
 *   "serverTimestamp": "2026-02-18T18:00:00Z",
 *   "sessionSlug": "cloud-native-pitfalls",
 *   "initiatedBy": "marco.organizer"
 * }
 * W4.2 Task 8.1: sessionSlug and initiatedBy added; null for non-session-control triggers.
 */
public record WatchStateUpdateMessage(
        String type,
        String trigger,
        String eventCode,
        List<SessionStateDto> sessions,
        List<ConnectedOrganizerDto> connectedOrganizers,
        String serverTimestamp,
        String sessionSlug,   // W4.2: slug of session that triggered (null for presence events)
        String initiatedBy    // W4.2: organizer who triggered the action (null for presence events)
) {

    /**
     * Session state entry in the broadcast.
     * Includes W4 session control fields (populated from V56 columns).
     */
    public record SessionStateDto(
            String sessionSlug,
            String title,
            String sessionType,
            String scheduledStartTime,
            String scheduledEndTime,
            String status,
            String actualStartTime,
            String actualEndTime,
            int overrunMinutes,
            String completedBy
    ) {}
}
