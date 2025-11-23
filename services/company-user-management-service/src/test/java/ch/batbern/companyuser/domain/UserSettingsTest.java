package ch.batbern.companyuser.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for UserSettings embeddable
 * Tests cover settings values and defaults
 * Story 1.14-2, AC: 7 (User Settings)
 */
@DisplayName("UserSettings Tests")
class UserSettingsTest {

    @Test
    @DisplayName("should_createWithDefaults_when_builderUsed")
    void should_createWithDefaults_when_builderUsed() {
        // When
        UserSettings settings = UserSettings.builder().build();

        // Then
        assertThat(settings.getProfileVisibility()).isEqualTo("public");
        assertThat(settings.isShowEmail()).isFalse();
        assertThat(settings.isShowCompany()).isTrue();
        assertThat(settings.isShowActivityHistory()).isTrue();
        assertThat(settings.isAllowMessaging()).isTrue();
        assertThat(settings.isAllowCalendarSync()).isFalse();
        assertThat(settings.getTimezone()).isEqualTo("Europe/Zurich");
        assertThat(settings.isTwoFactorEnabled()).isFalse();
    }

    @Test
    @DisplayName("should_setProfileVisibility_when_validValueProvided")
    void should_setProfileVisibility_when_validValueProvided() {
        // Given
        UserSettings settings = UserSettings.builder().build();

        // When
        settings.setProfileVisibility("private");

        // Then
        assertThat(settings.getProfileVisibility()).isEqualTo("private");
    }

    @Test
    @DisplayName("should_setPrivacySettings_when_updated")
    void should_setPrivacySettings_when_updated() {
        // Given
        UserSettings settings = UserSettings.builder().build();

        // When
        settings.setShowEmail(true);
        settings.setShowCompany(false);
        settings.setShowActivityHistory(false);

        // Then
        assertThat(settings.isShowEmail()).isTrue();
        assertThat(settings.isShowCompany()).isFalse();
        assertThat(settings.isShowActivityHistory()).isFalse();
    }

    @Test
    @DisplayName("should_setFeatureToggles_when_updated")
    void should_setFeatureToggles_when_updated() {
        // Given
        UserSettings settings = UserSettings.builder().build();

        // When
        settings.setAllowMessaging(false);
        settings.setAllowCalendarSync(true);
        settings.setTwoFactorEnabled(true);

        // Then
        assertThat(settings.isAllowMessaging()).isFalse();
        assertThat(settings.isAllowCalendarSync()).isTrue();
        assertThat(settings.isTwoFactorEnabled()).isTrue();
    }

    @Test
    @DisplayName("should_setTimezone_when_validTimezoneProvided")
    void should_setTimezone_when_validTimezoneProvided() {
        // Given
        UserSettings settings = UserSettings.builder().build();

        // When
        settings.setTimezone("America/New_York");

        // Then
        assertThat(settings.getTimezone()).isEqualTo("America/New_York");
    }

    @Test
    @DisplayName("should_createWithAllFields_when_builderUsedWithValues")
    void should_createWithAllFields_when_builderUsedWithValues() {
        // When
        UserSettings settings = UserSettings.builder()
                .profileVisibility("members_only")
                .showEmail(true)
                .showCompany(false)
                .showActivityHistory(false)
                .allowMessaging(false)
                .allowCalendarSync(true)
                .timezone("UTC")
                .twoFactorEnabled(true)
                .build();

        // Then
        assertThat(settings.getProfileVisibility()).isEqualTo("members_only");
        assertThat(settings.isShowEmail()).isTrue();
        assertThat(settings.isShowCompany()).isFalse();
        assertThat(settings.isShowActivityHistory()).isFalse();
        assertThat(settings.isAllowMessaging()).isFalse();
        assertThat(settings.isAllowCalendarSync()).isTrue();
        assertThat(settings.getTimezone()).isEqualTo("UTC");
        assertThat(settings.isTwoFactorEnabled()).isTrue();
    }
}
