package ch.batbern.events.watch.dto;

import java.util.List;

/**
 * Full state broadcast message sent to all Watch clients on any state change.
 * W4.1 Task 8 (AC2, AC4): Contains session list + presence count.
 *
 * JSON shape:
 * {
 *   "type": "STATE_UPDATE",
 *   "trigger": "ORGANIZER_JOINED",
 *   "eventCode": "BATbern56",
 *   "sessions": [...],
 *   "connectedOrganizers": [...],
 *   "serverTimestamp": "2026-02-18T18:00:00Z"
 * }
 */
public record WatchStateUpdateMessage(
        String type,
        String trigger,
        String eventCode,
        List<SessionStateDto> sessions,
        List<ConnectedOrganizerDto> connectedOrganizers,
        String serverTimestamp
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
