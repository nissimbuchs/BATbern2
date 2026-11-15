package ch.batbern.events.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User notification preferences DTO
 * Story BAT-7: Notifications API Consolidation
 *
 * Fetched from Company-User Management Service via HTTP
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {
    private boolean emailNotificationsEnabled;
    private String quietHoursStart;  // "22:00"
    private String quietHoursEnd;    // "07:00"
}
