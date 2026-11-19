package ch.batbern.events.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quiet hours of {@link NotificationService} (#1222): an overnight window suppresses at night, a
 * same-day window only during the day, both read on the recipient's wall clock (the server runs
 * on UTC). The start belongs to the window, the end does not.
 */
@DisplayName("Notification quiet hours")
class NotificationQuietHoursTest {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    private static UserPreferences window(String start, String end, String timezone) {
        return UserPreferences.builder()
                .emailNotificationsEnabled(true)
                .quietHoursStart(start)
                .quietHoursEnd(end)
                .timezone(timezone)
                .build();
    }

    /** A winter day, Zurich wall clock. */
    private static Instant zurich(String time) {
        return LocalDateTime.parse("2026-01-15T" + time).atZone(ZURICH).toInstant();
    }

    @ParameterizedTest(name = "{0}-{1} at {2} Zurich: quiet={3}")
    @CsvSource({
        "22:00, 07:00, 23:30, true",
        "22:00, 07:00, 03:00, true",
        "22:00, 07:00, 12:00, false",
        "09:00, 17:00, 10:00, true",
        "09:00, 17:00, 18:00, false",
        "09:00, 17:00, 08:00, false",
        "22:00, 07:00, 22:00, true",
        "22:00, 07:00, 07:00, false",
        "09:00, 17:00, 09:00, true",
        "09:00, 17:00, 17:00, false"
    })
    void should_suppressOnlyInsideWindow(String start, String end, String now, boolean quiet) {
        assertThat(NotificationService.isInQuietHours(window(start, end, "Europe/Zurich"), zurich(now)))
                .isEqualTo(quiet);
    }

    @Test
    @DisplayName("reads the window in Zurich when the clock says 21:30 UTC in winter (22:30 Zurich)")
    void should_readWindowInZurich_when_serverClockIsUtc() {
        Instant utc2130 = Instant.parse("2026-01-15T21:30:00Z");

        assertThat(NotificationService.isInQuietHours(window("22:00", "07:00", "Europe/Zurich"), utc2130)).isTrue();
        assertThat(NotificationService.isInQuietHours(window("22:00", "07:00", null), utc2130))
                .as("no timezone in the settings means Zurich")
                .isTrue();
    }

    @Test
    @DisplayName("reads the window in the recipient's own time zone")
    void should_readWindowInRecipientZone_when_timezoneSet() {
        Instant utc2130 = Instant.parse("2026-01-15T21:30:00Z");

        // 21:30 UTC is 16:30 in New York: outside 22:00-07:00 there
        assertThat(NotificationService.isInQuietHours(window("22:00", "07:00", "America/New_York"), utc2130))
                .isFalse();
        // an unknown zone falls back to Zurich (22:30)
        assertThat(NotificationService.isInQuietHours(window("22:00", "07:00", "Mars/Olympus"), utc2130))
                .isTrue();
    }

    @ParameterizedTest(name = "start={0} end={1}: never quiet")
    @CsvSource(value = {"NULL, 07:00", "22:00, NULL", "NULL, NULL", "'', ''", "08:00, 08:00", "late, 07:00"},
            nullValues = "NULL")
    void should_send_when_noUsableWindow(String start, String end) {
        assertThat(NotificationService.isInQuietHours(window(start, end, "Europe/Zurich"), zurich("08:00")))
                .isFalse();
    }
}
