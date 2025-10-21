package ch.batbern.companyuser.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for UserPreferences embeddable
 * Tests cover preference values and defaults
 * Story 1.14-2, AC: 6 (User Preferences)
 */
@DisplayName("UserPreferences Tests")
class UserPreferencesTest {

    @Test
    @DisplayName("should_createWithDefaults_when_builderUsed")
    void should_createWithDefaults_when_builderUsed() {
        // When
        UserPreferences prefs = UserPreferences.builder().build();

        // Then
        assertThat(prefs.getTheme()).isEqualTo("auto");
        assertThat(prefs.getLanguage()).isEqualTo("de");
        assertThat(prefs.isEmailNotifications()).isTrue();
        assertThat(prefs.isInAppNotifications()).isTrue();
        assertThat(prefs.isPushNotifications()).isFalse();
        assertThat(prefs.getNotificationFrequency()).isEqualTo("immediate");
    }

    @Test
    @DisplayName("should_setTheme_when_validThemeProvided")
    void should_setTheme_when_validThemeProvided() {
        // Given
        UserPreferences prefs = UserPreferences.builder().build();

        // When
        prefs.setTheme("dark");

        // Then
        assertThat(prefs.getTheme()).isEqualTo("dark");
    }

    @Test
    @DisplayName("should_setLanguage_when_validLanguageProvided")
    void should_setLanguage_when_validLanguageProvided() {
        // Given
        UserPreferences prefs = UserPreferences.builder().build();

        // When
        prefs.setLanguage("en");

        // Then
        assertThat(prefs.getLanguage()).isEqualTo("en");
    }

    @Test
    @DisplayName("should_setNotificationPreferences_when_updated")
    void should_setNotificationPreferences_when_updated() {
        // Given
        UserPreferences prefs = UserPreferences.builder().build();

        // When
        prefs.setEmailNotifications(false);
        prefs.setInAppNotifications(false);
        prefs.setPushNotifications(true);

        // Then
        assertThat(prefs.isEmailNotifications()).isFalse();
        assertThat(prefs.isInAppNotifications()).isFalse();
        assertThat(prefs.isPushNotifications()).isTrue();
    }

    @Test
    @DisplayName("should_setQuietHours_when_timeRangeProvided")
    void should_setQuietHours_when_timeRangeProvided() {
        // Given
        UserPreferences prefs = UserPreferences.builder().build();
        LocalTime start = LocalTime.of(22, 0);
        LocalTime end = LocalTime.of(8, 0);

        // When
        prefs.setQuietHoursStart(start);
        prefs.setQuietHoursEnd(end);

        // Then
        assertThat(prefs.getQuietHoursStart()).isEqualTo(start);
        assertThat(prefs.getQuietHoursEnd()).isEqualTo(end);
    }

    @Test
    @DisplayName("should_createWithAllFields_when_builderUsedWithValues")
    void should_createWithAllFields_when_builderUsedWithValues() {
        // When
        UserPreferences prefs = UserPreferences.builder()
                .theme("light")
                .language("fr")
                .emailNotifications(false)
                .inAppNotifications(true)
                .pushNotifications(true)
                .notificationFrequency("daily_digest")
                .quietHoursStart(LocalTime.of(23, 0))
                .quietHoursEnd(LocalTime.of(7, 0))
                .build();

        // Then
        assertThat(prefs.getTheme()).isEqualTo("light");
        assertThat(prefs.getLanguage()).isEqualTo("fr");
        assertThat(prefs.isEmailNotifications()).isFalse();
        assertThat(prefs.isInAppNotifications()).isTrue();
        assertThat(prefs.isPushNotifications()).isTrue();
        assertThat(prefs.getNotificationFrequency()).isEqualTo("daily_digest");
        assertThat(prefs.getQuietHoursStart()).isEqualTo(LocalTime.of(23, 0));
        assertThat(prefs.getQuietHoursEnd()).isEqualTo(LocalTime.of(7, 0));
    }
}
