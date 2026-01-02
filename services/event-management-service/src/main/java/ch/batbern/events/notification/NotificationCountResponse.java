package ch.batbern.events.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for notification count API
 * Story BAT-7: Notifications API Consolidation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationCountResponse {
    private long count;
}
