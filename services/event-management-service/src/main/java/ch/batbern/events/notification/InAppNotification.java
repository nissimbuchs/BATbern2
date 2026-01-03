package ch.batbern.events.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO for in-app notifications (queried dynamically, not stored)
 * Story BAT-7: Notifications API Consolidation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InAppNotification {
    private String type;
    private String title;
    private String eventCode;
    private Instant createdAt;
}
