package ch.batbern.companyuser.service;

import ch.batbern.companyuser.domain.User;
import ch.batbern.companyuser.domain.UserPreferences;
import ch.batbern.companyuser.domain.UserSettings;
import ch.batbern.companyuser.dto.generated.UpdateUserSettingsRequest;
import ch.batbern.companyuser.exception.UserNotFoundException;
import ch.batbern.companyuser.exception.UserValidationException;
import ch.batbern.companyuser.repository.UserRepository;
import ch.batbern.companyuser.security.SecurityContextHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;

/**
 * The caller's settings as one resource, {@code GET} and {@code PATCH /users/me/settings}
 * (Story 4.9, which merged the former {@code /users/me/preferences} into it). The wire object is
 * the generated {@code UserSettings}; the storage stays the two embeddables on user_profiles,
 * {@link UserPreferences} (display and notifications) and {@link UserSettings} (privacy and
 * timezone).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserRepository userRepository;
    private final SecurityContextHelper securityContext;

    /** The caller's settings; stored defaults are created on first read, as before. */
    @Transactional
    public ch.batbern.companyuser.dto.generated.UserSettings getCurrentUserSettings() {
        User user = currentUser();
        boolean initialized = false;
        if (user.getPreferences() == null) {
            user.setPreferences(UserPreferences.builder().build());
            initialized = true;
        }
        if (user.getSettings() == null) {
            user.setSettings(UserSettings.builder().build());
            initialized = true;
        }
        if (initialized) {
            userRepository.save(user);
        }
        return toDto(user);
    }

    /**
     * Applies the fields present in the request; every other setting stays.
     *
     * @throws UserValidationException for a timezone that is not an IANA zone id (400)
     */
    @Transactional
    public ch.batbern.companyuser.dto.generated.UserSettings updateCurrentUserSettings(
            UpdateUserSettingsRequest request) {
        User user = currentUser();
        if (request.getTimezone() != null) {
            requireZoneId(request.getTimezone());
        }
        if (user.getPreferences() == null) {
            user.setPreferences(UserPreferences.builder().build());
        }
        if (user.getSettings() == null) {
            user.setSettings(UserSettings.builder().build());
        }
        UserPreferences prefs = user.getPreferences();
        UserSettings settings = user.getSettings();

        if (request.getTheme() != null) {
            prefs.setTheme(request.getTheme().getValue());
        }
        if (request.getLanguage() != null) {
            prefs.setLanguage(request.getLanguage().getValue());
        }
        if (request.getEmailNotifications() != null) {
            prefs.setEmailNotifications(request.getEmailNotifications());
        }
        if (request.getInAppNotifications() != null) {
            prefs.setInAppNotifications(request.getInAppNotifications());
        }
        if (request.getPushNotifications() != null) {
            prefs.setPushNotifications(request.getPushNotifications());
        }
        if (request.getNotificationFrequency() != null) {
            prefs.setNotificationFrequency(request.getNotificationFrequency().getValue());
        }
        if (request.getQnaNotificationFrequency() != null) {
            prefs.setQnaNotificationFrequency(request.getQnaNotificationFrequency().getValue());
        }
        if (request.getQuietHoursStart() != null) {
            prefs.setQuietHoursStart(quietHour(request.getQuietHoursStart()));
        }
        if (request.getQuietHoursEnd() != null) {
            prefs.setQuietHoursEnd(quietHour(request.getQuietHoursEnd()));
        }
        if (request.getTimezone() != null) {
            settings.setTimezone(request.getTimezone());
        }
        if (request.getProfileVisibility() != null) {
            settings.setProfileVisibility(request.getProfileVisibility().getValue());
        }
        if (request.getShowEmail() != null) {
            settings.setShowEmail(request.getShowEmail());
        }
        if (request.getShowCompany() != null) {
            settings.setShowCompany(request.getShowCompany());
        }
        if (request.getShowActivityHistory() != null) {
            settings.setShowActivityHistory(request.getShowActivityHistory());
        }
        if (request.getAllowMessaging() != null) {
            settings.setAllowMessaging(request.getAllowMessaging());
        }
        if (request.getAllowCalendarSync() != null) {
            settings.setAllowCalendarSync(request.getAllowCalendarSync());
        }
        if (request.getTwoFactorEnabled() != null) {
            settings.setTwoFactorEnabled(request.getTwoFactorEnabled());
        }

        userRepository.save(user);
        log.info("Updated settings for user: {}", user.getUsername());
        return toDto(user);
    }

    /**
     * The merged wire object of a user (also used by {@code ?include=settings}). A group that was
     * never stored answers with its defaults.
     */
    static ch.batbern.companyuser.dto.generated.UserSettings toDto(User user) {
        UserPreferences prefs = user.getPreferences() != null
                ? user.getPreferences() : UserPreferences.builder().build();
        UserSettings settings = user.getSettings() != null
                ? user.getSettings() : UserSettings.builder().build();
        return new ch.batbern.companyuser.dto.generated.UserSettings()
                .theme(ch.batbern.companyuser.dto.generated.UserSettings.ThemeEnum.fromValue(prefs.getTheme()))
                .language(ch.batbern.companyuser.dto.generated.UserSettings.LanguageEnum.fromValue(
                        prefs.getLanguage()))
                .timezone(settings.getTimezone())
                .emailNotifications(prefs.isEmailNotifications())
                .inAppNotifications(prefs.isInAppNotifications())
                .pushNotifications(prefs.isPushNotifications())
                .notificationFrequency(ch.batbern.companyuser.dto.generated.UserSettings
                        .NotificationFrequencyEnum.fromValue(prefs.getNotificationFrequency()))
                .qnaNotificationFrequency(ch.batbern.companyuser.dto.generated.UserSettings
                        .QnaNotificationFrequencyEnum.fromValue(prefs.getQnaNotificationFrequency() != null
                                ? prefs.getQnaNotificationFrequency() : "live"))
                .quietHoursStart(prefs.getQuietHoursStart() != null ? prefs.getQuietHoursStart().toString() : null)
                .quietHoursEnd(prefs.getQuietHoursEnd() != null ? prefs.getQuietHoursEnd().toString() : null)
                .profileVisibility(ch.batbern.companyuser.dto.generated.UserSettings.ProfileVisibilityEnum
                        .fromValue(settings.getProfileVisibility().toLowerCase(Locale.ROOT)))
                .showEmail(settings.isShowEmail())
                .showCompany(settings.isShowCompany())
                .showActivityHistory(settings.isShowActivityHistory())
                .allowMessaging(settings.isAllowMessaging())
                .allowCalendarSync(settings.isAllowCalendarSync())
                .twoFactorEnabled(settings.isTwoFactorEnabled());
    }

    private User currentUser() {
        String username = securityContext.getCurrentUsername();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
    }

    private static void requireZoneId(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new UserValidationException("timezone", "Not an IANA time zone id: " + timezone);
        }
    }

    /**
     * A quiet hour from the request: an empty string clears it (null keeps it, handled by the
     * caller); {@code 7:30} becomes {@code 07:30}, as the spec allows a one-digit hour.
     */
    private static LocalTime quietHour(String time) {
        if (time.isEmpty()) {
            return null;
        }
        return LocalTime.parse(time.length() == 4 ? "0" + time : time);
    }
}
