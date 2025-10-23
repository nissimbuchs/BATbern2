package ch.batbern.companyuser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

/**
 * User settings embeddable object
 * Story 1.14-2, AC: 7 (User Settings)
 * Embedded in User entity - all columns prefixed with "settings_"
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSettings {

    @Column(name = "settings_profile_visibility", length = 20)
    @Builder.Default
    private String profileVisibility = "public";

    @Column(name = "settings_show_email")
    @Builder.Default
    private boolean showEmail = false;

    @Column(name = "settings_show_company")
    @Builder.Default
    private boolean showCompany = true;

    @Column(name = "settings_show_activity_history")
    @Builder.Default
    private boolean showActivityHistory = true;

    @Column(name = "settings_allow_messaging")
    @Builder.Default
    private boolean allowMessaging = true;

    @Column(name = "settings_allow_calendar_sync")
    @Builder.Default
    private boolean allowCalendarSync = false;

    @Column(name = "settings_timezone", length = 50)
    @Builder.Default
    private String timezone = "Europe/Zurich";

    @Column(name = "settings_two_factor_enabled")
    @Builder.Default
    private boolean twoFactorEnabled = false;
}
