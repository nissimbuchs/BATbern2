package ch.batbern.companyuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.time.LocalTime;

/**
 * User preferences embeddable object
 * Story 1.14-2, AC: 6 (User Preferences)
 * Embedded in User entity - all columns prefixed with "pref_"
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreferences {

    @Column(name = "pref_theme", length = 10)
    @Builder.Default
    private String theme = "auto";

    @Column(name = "pref_language", length = 2)
    @Builder.Default
    private String language = "de";

    @Column(name = "pref_email_notifications")
    @Builder.Default
    private boolean emailNotifications = true;

    @Column(name = "pref_in_app_notifications")
    @Builder.Default
    private boolean inAppNotifications = true;

    @Column(name = "pref_push_notifications")
    @Builder.Default
    private boolean pushNotifications = false;

    @Column(name = "pref_notification_frequency", length = 20)
    @Builder.Default
    private String notificationFrequency = "immediate";

    @Column(name = "pref_quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "pref_quiet_hours_end")
    private LocalTime quietHoursEnd;
}
