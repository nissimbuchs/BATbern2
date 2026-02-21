package ch.batbern.events.watch.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * Full event detail for Watch active events response.
 * W2.3: Event Join & Schedule Sync
 */
@Getter
@AllArgsConstructor
public class ActiveEventDetail {
    private final String eventCode;
    private final String title;
    private final String eventDate;          // yyyy-MM-dd
    private final String venueName;
    private final String typicalStartTime;   // HH:mm (derived from first session) or null
    private final String typicalEndTime;     // HH:mm (derived from last session) or null
    private final String themeImageUrl;
    private final String currentPublishedPhase;
    private final String eventStatus;        // SCHEDULED, LIVE, COMPLETED
    private final List<SessionDetail> sessions;
}
